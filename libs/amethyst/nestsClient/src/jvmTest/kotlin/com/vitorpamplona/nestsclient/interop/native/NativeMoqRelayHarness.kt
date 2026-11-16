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
package com.vitorpamplona.nestsclient.interop.native

import java.io.File
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Boots a native `moq-relay` subprocess + provides the test sidecar
 * binary paths for the cross-stack interop harness.
 *
 *   - `moq-relay` is `cargo install`ed at the version pinned in
 *     `nestsClient/tests/hang-interop/REV` and cached under
 *     `~/.cache/amethyst-nests-interop/hang-interop-cargo/bin/`.
 *   - TLS: `--tls-generate localhost` so the relay self-signs at
 *     startup. Kotlin clients use the existing
 *     `PermissiveCertValidator` to skip chain validation.
 *   - Auth: `--auth-public ""` so connections need no JWT. Real JWT
 *     issuance is exercised separately by the existing
 *     `NostrNestsAuthInteropTest` against the Docker'd `moq-auth`.
 *
 * One harness instance per test class — startup is ~500 ms once the
 * cached binaries exist, so tests amortise cheaply.
 *
 * **Phase 1 status**: harness boots the relay and exposes paths to
 * the (stub) sidecar binaries `hang-listen` / `hang-publish` /
 * `udp-loss-shim`. Phase 2 fills in the sidecars' real subscribe /
 * publish loops. See
 * `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.
 */
class NativeMoqRelayHarness private constructor(
    private val relayProcess: Process,
    private val relayPort: Int,
    private val sidecarsDir: Path,
    private val cargoBinDir: Path,
    /**
     * File the relay's combined stdout/stderr was tee'd to for this
     * boot, when trace-log capture was enabled. Useful for tests that
     * want to attach the per-method relay log to a failure assertion.
     * `null` when the per-method log dir wasn't configured.
     */
    val relayLogFile: Path?,
) : AutoCloseable {
    private var stopped = false

    /** Base relay URL. Append a broadcast namespace for connections. */
    val relayUrl: String get() = "https://127.0.0.1:$relayPort"

    /** UDP loopback (host, port) the relay listens on. */
    fun loopbackHostPort(): Pair<String, Int> = "127.0.0.1" to relayPort

    /** Path to the (Phase-1 stub) hang-listen binary. */
    fun hangListenBin(): Path = sidecarsDir.resolve(binName("hang-listen"))

    /** Path to the (Phase-1 stub) hang-publish binary. */
    fun hangPublishBin(): Path = sidecarsDir.resolve(binName("hang-publish"))

    /** Path to the (Phase-1 stub) udp-loss-shim binary. */
    fun udpLossShimBin(): Path = sidecarsDir.resolve(binName("udp-loss-shim"))

    /** Path to the cargo-installed `moq-token-cli` binary. */
    fun moqTokenBin(): Path = cargoBinDir.resolve(binName("moq-token-cli"))

    override fun close() {
        if (stopped) return
        stopped = true
        runCatching { relayProcess.destroy() }
        if (!relayProcess.waitFor(5, TimeUnit.SECONDS)) {
            runCatching { relayProcess.destroyForcibly() }
        }
    }

    companion object {
        /** Gate property — mirrors `nestsInterop`. */
        const val ENABLE_PROPERTY = "nestsHangInterop"

        /**
         * `interopBuildHangSidecars` writes this. Points to
         * `nestsClient/tests/hang-interop/target/release` where the (Phase-1
         * stub) sidecar binaries live.
         */
        const val SIDECARS_DIR_PROPERTY = "nestsHangInteropSidecarsDir"

        /**
         * Cargo install root used by `interopInstallMoqRelay` /
         * `interopInstallMoqTokenCli`. Sub-directory `bin/` holds
         * `moq-relay` + `moq-token`.
         */
        const val CARGO_BIN_DIR_PROPERTY = "nestsHangInteropCargoBinDir"

        /**
         * Optional dir where each relay subprocess boot writes its
         * combined stdout/stderr to a file. Forwarded by
         * `:nestsClient`'s test task to
         * `nestsClient/build/relay-logs/`. When set, the relay also
         * runs with `RUST_LOG=moq_relay=trace,moq_lite=trace` so the
         * captured file contains the per-broadcast subscribe-routing
         * trace investigated in
         * `nestsClient/plans/2026-05-07-moq-relay-routing-investigation.md`.
         *
         * One file per relay boot; `resetShared(testTag = "<name>")`
         * tags filenames so a sweep produces
         * `<name>-<seq>-<timestamp>.log` and a failed run is easy to
         * locate by test method name. Without the property, the relay
         * runs with `--log-level info` and no per-boot file is
         * produced — keeps the harness's existing behaviour for
         * non-investigatory runs.
         */
        const val RELAY_LOG_DIR_PROPERTY = "nestsHangInteropRelayLogDir"

        private const val PORT_READY_TIMEOUT_MS = 30_000L
        private const val PORT_PROBE_INTERVAL_MS = 200L

        /** Monotonic counter used to disambiguate same-tag boots in one JVM. */
        private val bootSequence = AtomicInteger(0)
        private val logTimestampFmt =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        private val tagSanitiser = Regex("[^A-Za-z0-9._-]")

        fun isEnabled(): Boolean = System.getProperty(ENABLE_PROPERTY) == "true"

        /**
         * JUnit "skipped" if the gate isn't on, like the existing
         * [com.vitorpamplona.nestsclient.interop.NostrNestsHarness.assumeNestsInterop].
         */
        fun assumeHangInterop() {
            if (isEnabled()) return
            val msg =
                "Skipping cross-stack hang interop test — set -D$ENABLE_PROPERTY=true to enable. " +
                    "See nestsClient/plans/2026-05-06-cross-stack-interop-test.md."
            try {
                val assume = Class.forName("org.junit.Assume")
                val assumeTrue = assume.getMethod("assumeTrue", String::class.java, Boolean::class.javaPrimitiveType)
                assumeTrue.invoke(null, msg, false)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e
            } catch (_: ClassNotFoundException) {
                throw IllegalStateException(msg)
            }
        }

        @Volatile private var shared: NativeMoqRelayHarness? = null
        private val sharedLock = Any()

        /**
         * Bring the relay up if not already running; reuses the same
         * subprocess across test classes within one JVM run. Mirrors
         * the singleton pattern in [com.vitorpamplona.nestsclient.interop.NostrNestsHarness].
         *
         * If a relay log dir is configured (see [RELAY_LOG_DIR_PROPERTY])
         * and no shared relay exists yet, the boot is tagged with
         * [testTag]; otherwise the existing relay is reused regardless
         * of tag. Use [resetShared] to force a fresh boot with a
         * specific tag.
         */
        fun shared(testTag: String? = null): NativeMoqRelayHarness {
            shared?.let { return it }
            synchronized(sharedLock) {
                shared?.let { return it }
                val instance = doStart(testTag)
                Runtime.getRuntime().addShutdownHook(
                    Thread({ runCatching { instance.close() } }, "NativeMoqRelayHarness-shutdown"),
                )
                shared = instance
                return instance
            }
        }

        /**
         * Tear down the current shared relay subprocess and start a
         * fresh one. Used as a JUnit `@BeforeTest` hook by
         * `HangInteropTest` and `BrowserInteropTest` so each scenario
         * runs against a relay that started ~500 ms before the test
         * body — under accumulated cross-test broadcasts /
         * connections the relay's per-subscriber forward queues +
         * announce tables drift, manifesting as intermittent
         * catalog-cancel and sample-count flakes that don't reproduce
         * in isolation.
         *
         * Cost: ~500 ms per call (cargo binaries are cached, only
         * the subprocess boot + UDP bind + first client handshake
         * are paid). At 11 scenarios × 500 ms that's ~5.5 s added
         * to the suite wallclock — acceptable trade for stability.
         */
        fun resetShared(testTag: String? = null) {
            synchronized(sharedLock) {
                shared?.let {
                    runCatching { it.close() }
                }
                shared = null
            }
            // Pre-warm so the next caller observes the relay already up
            // tagged with this test method's name. Without this, the
            // first call to shared() after resetShared() picks up the
            // tag of whoever wins the race — usually the test body
            // calling `shared()`, which is fine, but a concurrent
            // listener-side helper may race in first under suite
            // mode. Pre-warming is cheap (~500 ms cargo cache hit)
            // and keeps the per-method log filename stable.
            if (testTag != null && System.getProperty(RELAY_LOG_DIR_PROPERTY) != null) {
                shared(testTag)
            }
        }

        private fun doStart(testTag: String?): NativeMoqRelayHarness {
            check(isEnabled()) {
                "NativeMoqRelayHarness.shared() called without -D$ENABLE_PROPERTY=true."
            }

            val sidecarsDir = requireDirProperty(SIDECARS_DIR_PROPERTY)
            val cargoBinDir = requireDirProperty(CARGO_BIN_DIR_PROPERTY)

            val moqRelay = cargoBinDir.resolve(binName("moq-relay"))
            check(Files.isExecutable(moqRelay)) {
                "moq-relay not found at $moqRelay — did `interopBuildHangSidecars` run? " +
                    "Try: ./gradlew :nestsClient:interopBuildHangSidecars"
            }
            check(Files.isExecutable(sidecarsDir.resolve(binName("hang-listen")))) {
                "hang-listen sidecar not found under $sidecarsDir — did `interopBuildSidecars` run?"
            }

            val port = reservePort()
            val relayLogDir = System.getProperty(RELAY_LOG_DIR_PROPERTY)?.let { File(it) }
            val relayLogFile: File? =
                if (relayLogDir != null) {
                    relayLogDir.mkdirs()
                    val seq = bootSequence.incrementAndGet().toString().padStart(3, '0')
                    val ts = LocalDateTime.now().format(logTimestampFmt)
                    val tag = sanitiseTag(testTag ?: "boot")
                    File(relayLogDir, "$tag-$seq-$ts.log")
                } else {
                    null
                }
            // Keep `--log-level info` as the baseline; the relay's
            // tracing_subscriber EnvFilter honours `RUST_LOG`, which
            // we set to trace on `moq_relay` + `moq_lite` only when
            // capture is enabled. That gives us the per-broadcast
            // subscribe-routing trace investigated in plan
            // `2026-05-07-moq-relay-routing-investigation.md` while
            // keeping quinn/h3/tls noise at info — full-tree trace
            // is ~100s of MB per test, way more than needed.
            val pb =
                ProcessBuilder(
                    moqRelay.toString(),
                    "--server-bind",
                    "127.0.0.1:$port",
                    // moq-relay also opens an outbound clustering
                    // client; its default `[::]:0` bind fails in
                    // sandboxes without IPv6 (errno 97 EAFNOSUPPORT).
                    // Pin to IPv4 loopback to keep the harness
                    // portable across CI runners.
                    "--client-bind",
                    "127.0.0.1:0",
                    "--tls-generate",
                    "localhost",
                    // Empty prefix grants pub+sub on every path, so
                    // tests don't need to mint JWTs. The
                    // moq-relay/auth.rs `verify` path falls through
                    // to public access when no JWT is present.
                    "--auth-public",
                    "",
                    "--log-level",
                    "info",
                ).redirectErrorStream(true)
            if (relayLogFile != null) {
                pb.environment()["RUST_LOG"] =
                    "info,moq_relay=trace,moq_lite=trace,moq_native=debug"
            }

            val process = pb.start()
            val drainer =
                ProcessOutputDrainer(process, "moq-relay", relayLogFile).also { it.start() }

            try {
                // moq-relay logs `addr=… listening` on bind. Wait for
                // that line — strictly more reliable than a port
                // probe (TCP probes succeed on a UDP-only listener,
                // and UDP probes against a SO_REUSEPORT-bound socket
                // can also succeed even when the relay is healthy).
                drainer.waitForLine("listening", PORT_READY_TIMEOUT_MS)
                // Belt-and-braces: also confirm the UDP port is bound.
                // If something's broken in the log path this still
                // catches a non-listening relay within a few seconds.
                waitForUdpBound("127.0.0.1", port, 3_000L)
            } catch (t: Throwable) {
                // Best-effort log capture before tearing down so the
                // failure includes WHY the relay didn't come up.
                val tail = drainer.tail()
                runCatching { process.destroyForcibly() }
                throw IllegalStateException(
                    "moq-relay did not become ready on 127.0.0.1:$port within " +
                        "${PORT_READY_TIMEOUT_MS}ms.\n--- moq-relay log tail ---\n$tail",
                    t,
                )
            }

            return NativeMoqRelayHarness(
                relayProcess = process,
                relayPort = port,
                sidecarsDir = sidecarsDir,
                cargoBinDir = cargoBinDir,
                relayLogFile = relayLogFile?.toPath(),
            )
        }

        /**
         * Strip filesystem-unfriendly characters from a JUnit test
         * method name so it can be used directly in a log filename.
         */
        private fun sanitiseTag(raw: String): String =
            raw
                .replace(tagSanitiser, "_")
                .take(80)
                .ifBlank { "boot" }

        private fun requireDirProperty(name: String): Path {
            val raw = System.getProperty(name)
            check(!raw.isNullOrBlank()) {
                "system property '$name' not set — did the Gradle test task forward it? " +
                    "(see :nestsClient build.gradle.kts)"
            }
            val path = File(raw).toPath()
            check(Files.isDirectory(path)) {
                "system property '$name' = '$raw' is not a directory; " +
                    "did `interopBuildHangSidecars` run?"
            }
            return path
        }

        /**
         * Ask the OS for a free TCP port, close the socket, and use
         * that port number for the relay's UDP listener. There's a
         * tiny window where another process could grab the port; in
         * practice CI loopback is uncontested. Reused from the same
         * pattern used elsewhere in the test infra.
         */
        private fun reservePort(): Int {
            ServerSocket(0).use { return it.localPort }
        }

        /**
         * Confirm the relay's UDP port is bound by trying to bind a
         * *second* `DatagramSocket` on it. If the OS rejects with
         * `BindException`, something owns the port — almost
         * certainly the relay we just spawned. UDP namespace is
         * separate from TCP, so this is the only kind of probe that
         * meaningfully reports "is the relay listening" on a
         * QUIC-only data plane.
         *
         * Defaults to `SO_REUSEADDR=false` so a relay bound without
         * `SO_REUSEPORT` correctly fails our second bind. Falls back
         * to the relay's startup log line as the primary signal —
         * see `doStart` callers.
         */
        private fun waitForUdpBound(
            host: String,
            port: Int,
            timeoutMs: Long,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastError: Throwable? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    val probe = DatagramSocket(null)
                    probe.reuseAddress = false
                    try {
                        probe.bind(InetSocketAddress(host, port))
                        // Bind succeeded → nothing else is on this
                        // UDP port. The relay isn't bound yet.
                    } finally {
                        probe.close()
                    }
                    Thread.sleep(PORT_PROBE_INTERVAL_MS)
                } catch (_: java.net.BindException) {
                    return
                } catch (t: Throwable) {
                    lastError = t
                    Thread.sleep(PORT_PROBE_INTERVAL_MS)
                }
            }
            throw IllegalStateException(
                "moq-relay UDP port $host:$port did not bind within ${timeoutMs}ms",
                lastError,
            )
        }

        private fun binName(stem: String): String =
            if (System
                    .getProperty("os.name")
                    .orEmpty()
                    .lowercase()
                    .contains("win")
            ) {
                "$stem.exe"
            } else {
                stem
            }
    }
}

/**
 * Reads the subprocess's combined stdout/stderr into a bounded ring
 * so the harness can include the tail in a failure message. Without
 * this the relay's log line `listening on 127.0.0.1:<port>` is the
 * only signal that startup succeeded, and a silent error (cert
 * generation failure, port collision after the OS reservation, …)
 * leaves us with nothing to include in the assertion.
 */
private class ProcessOutputDrainer(
    private val process: Process,
    private val name: String,
    /**
     * Optional sink for the full subprocess output. When non-null,
     * every line is also written here verbatim — used by the
     * routing-race investigation (see plan
     * `2026-05-07-moq-relay-routing-investigation.md`) to keep the
     * trace-level log around for post-hoc analysis. The in-memory
     * ring is kept regardless so `tail()` still feeds failure
     * messages.
     */
    private val sinkFile: File? = null,
) {
    private val ring = ConcurrentLinkedQueue<String>()
    private val maxLines = 64
    private var thread: Thread? = null
    private val lock = ReentrantLock()
    private val newLineCond = lock.newCondition()

    fun start() {
        thread =
            Thread({
                // Open the sink file inside the drain thread + tolerant
                // of failure: if `bufferedWriter()` throws (parent dir
                // gone, disk full, file-already-a-directory, …) the
                // drainer must STILL pump the subprocess's
                // stdout/stderr — otherwise the relay's pipe buffer
                // fills (~64 KB on Linux) and the subprocess deadlocks
                // on its next write. Per-test runs are unattended; a
                // misconfigured log dir shouldn't take the whole
                // suite down.
                val writer: java.io.BufferedWriter? =
                    if (sinkFile != null) {
                        try {
                            sinkFile.bufferedWriter()
                        } catch (t: Throwable) {
                            System.err.println(
                                "NativeMoqRelayHarness-$name: failed to open trace log " +
                                    "$sinkFile (${t::class.simpleName}: ${t.message}); " +
                                    "continuing without per-test capture.",
                            )
                            null
                        }
                    } else {
                        null
                    }
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            ring.add(line)
                            while (ring.size > maxLines) ring.poll()
                            if (writer != null) {
                                runCatching {
                                    writer.write(line)
                                    writer.newLine()
                                    // Per-line flush so a hung-test
                                    // post-mortem still has the latest
                                    // line on disk. Tests don't run
                                    // long enough for the syscall cost
                                    // to matter (max ~1k lines/test).
                                    writer.flush()
                                }
                            }
                            lock.withLock { newLineCond.signalAll() }
                        }
                    }
                } finally {
                    runCatching { writer?.close() }
                }
            }, "NativeMoqRelayHarness-$name").apply {
                isDaemon = true
                start()
            }
    }

    fun tail(): String = ring.joinToString("\n")

    /**
     * Block until the drainer has observed a line containing
     * [needle], scanning lines that have already been buffered
     * (handles the race where the relay finished logging "listening"
     * before [waitForLine] was called) plus any new lines that
     * arrive within [timeoutMs]. Throws if the deadline expires
     * before a match. Substring match rather than regex to keep
     * upstream-log-format tweaks from breaking us.
     */
    fun waitForLine(
        needle: String,
        timeoutMs: Long,
    ) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        if (ring.any { it.contains(needle) }) return
        lock.withLock {
            while (true) {
                if (ring.any { it.contains(needle) }) return
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0) {
                    throw IllegalStateException(
                        "did not observe '$needle' in $name output within ${timeoutMs}ms",
                    )
                }
                newLineCond.awaitNanos(remaining)
            }
        }
    }
}
