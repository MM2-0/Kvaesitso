/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.service.upload.video

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Runs one child process to completion, feeding its stdout lines to the caller. */
fun interface ProcessRunner {
    /**
     * Returns when the process exits, or with exit code -1 when it printed nothing on stdout for
     * [stallTimeoutMs] and was killed. Coroutine cancellation kills it too.
     */
    suspend fun run(
        command: List<String>,
        onStdoutLine: (String) -> Unit,
        stallTimeoutMs: Long,
    ): ProcessOutcome
}

data class ProcessOutcome(
    val exitCode: Int,
    val stderrTail: String,
)

/**
 * The real [ProcessRunner] for ffmpeg: every live process is registered so a JVM shutdown hook
 * can kill it (uploads run in a ViewModel scope and the window may close mid-encode), stderr is
 * drained on its own thread with only its tail kept, and a stall watchdog replaces any overall
 * timeout. This and [FfmpegProbe] are the only places that block on a child process, so they
 * are the only ones that move to [Dispatchers.IO].
 */
object FfmpegProcesses : ProcessRunner {
    private const val TAG = "FfmpegProcesses"
    private const val POLL_MS = 200L
    private const val STDERR_TAIL_CHARS = 2048

    internal val live: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    /** One encode at a time: N videos in a post must not start N ffmpeg processes on every core. */
    val encodeGate = Semaphore(1)

    /**
     * Set by the shutdown hook. A child killed by the hook must read as a cancellation, never as
     * an encoder failure: the fallback loop would otherwise start the next candidate (or the
     * remux) after the hook has run, and nothing would kill that one.
     */
    @Volatile
    internal var shuttingDown = false

    /** What the JVM shutdown hook does; callable by tests. */
    internal fun shutdownNow() {
        shuttingDown = true
        live.forEach { it.destroyForcibly() }
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread(::shutdownNow))
    }

    override suspend fun run(
        command: List<String>,
        onStdoutLine: (String) -> Unit,
        stallTimeoutMs: Long,
    ): ProcessOutcome =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(command).start()
            live += process
            if (shuttingDown) {
                // The hook may have run before the registration and never seen this child.
                process.destroyForcibly()
                live -= process
                throw CancellationException("JVM is shutting down")
            }
            val lastOutputAt = AtomicLong(System.nanoTime())
            val stderrTail = StringBuilder()
            val stdoutReader =
                thread(name = "ffmpeg-stdout", isDaemon = true) {
                    runCatching {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach {
                                lastOutputAt.set(System.nanoTime())
                                onStdoutLine(it)
                            }
                        }
                    }
                }
            val stderrReader =
                thread(name = "ffmpeg-stderr", isDaemon = true) {
                    runCatching {
                        process.errorStream.bufferedReader().use { reader ->
                            val buffer = CharArray(1024)
                            while (true) {
                                val n = reader.read(buffer)
                                if (n < 0) break
                                synchronized(stderrTail) {
                                    stderrTail.append(buffer, 0, n)
                                    if (stderrTail.length > STDERR_TAIL_CHARS) stderrTail.delete(0, stderrTail.length - STDERR_TAIL_CHARS)
                                }
                            }
                        }
                    }
                }
            var stalled = false
            try {
                process.outputStream.close()
                while (!process.waitFor(POLL_MS, TimeUnit.MILLISECONDS)) {
                    ensureActive()
                    if (System.nanoTime() - lastOutputAt.get() > TimeUnit.MILLISECONDS.toNanos(stallTimeoutMs)) {
                        Log.w(TAG) { "No output for $stallTimeoutMs ms; killing ${command.firstOrNull()}" }
                        process.destroyForcibly().waitFor()
                        stalled = true
                        break
                    }
                }
            } catch (e: Throwable) {
                // Cancellation, an interrupt or a failed stdin close: whatever ended the wait, the child must not outlive it.
                process.destroyForcibly().waitFor(2, TimeUnit.SECONDS)
                throw e
            } finally {
                live -= process
                // Both readers end at EOF once the child is gone; give the tail a moment to land.
                stdoutReader.join(1_000)
                stderrReader.join(1_000)
            }
            if (shuttingDown) throw CancellationException("JVM is shutting down")
            ProcessOutcome(if (stalled) -1 else process.exitValue(), synchronized(stderrTail) { stderrTail.toString() })
        }

    /**
     * Runs [command] and returns its stdout, or null when it could not start, exited non-zero or
     * stalled, logged under [tag] as [what]. Cancellation propagates.
     */
    suspend fun captureStdout(
        command: List<String>,
        stallTimeoutMs: Long,
        tag: String,
        what: String,
    ): String? {
        val output = StringBuilder()
        val outcome =
            try {
                run(command, { output.append(it).append('\n') }, stallTimeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "$what failed", e)
                return null
            }
        if (outcome.exitCode != 0) {
            Log.w(tag) { "$what exited ${outcome.exitCode}: ${outcome.stderrTail.trim()}" }
            return null
        }
        return output.toString()
    }
}
