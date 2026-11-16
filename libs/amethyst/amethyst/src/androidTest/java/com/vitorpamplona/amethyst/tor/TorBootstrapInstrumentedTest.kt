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
package com.vitorpamplona.amethyst.tor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.ui.tor.TorService
import com.vitorpamplona.amethyst.ui.tor.TorServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Real-Arti bootstrap + SOCKS round trip on-device. Verifies that the self-heal /
 * destroy / re-init paths work end-to-end against the actual native lib.
 *
 * **This test is [Ignore]'d by default** because:
 *   - It needs network egress to the Tor network from the device/emulator. Many CI
 *     environments don't have it.
 *   - Bootstrap on a cold device can take 30-120s; the test costs real wall-clock time.
 *   - It depends on `check.torproject.org` being reachable.
 *
 * **To run manually:**
 *   1. Connect a device or start an emulator that has internet egress to Tor.
 *   2. Remove the `@Ignore` annotation below.
 *   3. `./gradlew :amethyst:connectedPlayDebugAndroidTest -P android.testInstrumentationRunnerArguments.class=com.vitorpamplona.amethyst.tor.TorBootstrapInstrumentedTest`
 *
 * **What it covers that [TorManagerTest] does not:**
 *   - Real `ArtiNative.initialize` → `create_unbootstrapped_async` → SOCKS listener bind.
 *   - Real rustls `CryptoProvider` install (regression check after the arti-v2.3.0 bump).
 *   - Real `destroy()` releasing the state file lock so a second `initialize()` succeeds.
 *   - OkHttp routing traffic through the SOCKS port and Arti exiting through the
 *     Tor network.
 *
 * **Companion fast tests:** `amethyst/src/test/.../tor/TorManagerTest.kt` covers the
 * Kotlin-side self-heal logic (watchdog, cooldown, network change, status routing)
 * with virtual time and in-memory fakes — no Arti required.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@Ignore("Tier-3 integration test — requires on-device network access to Tor. See class kdoc to enable.")
class TorBootstrapInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * [TorService] promotes Bootstrapping -> Active from a coroutine on this scope, so the test
     * must own one and cancel it — without a live scope `status` would never reach Active and every
     * assertion below would hang until its timeout.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val torService = TorService(context, scope)

    @After
    fun tearDown() =
        runBlocking {
            // Drop the native client so this test's state file lock doesn't bleed into
            // the next instrumented run on the same device.
            torService.reset()
            scope.cancel()
        }

    /**
     * Cold-start bootstrap. The whole point of the custom Arti build is that this
     * works at all — if client creation panics (e.g., because we forgot to install
     * a rustls CryptoProvider after an arti bump) the test catches it.
     */
    @Test
    fun bootstraps_to_Active_within_120s() =
        runBlocking(Dispatchers.IO) {
            val elapsed =
                measureTimeMillis {
                    torService.start()
                    val active =
                        withTimeout(BOOTSTRAP_TIMEOUT_MS) {
                            torService.status.first { it is TorServiceStatus.Active }
                        } as TorServiceStatus.Active
                    assertTrue("SOCKS port should be > 0", active.port > 0)
                }
            // Logged via assertEquals failure-on-too-slow; an actual `Log.i` would be invisible.
            // Bootstrap should comfortably fit in 120s on a healthy network.
            assertTrue("Bootstrap took ${elapsed}ms, expected < ${BOOTSTRAP_TIMEOUT_MS}ms", elapsed < BOOTSTRAP_TIMEOUT_MS)
        }

    /**
     * SOCKS round-trip through Tor. Hits `check.torproject.org` which returns a JSON
     * payload including `"IsTor":true` when the request actually exited via Tor.
     * Catches regressions where the listener binds but no traffic flows (e.g., a
     * broken handler-spawn race, or a crypto provider mismatch on the TLS handshake).
     */
    @Test
    fun proxies_HTTPS_through_Tor_and_reports_IsTor_true() =
        runBlocking(Dispatchers.IO) {
            torService.start()
            val active =
                withTimeout(BOOTSTRAP_TIMEOUT_MS) {
                    torService.status.first { it is TorServiceStatus.Active }
                } as TorServiceStatus.Active

            val client =
                OkHttpClient
                    .Builder()
                    .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", active.port)))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

            val request =
                Request
                    .Builder()
                    .url("https://check.torproject.org/api/ip")
                    .build()

            val body =
                client.newCall(request).execute().use { resp ->
                    assertEquals("HTTP 200", 200, resp.code)
                    resp.body.string()
                }
            assertTrue(
                "Response should report IsTor:true — actual body: $body",
                body.contains("\"IsTor\":true"),
            )
        }

    /**
     * Verifies the destroy → re-init cycle that backs the self-heal path. After
     * [TorService.reset], the next [TorService.start] must rebuild the TorClient and
     * bring SOCKS back to Active — without a "state file already locked" error from
     * the still-alive previous client.
     */
    @Test
    fun reset_then_re_start_brings_SOCKS_back_to_Active() =
        runBlocking(Dispatchers.IO) {
            torService.start()
            withTimeout(BOOTSTRAP_TIMEOUT_MS) {
                torService.status.first { it is TorServiceStatus.Active }
            }

            torService.reset()
            assertEquals(TorServiceStatus.Off, torService.status.value)

            torService.start()
            val second =
                withTimeout(BOOTSTRAP_TIMEOUT_MS) {
                    torService.status.first { it is TorServiceStatus.Active }
                } as TorServiceStatus.Active
            assertTrue("Second bootstrap port valid", second.port > 0)
        }

    companion object {
        private const val BOOTSTRAP_TIMEOUT_MS: Long = 120_000L
    }
}
