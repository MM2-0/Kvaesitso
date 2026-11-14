package de.mm20.launcher2.ktx

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified, thread-safe privileged shell execution engine shared across Smart Freeze Manager,
 * Telos ART Optimizer, Telos Store, and Telos System utilities.
 *
 * Uses a dedicated IO [CoroutineScope] gated by a concurrency [Semaphore] of 2 permits to prevent
 * CPU spikes, pipe deadlocks, thread starvation, and thermal throttling during heavy batch work.
 */
object TelosShellEngine {

    private const val MAX_COMMAND_LENGTH = 65_536
    private const val DEFAULT_MAX_OUTPUT_BYTES = 512 * 1024

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val concurrencyGate = Semaphore(permits = 2)

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false,
    ) {
        val isSuccess: Boolean
            get() = exitCode == 0 && !timedOut
    }

    fun init(context: Context) {
        ShizukuProcesses.init(context.applicationContext)
    }

    fun isShizukuAvailable(context: Context? = null): Boolean {
        if (context != null) {
            ShizukuProcesses.init(context.applicationContext)
        }
        return ShizukuProcesses.available()
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        cachedRootAvailable?.let { if (it) return@withContext true }
        val granted = concurrencyGate.withPermit {
            runCatching {
                val proc = ProcessBuilder("su", "-c", "id").start()
                val res = executeAndCollect(proc, timeoutMs = 5_000L, maxOutputBytes = 4_096)
                res.isSuccess && res.stdout.contains("uid=0")
            }.getOrDefault(false)
        }
        if (granted) {
            cachedRootAvailable = true
        }
        granted
    }

    suspend fun isPrivilegedAvailable(context: Context? = null): Boolean {
        return isShizukuAvailable(context) || isRootAvailable()
    }

    /**
     * Executes a shell [command] via Shizuku (preferred) or Root fallback, bounded by [timeoutMs]
     * and gated by the shared 2-permit concurrency semaphore.
     */
    suspend fun execResult(
        command: String,
        timeoutMs: Long = 30_000L,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): ShellResult = withContext(Dispatchers.IO) {
        if (command.isBlank() || command.length > MAX_COMMAND_LENGTH) {
            return@withContext ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Invalid or oversized command",
            )
        }

        concurrencyGate.withPermit {
            if (ShizukuProcesses.available()) {
                val shizukuResult = runCatching {
                    val proc = ShizukuProcesses.start(arrayOf("sh", "-c", command))
                    executeAndCollect(proc, timeoutMs = timeoutMs, maxOutputBytes = maxOutputBytes)
                }.getOrNull()
                if (shizukuResult != null && shizukuResult.isSuccess) {
                    return@withPermit shizukuResult
                }
                val rootFallback = runCatching {
                    val proc = ProcessBuilder("su", "-c", command).start()
                    executeAndCollect(proc, timeoutMs = timeoutMs, maxOutputBytes = maxOutputBytes)
                }.getOrNull()
                return@withPermit rootFallback?.takeIf { it.isSuccess }
                    ?: shizukuResult
                    ?: rootFallback
                    ?: ShellResult(-1, "", "Privileged execution unavailable")
            }

            runCatching {
                val proc = ProcessBuilder("su", "-c", command).start()
                executeAndCollect(proc, timeoutMs = timeoutMs, maxOutputBytes = maxOutputBytes)
            }.getOrElse { err ->
                ShellResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = err.message ?: "Privileged shell execution failed",
                )
            }
        }
    }

    /**
     * Executes a direct argument vector via Shizuku (or Root shell fallback with strict single-quoting)
     * without shell interpolation risks. Set [retryAfterFailure] to false for writes that must
     * not be replayed after a failed or missing Shizuku response.
     */
    suspend fun execArgsResult(
        args: Array<String>,
        timeoutMs: Long = 30_000L,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
        retryAfterFailure: Boolean = true,
        allowRoot: Boolean = true,
    ): ShellResult = withContext(Dispatchers.IO) {
        if (args.isEmpty()) {
            return@withContext ShellResult(-1, "", "Empty command arguments")
        }

        concurrencyGate.withPermit {
            if (ShizukuProcesses.available()) {
                val shizukuRes = runCatching {
                    val proc = ShizukuProcesses.start(args, retryOnSpawnFailure = retryAfterFailure)
                    executeAndCollect(proc, timeoutMs = timeoutMs, maxOutputBytes = maxOutputBytes)
                }.getOrNull()
                if (!retryAfterFailure || shizukuRes?.isSuccess == true) {
                    return@withPermit shizukuRes
                        ?: ShellResult(-1, "", "Shizuku command failed; command was not retried")
                }
            }

            if (!allowRoot) return@withPermit ShellResult(-1, "", "Authorized Shizuku execution unavailable")
            val escapedCmd = args.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            runCatching {
                val proc = ProcessBuilder("su", "-c", escapedCmd).start()
                executeAndCollect(proc, timeoutMs = timeoutMs, maxOutputBytes = maxOutputBytes)
            }.getOrElse { err ->
                ShellResult(-1, "", err.message ?: "Execution failed")
            }
        }
    }

    /**
     * Streams stdout lines in real time for terminal views, dumpsys parsers, and progress bars.
     * Guaranteed to release all streams, readers, and native/remote processes on completion or cancellation.
     */
    fun execStream(
        command: String,
        timeoutMs: Long = 180_000L,
    ): Flow<String> = channelFlow {
        require(command.isNotBlank() && command.length <= MAX_COMMAND_LENGTH) {
            "Invalid shell command length"
        }

        concurrencyGate.withPermit {
            val process = startRawPrivilegedProcess(command)
                ?: return@withPermit

            val timedOut = AtomicBoolean(false)
            val watchdog = launch {
                if (timeoutMs > 0L) {
                    delay(timeoutMs)
                    timedOut.set(true)
                    runCatching { process.destroy() }
                }
            }
            val stderrDrain = launch {
                runCatching {
                    process.errorStream.bufferedReader().use { reader ->
                        while (isActive) {
                            if (reader.readLine() == null) break
                        }
                    }
                }
            }

            try {
                runCatching { process.outputStream.use { } }
                process.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        send(line)
                    }
                }
                if (!timedOut.get()) {
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            } finally {
                watchdog.cancel()
                stderrDrain.cancel()
                safeDestroyProcess(process)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams stdout directly into a [Sequence] consumer under the 2-permit gate without buffering
     * the full command output in memory (ideal for large `dumpsys package` scans).
     */
    suspend fun <T> execLineSequence(
        command: String,
        timeoutMs: Long = 30_000L,
        consumer: (Sequence<String>) -> T,
    ): T? = withContext(Dispatchers.IO) {
        if (command.isBlank() || command.length > MAX_COMMAND_LENGTH) return@withContext null

        concurrencyGate.withPermit {
            val process = startRawPrivilegedProcess(command) ?: return@withPermit null
            val watchdog = scope.launch {
                if (timeoutMs > 0L) {
                    delay(timeoutMs)
                    runCatching { process.destroy() }
                }
            }
            val stderrDrain = scope.launch {
                runCatching {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { _ -> }
                    }
                }
            }
            try {
                runCatching { process.outputStream.use { } }
                val value = process.inputStream.bufferedReader().useLines { lines ->
                    consumer(lines)
                }
                process.waitFor(5, TimeUnit.SECONDS)
                value
            } catch (_: Exception) {
                null
            } finally {
                watchdog.cancel()
                stderrDrain.cancel()
                safeDestroyProcess(process)
            }
        }
    }

    private fun startRawPrivilegedProcess(command: String): Process? {
        if (ShizukuProcesses.available()) {
            val shizukuProc = runCatching {
                ShizukuProcesses.start(arrayOf("sh", "-c", command))
            }.getOrNull()
            if (shizukuProc != null) return shizukuProc
        }
        return runCatching {
            ProcessBuilder("su", "-c", command).start()
        }.getOrNull()
    }

    private suspend fun executeAndCollect(
        process: Process,
        timeoutMs: Long,
        maxOutputBytes: Int,
    ): ShellResult = withContext(Dispatchers.IO) {
        try {
            runCatching { process.outputStream.use { } }
            val stdoutDeferred = async(Dispatchers.IO) {
                readBoundedUtf8(process.inputStream, maxOutputBytes)
            }
            val stderrDeferred = async(Dispatchers.IO) {
                readBoundedUtf8(process.errorStream, maxOutputBytes)
            }

            val finished = process.waitFor(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            if (!finished) {
                runCatching { process.destroy() }
                stdoutDeferred.cancel()
                stderrDeferred.cancel()
                return@withContext ShellResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Command timed out after ${timeoutMs}ms",
                    timedOut = true,
                )
            }

            val exit = runCatching { process.exitValue() }.getOrDefault(-1)
            val out = runCatching { stdoutDeferred.await() }.getOrDefault("")
            val err = runCatching { stderrDeferred.await() }.getOrDefault("")
            ShellResult(exitCode = exit, stdout = out, stderr = err, timedOut = false)
        } finally {
            safeDestroyProcess(process)
        }
    }

    private fun readBoundedUtf8(stream: InputStream, maxBytes: Int): String {
        return stream.use { input ->
            val out = ByteArrayOutputStream(minOf(maxBytes, 8_192))
            val buffer = ByteArray(4_096)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val remaining = maxBytes - out.size()
                if (remaining > 0) {
                    out.write(buffer, 0, minOf(read, remaining))
                }
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private fun safeDestroyProcess(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.destroy() }
    }
}
