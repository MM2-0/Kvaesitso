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
import java.util.concurrent.TimeUnit

/**
 * Phase 4 (T16) Kotlin-side shim that drives a headless Chromium
 * harness via Playwright. Mirrors the role `hang-listen` plays for
 * the Phase 2 Rust-listener scenarios, but the listener is a
 * Chromium tab loading [openListenPage] / [openPublishPage] from
 * a bun static server.
 *
 * Two subprocesses per scenario:
 *   1. **bun static + WebSocket back-channel** (`server.ts`): serves
 *      the bundled `listen.html` / `publish.html` and writes any PCM
 *      frames the page posts over WS to the file the test reads.
 *   2. **`npx playwright test`** (or `bun x playwright test`): one-off
 *      Chromium spawn that opens the harness page and waits for
 *      `body[data-state="done"]`.
 *
 * Both are spawned per-scenario for isolation — sharing the bun
 * server across scenarios would race the PCM-output file across
 * runs, and sharing a Chromium across runs invites stale
 * `AudioContext` / `WebTransport` state.
 *
 * Gate: `-DnestsBrowserInterop=true`. The Gradle `Test` task hooks
 * `interopBuildBrowserHarness` + `interopInstallPlaywrightChromium`
 * dependencies onto this gate (see `nestsClient/build.gradle.kts`).
 */
internal object PlaywrightDriver {
    /** Gate property — mirrors [NativeMoqRelayHarness.ENABLE_PROPERTY]. */
    const val ENABLE_PROPERTY = "nestsBrowserInterop"

    /**
     * Forwarded by Gradle: absolute path to `nestsClient/tests/browser-interop/`.
     */
    const val HARNESS_DIR_PROPERTY = "nestsBrowserInteropHarnessDir"

    fun isEnabled(): Boolean = System.getProperty(ENABLE_PROPERTY) == "true"

    /**
     * JUnit "skipped" if the gate isn't on. Mirrors
     * [NativeMoqRelayHarness.assumeHangInterop].
     */
    fun assumeBrowserInterop() {
        if (isEnabled()) return
        val msg =
            "Skipping browser interop test — set -D$ENABLE_PROPERTY=true to enable. " +
                "See nestsClient/plans/2026-05-06-phase4-browser-harness.md."
        try {
            val assume = Class.forName("org.junit.Assume")
            val assumeTrue =
                assume.getMethod("assumeTrue", String::class.java, Boolean::class.javaPrimitiveType)
            assumeTrue.invoke(null, msg, false)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        } catch (_: ClassNotFoundException) {
            throw IllegalStateException(msg)
        }
    }

    /**
     * Outcome handed back to the test once the Chromium harness has
     * finished. [pcmFile] holds the Float32 LE PCM bytes the listener
     * page wrote via the WS back-channel; [playwrightStdout] is the
     * combined stdout/stderr of the `npx playwright test` invocation
     * (Kotlin parses the trailing JSON line for diagnostic metadata).
     */
    data class HarnessRun(
        val pcmFile: File,
        val playwrightStdout: String,
        val exitCode: Int,
    )

    /**
     * Spawn the listener harness:
     *   1. Pick an ephemeral port (via `ServerSocket(0)`),
     *   2. Start `bun run server.ts --port <p> --root dist --out-pcm <pcm>`,
     *   3. Wait for the server's `ready` line,
     *   4. Spawn `npx playwright test` with NESTS_HARNESS_URL =
     *      `http://127.0.0.1:<p>/listen.html?relay=<…>&broadcast=<pubkey>&wsPort=<p>&duration=<sec>`,
     *   5. Block until Playwright exits or [overallTimeoutSec] elapses,
     *   6. Tear down both subprocesses.
     *
     * The relay URL passed to the page is the *full* connect target
     * (path + `?jwt=` query), built by [buildHarnessRelayUrl] —
     * Chromium's WebTransport driver consumes it directly.
     */
    fun openListenPage(
        relayUrlFull: String,
        broadcastPath: String,
        durationSec: Int,
        overallTimeoutSec: Int = durationSec + 30,
        track: String = "audio/data",
        serverCertHashB64: String? = null,
        channels: Int = 1,
    ): HarnessRun {
        val certPart =
            if (serverCertHashB64 != null) {
                "&certSha256=" + java.net.URLEncoder.encode(serverCertHashB64, Charsets.UTF_8)
            } else {
                ""
            }
        // Always pass the channel count so listen.ts can configure
        // its WebCodecs AudioDecoder with the matching value. The
        // hang-tier I4 uses 2 (440/660 stereo); the rest use 1.
        val extraQuery = "$certPart&channels=$channels"
        return run(
            "listen.html",
            relayUrlFull,
            broadcastPath,
            durationSec,
            overallTimeoutSec,
            track,
            extraQuery,
        )
    }

    /**
     * Spawn the publisher harness. Symmetric to [openListenPage] but
     * loads `publish.html` and passes the oscillator parameters.
     * Phase 4.C scenarios — the I1-forward smoke test does NOT use this.
     *
     * @param serverCertHashB64 Base64-encoded SHA-256 of the relay's
     *   leaf DER cert. Same channel as [openListenPage]; required so
     *   Chromium's WebTransport accepts the test harness's
     *   self-signed cert.
     * @param reconnectAfterMs If > 0, the publisher cycles its moq-lite
     *   session at this mark — drops the current Connection, builds a
     *   fresh one, re-publishes the same broadcast suffix. Used by the
     *   Browser I7 scenario.
     */
    @Suppress("LongParameterList")
    fun openPublishPage(
        relayUrlFull: String,
        broadcastPath: String,
        freqHz: Int,
        channels: Int,
        durationSec: Int,
        overallTimeoutSec: Int = durationSec + 30,
        track: String = "audio/data",
        serverCertHashB64: String? = null,
        reconnectAfterMs: Long = 0L,
    ): HarnessRun {
        val certPart =
            if (serverCertHashB64 != null) {
                "&certSha256=" + java.net.URLEncoder.encode(serverCertHashB64, Charsets.UTF_8)
            } else {
                ""
            }
        val reconnectPart =
            if (reconnectAfterMs > 0) "&reconnectAfterMs=$reconnectAfterMs" else ""
        val extraQuery = "&freqHz=$freqHz&channels=$channels$certPart$reconnectPart"
        return run(
            "publish.html",
            relayUrlFull,
            broadcastPath,
            durationSec,
            overallTimeoutSec,
            track,
            extraQuery,
        )
    }

    private fun run(
        page: String,
        relayUrlFull: String,
        broadcastPath: String,
        durationSec: Int,
        overallTimeoutSec: Int,
        track: String,
        extraQuery: String = "",
    ): HarnessRun {
        check(isEnabled()) {
            "PlaywrightDriver.run called without -D$ENABLE_PROPERTY=true."
        }
        val harnessDir = requireHarnessDir()
        val distDir =
            File(harnessDir, "dist").apply {
                check(isDirectory) {
                    "browser harness dist/ missing at $absolutePath — did " +
                        "`./gradlew :nestsClient:interopBuildBrowserHarness` run?"
                }
            }

        // 1) Reserve a port for the bun server (it binds to 127.0.0.1
        //    on the same number; a tiny race window but loopback in CI
        //    is uncontested, same pattern as NativeMoqRelayHarness).
        val bunPort = java.net.ServerSocket(0).use { it.localPort }
        val pcmFile = File.createTempFile("browser-pcm", ".bin").also { it.deleteOnExit() }

        val bun = resolveBunBinary()
        val bunProc =
            ProcessBuilder(
                bun,
                "run",
                File(harnessDir, "src/server.ts").absolutePath,
                "--port",
                bunPort.toString(),
                "--root",
                distDir.absolutePath,
                "--out-pcm",
                pcmFile.absolutePath,
            ).directory(harnessDir)
                .redirectErrorStream(true)
                .start()
        val bunDrainer = PlaywrightProcessDrainer(bunProc, "bun-server").also { it.start() }

        try {
            bunDrainer.waitForLine("ready", BUN_READY_TIMEOUT_MS)

            // 2) Compose the harness page URL. The relay URL is already
            //    a `https://host:port/path?jwt=...` string from
            //    `buildRelayConnectTarget` — URL-encode it once for the
            //    `?relay=` slot so the inner `?jwt=` doesn't truncate.
            val encodedRelay =
                java.net.URLEncoder.encode(relayUrlFull, Charsets.UTF_8)
            val pageUrl =
                "http://127.0.0.1:$bunPort/$page" +
                    "?relay=$encodedRelay" +
                    "&broadcast=$broadcastPath" +
                    "&track=$track" +
                    "&wsPort=$bunPort" +
                    "&duration=$durationSec" +
                    extraQuery

            // 3) Spawn Playwright. Use bun's `bun x` if available so we
            //    don't need a separate node install; falls back to npx.
            val pwCmd = mutableListOf<String>()
            if (File(bun).canExecute()) {
                pwCmd += listOf(bun, "x", "playwright", "test", "--config=playwright.config.ts")
            } else {
                pwCmd += listOf("npx", "playwright", "test", "--config=playwright.config.ts")
            }
            val pwProc =
                ProcessBuilder(pwCmd)
                    .directory(harnessDir)
                    .redirectErrorStream(true)
                    .also { pb ->
                        pb.environment()["NESTS_HARNESS_URL"] = pageUrl
                        pb.environment()["NESTS_TIMEOUT_MS"] =
                            (overallTimeoutSec * 1_000).toString()
                        // Inherit PLAYWRIGHT_BROWSERS_PATH if the host
                        // has it (the agent runner ships it pointing at
                        // /opt/pw-browsers); otherwise Playwright falls
                        // back to ~/.cache/ms-playwright.
                        // No-op when env is already inherited.
                    }.start()
            val pwDrainer = PlaywrightProcessDrainer(pwProc, "playwright").also { it.start() }

            val exited = pwProc.waitFor(overallTimeoutSec.toLong(), TimeUnit.SECONDS)
            if (!exited) {
                runCatching { pwProc.destroyForcibly() }
                val tail = pwDrainer.tail()
                throw IllegalStateException(
                    "Playwright did not exit within ${overallTimeoutSec}s.\n" +
                        "--- playwright tail ---\n$tail",
                )
            }
            // Allow the bun server a brief moment to flush the WS frames
            // it's still writing to disk before we read the PCM.
            Thread.sleep(200)
            return HarnessRun(
                pcmFile = pcmFile,
                playwrightStdout = pwDrainer.tail(),
                exitCode = pwProc.exitValue(),
            )
        } finally {
            runCatching { bunProc.destroy() }
            if (!bunProc.waitFor(3, TimeUnit.SECONDS)) {
                runCatching { bunProc.destroyForcibly() }
            }
        }
    }

    private fun requireHarnessDir(): File {
        val raw = System.getProperty(HARNESS_DIR_PROPERTY)
        check(!raw.isNullOrBlank()) {
            "system property '$HARNESS_DIR_PROPERTY' not set — did the Gradle test task forward it?"
        }
        val dir = File(raw)
        check(dir.isDirectory) {
            "$HARNESS_DIR_PROPERTY = '$raw' is not a directory"
        }
        return dir
    }

    private fun resolveBunBinary(): String {
        System.getenv("BUN_BIN")?.let { return it }
        System.getProperty("bunBin")?.let { return it }
        val agentPath = "/root/.bun/bin/bun"
        if (File(agentPath).canExecute()) return agentPath
        return "bun"
    }

    private const val BUN_READY_TIMEOUT_MS = 30_000L
}

/**
 * Captures the relay's leaf certificate during a QUIC TLS handshake
 * so the test driver can pin it via Chromium's
 * `WebTransport({ serverCertificateHashes: [...] })` option.
 *
 * Why we need this: Chromium's `--ignore-certificate-errors` flag does
 * NOT apply to QUIC — see crbug.com/1190655 — so we can't simply skip
 * certificate validation the way the Kotlin clients do.
 * `serverCertificateHashes` is the supported alternative for
 * test-only WebTransport pinning, accepting a SHA-256 of the entire
 * DER-encoded X.509 certificate as long as the cert is ECDSA P-256
 * and valid for ≤ 14 days. moq-relay's `--tls-generate` produces
 * exactly that (rcgen default = ECDSA P-256, validity = 14 days; see
 * `kixelated/moq/rs/moq-native/src/tls.rs:140`), so we can pin it.
 */
internal class CertCapturingValidator : com.vitorpamplona.quic.tls.CertificateValidator {
    @Volatile private var captured: ByteArray? = null

    override fun validateChain(
        chain: List<ByteArray>,
        expectedHost: String,
    ) {
        if (captured == null && chain.isNotEmpty()) {
            captured = chain.first().copyOf()
        }
    }

    override fun verifySignature(
        signatureAlgorithm: Int,
        signature: ByteArray,
        transcriptHash: ByteArray,
    ) {
        // No-op; we're just here for the cert.
    }

    /** SHA-256 of the captured DER cert, base64-encoded. Null until handshake completes. */
    fun derSha256(): ByteArray? {
        val der = captured ?: return null
        return java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(der)
    }
}

/**
 * Minimal stdout drainer for the bun + Playwright subprocesses.
 * Mirrors the private one in `NativeMoqRelayHarness.kt` — kept
 * separate so the two test entry points don't share file-private
 * symbols.
 */
private class PlaywrightProcessDrainer(
    private val process: Process,
    private val name: String,
) {
    private val ring = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val maxLines = 256
    private val lock =
        java.util.concurrent.locks
            .ReentrantLock()
    private val newLineCond = lock.newCondition()

    fun start() {
        Thread({
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    ring.add(line)
                    while (ring.size > maxLines) ring.poll()
                    lock.lock()
                    try {
                        newLineCond.signalAll()
                    } finally {
                        lock.unlock()
                    }
                }
            }
        }, "PlaywrightDriver-$name").apply {
            isDaemon = true
            start()
        }
    }

    fun tail(): String = ring.joinToString("\n")

    fun waitForLine(
        needle: String,
        timeoutMs: Long,
    ) {
        val deadlineNanos =
            System.nanoTime() +
                java.util.concurrent.TimeUnit.MILLISECONDS
                    .toNanos(timeoutMs)
        if (ring.any { it.contains(needle) }) return
        lock.lock()
        try {
            while (true) {
                if (ring.any { it.contains(needle) }) return
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0) {
                    throw IllegalStateException(
                        "did not observe '$needle' in $name output within ${timeoutMs}ms.\n" +
                            "--- $name tail ---\n${tail()}",
                    )
                }
                newLineCond.awaitNanos(remaining)
            }
        } finally {
            lock.unlock()
        }
    }
}
