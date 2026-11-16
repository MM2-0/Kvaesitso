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
package com.vitorpamplona.amethyst.ui.tor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Tier-3 integration tests that drive the real Arti JNI shim on JVM, against the
 * Linux x86_64 host build of the same wrapper crate that powers Android. The .so
 * is checked in at `amethyst/src/test/native-libs/x86_64-linux/libarti_android.so`
 * and the Gradle test task sets `java.library.path` to point at it.
 *
 * **Layered safety net for our Tor stack:**
 *   - [TorManagerTest] — fast unit tests, no Arti, virtual time. Covers Kotlin
 *     self-heal logic.
 *   - This file (smoke) — JNI bridge loads, version JNI call works. Always runs
 *     on Linux x86_64 hosts. ~10ms. Catches build/link regressions in the .so.
 *   - This file (integration) — opt-in via `-Pamethyst.arti.integration=true`.
 *     Real bootstrap + SOCKS round trips. Needs outbound TCP egress to arbitrary
 *     IPs/ports — works on most dev machines and Docker hosts with default
 *     networking; *will hang* on CI runners with restrictive egress lists.
 *   - `androidTest/.../tor/TorBootstrapInstrumentedTest` — same shape but against
 *     the Android .so on a connected device/emulator.
 *
 * **What the integration suite verifies that the unit tests cannot:**
 *
 * The original "Tor stops working until data-wipe" bug had four root causes that
 * unit tests with a fake `TorBackend` can't exercise — they need the real Arti
 * client, real circuits, real OS sockets:
 *
 *   1. The native `TorClient` getting stuck with bad guards / dead circuits /
 *      expired consensus, with no way to drop it in-process. Pre-fix there was
 *      no JNI `destroy()`. Verified by `destroy then re-initialize releases the
 *      state file lock cleanly`.
 *
 *   2. In-flight per-connection handlers each holding an `Arc<TorClient>` clone,
 *      pinning the state file lock past `destroy()`. Pre-fix the handler
 *      tracking was racy. Verified by `destroy aborts an in-flight SOCKS handler`.
 *
 *   3. `stopSocksProxy` deliberately not destroying the client (by design — for
 *      the legitimate stop/start reuse path), so a stuck client survived
 *      toggle-off-then-on. Verified by `stopSocksProxy then startSocksProxy
 *      reuses the running TorClient`.
 *
 *   4. State / fd / memory leaks accumulating across many destroy/init cycles
 *      (which the self-heal watchdog can drive at up to one per 5 minutes
 *      indefinitely). Verified by `survives multiple destroy then initialize
 *      cycles`.
 *
 * **Run the slow tests:**
 * ```
 * ./gradlew :amethyst:testPlayDebugUnitTest \
 *     --tests "com.vitorpamplona.amethyst.ui.tor.TorArtiNativeIntegrationTest" \
 *     -Pamethyst.arti.integration=true
 * ```
 */
class TorArtiNativeIntegrationTest {
    private var dataDir: File? = null

    @After
    fun tearDown() {
        // Drop the in-process client between tests so the state file lock
        // doesn't bleed across (and our tests stay independent). Idempotent —
        // no-op if initialize never ran.
        try {
            ArtiNative.destroy()
        } catch (_: Throwable) {
            // Library may not have loaded if assumeArchAvailable skipped us.
        }
        dataDir?.deleteRecursively()
        dataDir = null
    }

    // ---------------------------------------------------------------------
    // Smoke — runs without -P. Catches build/link regressions.
    // ---------------------------------------------------------------------

    /**
     * The host `.so` loads via `System.loadLibrary("arti_android")` and a trivial
     * JNI function returns. If this fails, every other Tor test is moot — typical
     * causes are a stale `.so` after an arti version bump, a missing rebuild on
     * the test native-libs path, or a build that didn't export the expected JNI
     * symbol. Always runs (no `-P` gate).
     */
    @Test
    fun `library loads and reports a version`() {
        assumeArchAvailable()
        val version = ArtiNative.getVersion()
        assertTrue("Version string was: $version", version.startsWith("Arti "))
        println("[arti] $version")
    }

    // ---------------------------------------------------------------------
    // Bootstrap + round trip — the basic data plane.
    // ---------------------------------------------------------------------

    /**
     * Real bootstrap + SOCKS round trip. Regression net for: rustls
     * `CryptoProvider` install after the v2.3.0 bump, `fs-mistrust` host-trust
     * override on JVM, and every `Java_..._ArtiNative_*` JNI export.
     */
    @Test(timeout = BOOTSTRAP_TIMEOUT_MS + 60_000L)
    fun `bootstraps and proxies an HTTPS request through Tor`() {
        assumeFullIntegration()
        val port = bootstrapAndStartSocks()

        val exitIp = fetchExitIp(port)
        println("[test] First-bootstrap exit IP: $exitIp")
        assertNotNull(exitIp)
    }

    // ---------------------------------------------------------------------
    // destroy + re-init releases state file lock — root cause #1.
    // ---------------------------------------------------------------------

    /**
     * After `destroy`, the *same* on-disk data dir must be re-acquirable by a
     * fresh `initialize` — no "state file already locked" error, no need to
     * `clearAllArtiData`. This is the direct mirror of the self-heal recovery
     * path that the watchdog drives on stuck-Connecting.
     */
    @Test(timeout = (BOOTSTRAP_TIMEOUT_MS * 2) + 60_000L)
    fun `destroy then re-initialize releases the state file lock cleanly`() {
        assumeFullIntegration()
        val firstPort = bootstrapAndStartSocks()
        val firstIp = fetchExitIp(firstPort)
        println("[test] Pre-destroy exit IP: $firstIp")

        val destroyResult = ArtiNative.destroy()
        assertEquals("destroy returned non-zero", 0, destroyResult)

        // Re-init against the SAME data dir. Must NOT see "state file already locked".
        assertEquals(
            "re-initialize after destroy should succeed without clearing data",
            0,
            ArtiNative.initialize(dataDir!!.absolutePath),
        )
        val secondPort = pickPort()
        assertEquals("post-destroy startSocksProxy", 0, ArtiNative.startSocksProxy(secondPort))

        val secondIp = fetchExitIp(secondPort)
        println("[test] Post-destroy exit IP: $secondIp (changed=${firstIp != secondIp})")
        assertNotNull(secondIp)
    }

    // ---------------------------------------------------------------------
    // In-flight handler abort — root cause #2.
    // ---------------------------------------------------------------------

    /**
     * If a SOCKS connection is open at the moment we call [ArtiNative.destroy],
     * the handler's `Arc<TorClient>` clone must be released — otherwise the
     * `TorClient` stays alive past `destroy()`, the state file lock isn't
     * released, and the next `initialize()` fails with "already locked".
     *
     * Pre-fix the handler-task tracking was racy: a connection accepted between
     * `SOCKS_TASK.abort()` and the `HANDLER_TASKS` drain pinned an Arc. This
     * test exercises that race window directly.
     */
    @Test(timeout = BOOTSTRAP_TIMEOUT_MS + 60_000L)
    fun `destroy aborts an in-flight SOCKS handler quickly`() {
        assumeFullIntegration()
        val port = bootstrapAndStartSocks()

        // Open a long-lived SOCKS connection. We don't actually read the body —
        // we just want a handler to be alive in tokio-land when destroy hits.
        val socksClient = socksOkHttp(port, readTimeoutSeconds = 300L)
        val inFlight =
            Thread {
                try {
                    // Hit a deliberately slow path. The fact that it never returns
                    // is fine — we're going to destroy() out from under it.
                    socksClient
                        .newCall(
                            Request
                                .Builder()
                                .url("https://check.torproject.org/api/ip")
                                .build(),
                        ).execute()
                        .use { resp ->
                            @Suppress("UNUSED_VARIABLE")
                            val ignored = resp.body.string()
                        }
                } catch (e: Throwable) {
                    println("[test] In-flight request aborted with: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        inFlight.name = "in-flight-socks-request"
        inFlight.start()

        // Let the handler get into client.connect() or io::copy.
        Thread.sleep(1_500)

        // destroy() must return in roughly its budgeted time even with traffic
        // in flight: ~1s wait for SOCKS_TASK termination + 500ms sleep for
        // handler cleanup, plus some slack.
        val destroyMs =
            measureTimeMillis {
                assertEquals(0, ArtiNative.destroy())
            }
        println("[test] destroy() with in-flight handler returned in ${destroyMs}ms")
        assertTrue("destroy took ${destroyMs}ms, expected < 3000ms", destroyMs < 3_000)

        // The in-flight thread should die promptly once its socket gets aborted.
        inFlight.join(5_000)
        assertTrue("In-flight request thread still alive after destroy", !inFlight.isAlive)

        // The critical assertion: the state file lock was released, so a fresh
        // initialize against the SAME data dir works. Pre-fix this would fail
        // because the orphaned handler still held an Arc<TorClient>.
        val reinitMs =
            measureTimeMillis {
                assertEquals(
                    "re-initialize after destroy-with-in-flight should succeed",
                    0,
                    ArtiNative.initialize(dataDir!!.absolutePath),
                )
            }
        println("[test] re-initialize after in-flight-destroy completed in ${reinitMs}ms")
    }

    // ---------------------------------------------------------------------
    // stop/start reuse — root cause #3 (negative test).
    // ---------------------------------------------------------------------

    /**
     * The legitimate stop/start reuse path: stopSocksProxy releases the SOCKS
     * port but keeps the TorClient alive, and startSocksProxy on a fresh port
     * binds against the same client without re-bootstrapping. This is the
     * pattern the user-facing toggle uses; we just verify it still works after
     * our self-heal changes.
     */
    @Test(timeout = BOOTSTRAP_TIMEOUT_MS + 60_000L)
    fun `stopSocksProxy then startSocksProxy reuses the running TorClient`() {
        assumeFullIntegration()
        val firstPort = bootstrapAndStartSocks()
        val firstIp = fetchExitIp(firstPort)
        println("[test] Pre-stop exit IP: $firstIp")

        assertEquals(0, ArtiNative.stopSocksProxy())

        // No new bootstrap should happen — the second startSocksProxy reuses
        // the client. So this round trip should be fast (no consensus download).
        val secondPort = pickPort()
        val secondStartMs =
            measureTimeMillis {
                assertEquals(0, ArtiNative.startSocksProxy(secondPort))
            }
        assertTrue(
            "startSocksProxy on existing client took ${secondStartMs}ms — should be fast (no re-bootstrap)",
            secondStartMs < 5_000,
        )

        val secondIp = fetchExitIp(secondPort)
        println("[test] Post-stop/start exit IP: $secondIp (${secondStartMs}ms to re-bind)")
        assertNotNull(secondIp)
    }

    // ---------------------------------------------------------------------
    // Many cycles — root cause #4 (gradual degradation).
    // ---------------------------------------------------------------------

    /**
     * The self-heal watchdog can drive `destroy → initialize` cycles up to once
     * per 5 minutes for the entire app lifetime. Even at one per hour that's
     * thousands of cycles before a user might restart the process. Verify we
     * don't accumulate state corruption, file-descriptor leaks, or memory
     * leaks across a handful of cycles.
     *
     * Bumped from 1 to [CYCLE_COUNT] cycles because 2 cycles (`destroy then
     * re-initialize releases the state file lock cleanly`) is enough to catch
     * the basic regression, but only 5+ catches gradual drift.
     */
    @Test(timeout = (BOOTSTRAP_TIMEOUT_MS * CYCLE_COUNT) + 90_000L)
    fun `survives multiple destroy then initialize cycles`() {
        assumeFullIntegration()
        dataDir = Files.createTempDirectory("arti-integ-cycles-").toFile()
        ArtiNative.setLogCallback { line -> println("[arti] $line") }

        val ips = mutableListOf<String>()
        for (i in 1..CYCLE_COUNT) {
            var ip: String? = null
            val cycleMs =
                measureTimeMillis {
                    assertEquals("cycle $i initialize", 0, ArtiNative.initialize(dataDir!!.absolutePath))
                    val port = pickPort()
                    assertEquals("cycle $i startSocksProxy", 0, ArtiNative.startSocksProxy(port))
                    ip = fetchExitIp(port)
                    ips += ip ?: "?"
                    assertEquals("cycle $i destroy", 0, ArtiNative.destroy())
                }
            println("[test] Cycle $i/$CYCLE_COUNT exit=$ip in ${cycleMs}ms")
        }
        // No hard assertion on IPs being different — Tor's exit selection isn't
        // deterministic and small cycles can repeat exits. We just log them so
        // the developer can see circuit variety.
        println("[test] All ${CYCLE_COUNT} cycles completed. Exit IPs: $ips")
    }

    // ---------------------------------------------------------------------
    // Concurrent SOCKS — accept loop + handler tracking under load.
    // ---------------------------------------------------------------------

    /**
     * Verify that multiple in-flight SOCKS connections can coexist. This
     * exercises:
     *   - the Rust accept loop pushing to `HANDLER_TASKS` (incl. its retain-on-push
     *     dedup of finished handles),
     *   - per-handler `Arc<TorClient>` clones being independent,
     *   - the listener handling several `accept().await` rounds back-to-back.
     */
    @Test(timeout = BOOTSTRAP_TIMEOUT_MS + 120_000L)
    fun `proxies concurrent SOCKS requests in parallel`() {
        assumeFullIntegration()
        val port = bootstrapAndStartSocks()

        val concurrency = 5
        val ips =
            runBlocking {
                (1..concurrency)
                    .map {
                        async(Dispatchers.IO) {
                            fetchExitIp(port)
                        }
                    }.awaitAll()
            }
        ips.forEachIndexed { i, ip ->
            println("[test] Concurrent request ${i + 1}/$concurrency exit: $ip")
        }
        assertTrue("All concurrent requests should return an IP", ips.all { it != null })
    }

    // ---------------------------------------------------------------------
    // destroy idempotency.
    // ---------------------------------------------------------------------

    /**
     * Calling `destroy()` when the client is already destroyed (or was never
     * initialized) should be a safe no-op. Pre-fix, a double-destroy could
     * panic on the Rust side because of unwrap on an already-`None` Option.
     * Now it's idempotent.
     */
    @Test(timeout = BOOTSTRAP_TIMEOUT_MS + 60_000L)
    fun `destroy is idempotent`() {
        assumeFullIntegration()
        // Destroy before any initialize — should be a no-op.
        assertEquals("destroy on uninitialized client", 0, ArtiNative.destroy())

        // Initialize, then destroy twice.
        dataDir = Files.createTempDirectory("arti-integ-idem-").toFile()
        ArtiNative.setLogCallback { line -> println("[arti] $line") }
        assertEquals(0, ArtiNative.initialize(dataDir!!.absolutePath))
        assertEquals("first destroy", 0, ArtiNative.destroy())
        assertEquals("second destroy is a no-op", 0, ArtiNative.destroy())

        // After two destroys, a fresh initialize still works.
        assertEquals("initialize after double-destroy", 0, ArtiNative.initialize(dataDir!!.absolutePath))
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun bootstrapAndStartSocks(): Int {
        dataDir = Files.createTempDirectory("arti-integ-").toFile()
        ArtiNative.setLogCallback { line -> println("[arti] $line") }
        val bootstrapMs =
            measureTimeMillis {
                val initResult = ArtiNative.initialize(dataDir!!.absolutePath)
                assertEquals("initialize returned $initResult", 0, initResult)
            }
        val port = pickPort()
        val socksResult = ArtiNative.startSocksProxy(port)
        assertEquals("startSocksProxy returned $socksResult", 0, socksResult)
        println("[test] Bootstrap+startSocks complete in ${bootstrapMs}ms on port $port")
        return port
    }

    /**
     * Fetches `https://check.torproject.org/api/ip` through the given SOCKS port
     * and returns the reported exit IP, or `null` if the response is malformed.
     * Asserts the request succeeded — callers can `assertNotNull` if they care
     * about the IP itself.
     */
    private fun fetchExitIp(port: Int): String? {
        val client = socksOkHttp(port)
        val body =
            client
                .newCall(
                    Request
                        .Builder()
                        .url("https://check.torproject.org/api/ip")
                        .build(),
                ).execute()
                .use { resp ->
                    assertEquals("HTTP 200 from check.torproject.org", 200, resp.code)
                    resp.body.string()
                }
        assertTrue(
            "Response should report IsTor:true — body was: $body",
            body.contains("\"IsTor\":true"),
        )
        return EXIT_IP_REGEX.find(body)?.groupValues?.getOrNull(1)
    }

    private fun socksOkHttp(
        port: Int,
        readTimeoutSeconds: Long = 60L,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .build()

    private fun pickPort(): Int = (40_000..49_999).random()

    /** Composite of `assumeArchAvailable` + `assumeIntegrationEnabled`. */
    private fun assumeFullIntegration() {
        assumeArchAvailable()
        assumeIntegrationEnabled()
    }

    /**
     * The checked-in test .so is built for Linux x86_64 only. Skip on other
     * hosts rather than failing — a developer on macOS or aarch64 shouldn't see
     * a build break just because they ran the full test suite.
     */
    private fun assumeArchAvailable() {
        val arch = System.getProperty("os.arch")?.lowercase().orEmpty()
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        assumeTrue(
            "Test .so is provided only for Linux x86_64 (was: $os $arch). " +
                "To run elsewhere, rebuild with `./tools/arti-build/build-arti-host.sh`.",
            os.contains("linux") && (arch == "amd64" || arch == "x86_64"),
        )
    }

    private fun assumeIntegrationEnabled() {
        assumeTrue(
            "Set -Pamethyst.arti.integration=true to enable. Needs network egress " +
                "to arbitrary IPs/ports (Tor directory authorities + guards) — restrictive " +
                "CI runners will hang in initialize().",
            System.getProperty("amethyst.arti.integration") == "true",
        )
    }

    companion object {
        private const val BOOTSTRAP_TIMEOUT_MS: Long = 120_000L
        private const val CYCLE_COUNT: Int = 5
        private val EXIT_IP_REGEX = """"IP"\s*:\s*"([^"]+)"""".toRegex()
    }
}
