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
package com.vitorpamplona.amethyst.service.resourceusage

import com.vitorpamplona.amethyst.commons.model.innermostSigner
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.service.resourceusage.BatteryDrainSampler
import com.vitorpamplona.amethyst.commons.service.resourceusage.ForegroundTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.RadioBurstEstimator
import com.vitorpamplona.amethyst.commons.service.resourceusage.RelayConnectionTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.RelayUsageListener
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageAccountant
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageAlerts
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageStore
import com.vitorpamplona.amethyst.commons.service.resourceusage.ScreenTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.SessionTimeIntegrator
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageCountingInterceptor
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageSummary
import com.vitorpamplona.amethyst.service.playback.playerPool.MediaPlayTimeTracker
import com.vitorpamplona.amethyst.service.resourceusage.MeteringNostrSigner
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResourceUsageStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(temp.root, ResourceUsageStore.FILE_NAME)
    }

    @Test
    fun mergesAndReloadsFromDisk() =
        runTest {
            val store = ResourceUsageStore(file)
            store.mergeInto(100, mapOf("a" to 5L, "b" to 2L))
            store.mergeInto(100, mapOf("a" to 3L))
            store.mergeInto(101, mapOf("a" to 1L))

            val reloaded = ResourceUsageStore(file).allDays()
            assertEquals(8L, reloaded[100]?.get("a"))
            assertEquals(2L, reloaded[100]?.get("b"))
            assertEquals(1L, reloaded[101]?.get("a"))
        }

    @Test
    fun prunesBucketsOlderThanKeepDays() =
        runTest {
            val store = ResourceUsageStore(file, keepDays = 7)
            store.mergeInto(100, mapOf("a" to 1L))
            store.mergeInto(110, mapOf("a" to 1L))

            val days = store.allDays()
            assertNull("day 100 is older than 110-7 and must be pruned", days[100])
            assertEquals(1L, days[110]?.get("a"))
        }

    @Test
    fun alertStateRoundTrips() =
        runTest {
            val store = ResourceUsageStore(file)
            assertEquals(0L, store.lastAlertAtSec())
            assertFalse(store.alertsOptOut())

            store.markAlertPrompted(12345L)
            store.setAlertsOptOut(true)

            val reloaded = ResourceUsageStore(file)
            assertEquals(12345L, reloaded.lastAlertAtSec())
            assertTrue(reloaded.alertsOptOut())
        }
}

class ResourceUsageAccountantTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accumulatesAndFlushesIntoTheRightDay() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            var day = 200L
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { day })

            accountant.add("x", 5)
            accountant.add("x", 5)
            accountant.flush()
            day = 201L
            accountant.add("x", 7)
            accountant.flush()

            val days = store.allDays()
            assertEquals(10L, days[200]?.get("x"))
            assertEquals(7L, days[201]?.get("x"))
        }

    @Test
    fun liveCountersAreVisibleBeforeFlush() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 300L })

            accountant.add("x", 42)
            val days = accountant.allDaysIncludingLive()
            assertEquals(42L, days[300]?.get("x"))
            // and not yet on disk
            assertNull(store.allDays()[300])
        }

    @Test
    fun countersKeepAccumulatingAfterADrain() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 200L })

            accountant.add("x", 5)
            accountant.flush()
            accountant.add("x", 7)
            accountant.flush()

            assertEquals(12L, store.allDays()[200]?.get("x"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun hookAddsAreDrainedInPlaceAndNeverRearmTheFlushLoop() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 300L })
            var hookRuns = 0
            accountant.addPreFlushHook {
                hookRuns++
                accountant.add("hook.counter", 1)
            }

            accountant.add("x", 1)
            testScheduler.advanceTimeBy(31_000)
            testScheduler.runCurrent()

            assertEquals("the one debounced flush ran its hooks once", 1, hookRuns)
            assertEquals(1L, store.allDays()[300]?.get("hook.counter"))

            // If hook adds re-armed the debounce, more flushes would fire and
            // the hook counter would keep growing without any real activity.
            testScheduler.advanceTimeBy(300_000)
            testScheduler.runCurrent()
            assertEquals("no self-perpetuating flush loop", 1, hookRuns)
            assertEquals(1L, store.allDays()[300]?.get("hook.counter"))
        }

    @Test
    fun preFlushHooksRunOnFlushAndRead() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 300L })
            var hookRuns = 0
            accountant.addPreFlushHook { hookRuns++ }

            accountant.flush()
            accountant.allDaysIncludingLive()
            assertEquals(2, hookRuns)
        }
}

class RelayConnectionTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun integratesConnectionTimeAcrossStateChanges() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })

            var now = 0L
            val count = MutableStateFlow(0)
            val mobile = MutableStateFlow<Boolean?>(false)
            val fg = MutableStateFlow(true)

            val integrator =
                RelayConnectionTimeIntegrator(
                    connectedCount = count,
                    isMobile = mobile,
                    isForeground = fg,
                    accountant = accountant,
                    nowMs = { now },
                )
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            // 5 relays connected on wifi foreground for 10s
            count.value = 5
            testScheduler.runCurrent()
            now = 10_000L

            // switch to cellular background: closes the wifi segment
            mobile.value = true
            fg.value = false
            testScheduler.runCurrent()

            // 5 relays on cellular background for 20s, then all disconnect
            now = 30_000L
            count.value = 0
            testScheduler.runCurrent()

            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(5 * 10_000L, counters[UsageKeys.relayConnMs(mobile = false, foreground = true)])
            assertEquals(5 * 20_000L, counters[UsageKeys.relayConnMs(mobile = true, foreground = false)])
            job.cancel()
        }

    @Test
    fun closeOpenSegmentAccountsLongStableSessions() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })

            var now = 0L
            val count = MutableStateFlow(3)
            val integrator =
                RelayConnectionTimeIntegrator(
                    connectedCount = count,
                    isMobile = MutableStateFlow(true),
                    isForeground = MutableStateFlow(false),
                    accountant = accountant,
                    nowMs = { now },
                )
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            // hours pass with no state change at all (always-on background)
            now = 2 * 60 * 60 * 1000L
            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(
                "reading the ledger must account the still-open segment",
                3 * 2 * 60 * 60 * 1000L,
                counters[UsageKeys.relayConnMs(mobile = true, foreground = false)],
            )
            job.cancel()
        }
}

class ProcessCpuSamplerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accountsCpuDeltasAcrossSamples() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var cpu = 1_000L
            val sampler = ProcessCpuSampler(accountant) { cpu }

            cpu = 1_500L
            sampler.sample()
            cpu = 1_800L
            sampler.sample()

            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(800L, counters[UsageKeys.CPU_MS])
        }
}

class ForegroundTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accountsOnlyForegroundTime() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var now = 0L
            val fg = MutableStateFlow(false)
            val integrator = ForegroundTimeIntegrator(fg, accountant) { now }
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            // 60s in background — must not count
            now = 60_000L
            fg.value = true
            testScheduler.runCurrent()

            // 30s in foreground — counts
            now = 90_000L
            fg.value = false
            testScheduler.runCurrent()

            // 60s more in background, then read: still 30s
            now = 150_000L
            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(30_000L, counters[UsageKeys.APP_FG_MS])
            job.cancel()
        }

    @Test
    fun openForegroundSegmentIsAccountedOnRead() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var now = 0L
            val fg = MutableStateFlow(true)
            val integrator = ForegroundTimeIntegrator(fg, accountant) { now }
            val job = integrator.start(backgroundScope)
            testScheduler.runCurrent()

            now = 45_000L
            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(45_000L, counters[UsageKeys.APP_FG_MS])
            job.cancel()
        }
}

class RadioBurstEstimatorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun countsBurstsOnlyAfterRadioIdleGaps() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            var now = 100_000L
            val estimator =
                RadioBurstEstimator(
                    accountant = accountant,
                    isMobile = { true },
                    isForeground = { false },
                    nowMs = { now },
                )

            estimator.onHttpActivity() // first activity = one burst
            now += 2_000
            estimator.onHttpActivity() // 2s later: same burst
            now += RadioBurstEstimator.BURST_GAP_MS + 1
            estimator.onHttpActivity() // >10s of silence: new burst
            now += 500
            estimator.onHttpActivity() // same burst

            val counters = accountant.allDaysIncludingLive()[1L].orEmpty()
            assertEquals(2L, counters[UsageKeys.radioBursts(mobile = true, foreground = false)])
        }
}

class LoopbackExclusionTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** Runs [UsageCountingInterceptor] over a chain whose downstream sent [sentUrl] for [url]. */
    private fun countedRequests(
        url: String,
        sentUrl: String,
    ): Long? {
        val accountant = ResourceUsageAccountant(ResourceUsageStore(File(temp.root, "u.json")), CoroutineScope(Dispatchers.Unconfined), epochDay = { 1L })
        val interceptor = UsageCountingInterceptor(accountant, isMobile = { true }, isForeground = { true }, nowMs = { 0L })

        val request = Request.Builder().url(url).build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            Response
                .Builder()
                .request(request.newBuilder().url(sentUrl).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }
        interceptor.intercept(chain).close()

        return runBlocking { accountant.allDaysIncludingLive() }[1L].orEmpty()[UsageKeys.netReqs(UsageKeys.ROLE_OTHER, mobile = true, foreground = true)]
    }

    @Test
    fun requestRewrittenToTheLocalCacheIsNotCounted() {
        // This interceptor is outermost: it sees the CDN URL, but the request that actually went
        // out (LocalBlossomCacheRedirectInterceptor's rewrite) never touched the radio.
        val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"
        assertNull(countedRequests("https://cdn.example.com/$sha.jpg", "http://127.0.0.1:24242/$sha.jpg"))
    }

    @Test
    fun requestToARealHostIsCounted() {
        assertEquals(1L, countedRequests("https://cdn.example.com/a.jpg", "https://cdn.example.com/a.jpg"))
    }

    @Test
    fun loopbackHostsAreRecognizedAndRealHostsAreNot() {
        assertTrue(UsageCountingInterceptor.isLoopback("127.0.0.1"))
        assertTrue(UsageCountingInterceptor.isLoopback("127.4.5.6"))
        assertTrue(UsageCountingInterceptor.isLoopback("localhost"))
        assertTrue(UsageCountingInterceptor.isLoopback("::1"))
        assertFalse(UsageCountingInterceptor.isLoopback("relay.example.com"))
        assertFalse(UsageCountingInterceptor.isLoopback("192.168.1.10"))
        assertFalse(UsageCountingInterceptor.isLoopback("128.0.0.1"))
    }
}

class MediaPlayTimeTrackerTest {
    @Test
    fun accumulatesOnlyWhilePlaying() {
        var now = 0L
        var played = 0L
        val tracker = MediaPlayTimeTracker(onPlayed = { played += it }, nowMs = { now })

        tracker.onIsPlayingChanged(true)
        now = 30_000L
        tracker.onIsPlayingChanged(false)

        now = 100_000L // paused time must not count
        tracker.onIsPlayingChanged(true)
        now = 105_000L
        tracker.onIsPlayingChanged(false)
        tracker.onIsPlayingChanged(false) // duplicate stop is a no-op

        assertEquals(35_000L, played)
    }
}

class SessionTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun accountsActiveTimeAndCountsActivations() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val session = SessionTimeIntegrator(accountant, "s.ms", "s.starts", nowMs = { clock })

            session.setActive(true)
            clock += 5_000
            session.setActive(false)
            clock += 60_000 // idle time must not count
            session.setActive(true)
            clock += 1_000
            session.setActive(false)

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(6_000L, today["s.ms"])
            assertEquals(2L, today["s.starts"])
        }

    @Test
    fun repeatedActivationsDoNotDoubleCountStarts() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val session = SessionTimeIntegrator(accountant, "s.ms", "s.starts", nowMs = { clock })

            session.setActive(true)
            clock += 1_000
            session.setActive(true)
            clock += 1_000
            session.setActive(false)

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(2_000L, today["s.ms"])
            assertEquals(1L, today["s.starts"])
        }

    @Test
    fun openSegmentIsAccountedOnFlushWithoutClosing() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val session = SessionTimeIntegrator(accountant, "s.ms", nowMs = { clock })
            session.registerFlushHook()

            session.setActive(true)
            clock += 120_000
            val mid = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals("multi-hour stable sessions account without a transition", 120_000L, mid["s.ms"])

            clock += 30_000
            session.setActive(false)
            val end = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(150_000L, end["s.ms"])
        }
}

class BatteryDrainSamplerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun harness(scope: CoroutineScope): Triple<ResourceUsageAccountant, BatteryDrainSampler, Controls> {
        val store = ResourceUsageStore(File(temp.root, "u.json"))
        val accountant = ResourceUsageAccountant(store, scope, epochDay = { 100L })
        val controls = Controls()
        val sampler =
            BatteryDrainSampler(
                accountant = accountant,
                capacityPct = { controls.pct },
                isCharging = { controls.charging },
                isForeground = { controls.foreground },
            )
        return Triple(accountant, sampler, controls)
    }

    private class Controls {
        var pct: Int? = 90
        var charging = false
        var foreground = true
    }

    @Test
    fun firstSampleOnlyEstablishesTheBaseline() =
        runTest {
            val (accountant, sampler, _) = harness(backgroundScope)
            sampler.sample()
            assertNull(accountant.allDaysIncludingLive()[100]?.get(UsageKeys.BATTERY_DRAIN_FG))
        }

    @Test
    fun dischargeDropsAreAccountedByVisibility() =
        runTest {
            val (accountant, sampler, controls) = harness(backgroundScope)
            sampler.sample() // baseline 90, discharging
            controls.pct = 87
            sampler.sample() // -3 while foreground
            controls.foreground = false
            controls.pct = 85
            sampler.sample() // -2 while background

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(3L, today[UsageKeys.BATTERY_DRAIN_FG])
            assertEquals(2L, today[UsageKeys.BATTERY_DRAIN_BG])
        }

    @Test
    fun intervalsTouchingAChargerAreSkipped() =
        runTest {
            val (accountant, sampler, controls) = harness(backgroundScope)
            sampler.sample() // baseline 90, discharging
            controls.charging = true
            controls.pct = 80 // weird drop while charging: ignore
            sampler.sample()
            controls.charging = false
            controls.pct = 78 // first discharging interval after charging: baseline only
            sampler.sample()
            controls.pct = 77
            sampler.sample() // clean discharging interval: -1

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(1L, today[UsageKeys.BATTERY_DRAIN_FG])
        }

    @Test
    fun aLevelIncreaseResetsTheBaselineWithoutAccounting() =
        runTest {
            val (accountant, sampler, controls) = harness(backgroundScope)
            sampler.sample() // baseline 90
            controls.pct = 95 // e.g. charged while the process was frozen
            sampler.sample()
            controls.pct = 94
            sampler.sample() // -1

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(1L, today[UsageKeys.BATTERY_DRAIN_FG])
        }
}

class MeteringNostrSignerTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** Pure-Kotlin fake so the test never touches secp256k1 JNI. */
    private class FakeSigner : NostrSigner("aa".repeat(32)) {
        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = throw UnsupportedOperationException("fake")

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = "enc04"

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = "dec04"

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = "enc44"

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = "dec44"

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = throw UnsupportedOperationException("fake")

        override suspend fun deriveKey(nonce: HexKey): HexKey = "bb".repeat(32)

        override suspend fun signPsbt(psbtHex: String): String = psbtHex

        override fun hasForegroundSupport() = true
    }

    @Test
    fun countsSignsAndCryptoOpsWithoutChangingResults() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            val metered = MeteringNostrSigner(FakeSigner(), accountant)

            assertEquals("dec44", metered.nip44Decrypt("x", "aa".repeat(32)))
            assertEquals("dec04", metered.nip04Decrypt("x", "aa".repeat(32)))
            assertEquals("enc44", metered.nip44Encrypt("x", "aa".repeat(32)))
            runCatching { metered.sign<Event>(0L, 1, arrayOf(), "hello") }

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(2L, today[UsageKeys.DECRYPT_COUNT])
            assertEquals(1L, today[UsageKeys.ENCRYPT_COUNT])
            assertEquals(1L, today[UsageKeys.signs(UsageKeys.SIGNER_LOCAL)])
            assertNull("non-local signers must not pollute crypto CPU time", today[UsageKeys.DECRYPT_US])
        }

    @Test
    fun innermostSignerUnwrapsTheMeteringDecorator() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            val raw = FakeSigner()
            val metered = MeteringNostrSigner(raw, accountant)
            assertEquals(raw, metered.innermostSigner())
        }
}

class ScreenTimeIntegratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun routeNamesLoseTheirArgumentsBeforeAnythingIsRecorded() {
        assertEquals("Profile", ScreenTimeIntegrator.screenNameOf("com.vitorpamplona.amethyst.commons.model.navigation.Route.Profile/{userId}"))
        assertEquals("Hashtag", ScreenTimeIntegrator.screenNameOf("routes.Route.Hashtag/{tag}?extra={extra}"))
        assertEquals("Home", ScreenTimeIntegrator.screenNameOf("routes.Route.Home"))
        assertNull(ScreenTimeIntegrator.screenNameOf(null))
        assertNull(ScreenTimeIntegrator.screenNameOf(""))
    }

    @Test
    fun accountsScreenTimeOnlyWhileForeground() =
        runTest {
            val store = ResourceUsageStore(File(temp.root, "u.json"))
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 100L })
            var clock = 0L
            val isForeground = MutableStateFlow(true)
            val integrator = ScreenTimeIntegrator(accountant, nowMs = { clock })
            integrator.start(backgroundScope, isForeground)
            testScheduler.runCurrent()

            integrator.onScreen("Home")
            testScheduler.runCurrent()
            clock += 5_000
            integrator.onScreen("Video")
            testScheduler.runCurrent()
            clock += 3_000
            isForeground.value = false // backgrounded on Video: segment closes
            testScheduler.runCurrent()
            clock += 60_000 // background time must not count
            isForeground.value = true
            testScheduler.runCurrent()
            clock += 2_000
            integrator.onScreen(null)
            testScheduler.runCurrent()

            val today = accountant.allDaysIncludingLive()[100].orEmpty()
            assertEquals(5_000L, today[UsageKeys.screenMs("Home")])
            assertEquals(5_000L, today[UsageKeys.screenMs("Video")])
        }
}

class UsageSummaryMapsTest {
    @Test
    fun screenTimeAndCellularMapsAreExtractedFromCounters() {
        val s =
            UsageSummary.from(
                mapOf(
                    UsageKeys.screenMs("Home") to 10_000L,
                    UsageKeys.screenMs("Video") to 5_000L,
                    UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = true, foreground = true, received = true) to 100L,
                    UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = false, foreground = true, received = true) to 900L,
                ),
            )
        assertEquals(10_000L, s.screenTimeMs["Home"])
        assertEquals(5_000L, s.screenTimeMs["Video"])
        assertEquals(100L, s.mobileBytesPerSubsystem[UsageKeys.ROLE_IMAGE])
        assertEquals(1_000L, s.bytesPerSubsystem[UsageKeys.ROLE_IMAGE])
    }
}

class ResourceUsageAlertsTest {
    private fun day(vararg counters: Pair<String, Long>) = mapOf(*counters)

    @Test
    fun quietUsageDoesNotAlert() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = true, foreground = false, received = true) to 1024L * 1024L,
                        UsageKeys.relayConnMs(mobile = true, foreground = false) to 60L * 60L * 1000L,
                    ),
            )
        assertNull(ResourceUsageAlerts.evaluate(days, today = 10L))
    }

    @Test
    fun backgroundMobileDataCrossingThresholdAlerts() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.net(UsageKeys.ROLE_VIDEO, mobile = true, foreground = false, received = true) to
                            ResourceUsageAlerts.BG_MOBILE_BYTES_PER_DAY + 1,
                    ),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.BACKGROUND_MOBILE_DATA, alert?.reason)
        assertEquals(9L, alert?.day)
    }

    @Test
    fun foregroundMobileDataDoesNotTripTheBackgroundThreshold() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.net(UsageKeys.ROLE_VIDEO, mobile = true, foreground = true, received = true) to
                            ResourceUsageAlerts.BG_MOBILE_BYTES_PER_DAY * 10,
                    ),
            )
        assertNull(ResourceUsageAlerts.evaluate(days, today = 10L))
    }

    @Test
    fun connectionTimeCounterDoesNotLeakIntoByteThreshold() {
        // relay.connms keys carry mobile+bg dims but are milliseconds, not
        // bytes: they must never count toward the data threshold.
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.relayConnMs(mobile = true, foreground = false) to
                            ResourceUsageAlerts.BG_MOBILE_BYTES_PER_DAY * 100,
                    ),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.BACKGROUND_MOBILE_CONNECTION_TIME, alert?.reason)
    }

    @Test
    fun todayIsCheckedWhenYesterdayIsQuiet() {
        val days =
            mapOf(
                10L to day(UsageKeys.APP_STARTS to ResourceUsageAlerts.APP_STARTS_PER_DAY + 1),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.PROCESS_CHURN, alert?.reason)
    }

    @Test
    fun reconnectChurnAlerts() {
        val days =
            mapOf(
                9L to
                    day(
                        UsageKeys.relayConnects(mobile = true, foreground = false) to 3_000L,
                        UsageKeys.relayConnects(mobile = false, foreground = true) to
                            ResourceUsageAlerts.RELAY_CONNECTS_PER_DAY - 2_000L,
                    ),
            )
        val alert = ResourceUsageAlerts.evaluate(days, today = 10L)
        assertEquals(ResourceUsageAlerts.Reason.RECONNECT_CHURN, alert?.reason)
    }

    @Test
    fun promptRateLimiting() {
        val now = 1_000_000L
        val week = ResourceUsageAlerts.MIN_DAYS_BETWEEN_PROMPTS * 24 * 60 * 60
        assertTrue(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = 0, optOut = false, nowSec = now))
        assertFalse(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = now - week + 10, optOut = false, nowSec = now))
        assertTrue(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = now - week - 10, optOut = false, nowSec = now))
        assertFalse(ResourceUsageAlerts.shouldPrompt(lastAlertAtSec = 0, optOut = true, nowSec = now))
    }
}

/**
 * The guard that keeps the counter-key grammar honest — see the reserved-segment
 * note on [UsageKeys] for the mechanism and what it would cost to get wrong.
 */
class UsageKeyGrammarTest {
    /** Every diagnostic key shape, with values that would be obvious if they leaked into a sum. */
    private fun churnKeys(): Map<String, Long> {
        val out = mutableMapOf<String, Long>()
        var n = 1_000_000L
        for (mobile in listOf(true, false)) {
            for (fg in listOf(true, false)) {
                out[UsageKeys.relayDials(mobile, fg)] = n++
                out[UsageKeys.relayDisconnects(mobile, fg)] = n++
                for (ms in listOf(1L, 10_000L, 45_000L, 90_000L, 200_000L, 900_000L)) {
                    out[UsageKeys.relayLife(ms, mobile, fg)] = n++
                }
                for (verb in CMD_LABELS) {
                    out[UsageKeys.relayVerb(verb, received = false, mobile, fg)] = n++
                }
                for (verb in MSG_LABELS) {
                    out[UsageKeys.relayVerb(verb, received = true, mobile, fg)] = n++
                }
            }
        }
        out[UsageKeys.RELAY_LIFE_OVERWRITE] = n++
        out[UsageKeys.RELAY_LIFE_ORPHAN] = n++
        for (cause in listOf(
            UsageKeys.TRIGGER_NETID,
            UsageKeys.TRIGGER_TRANSPORT,
            UsageKeys.TRIGGER_TOR_POLICY,
            UsageKeys.TRIGGER_CLASSIFICATION,
            UsageKeys.TRIGGER_COLD_START,
            UsageKeys.TRIGGER_OFF,
            UsageKeys.TRIGGER_BUZZ,
        )) {
            out[UsageKeys.relayTrigger(cause)] = n++
        }
        for (mobile in listOf(true, false)) {
            for (fg in listOf(true, false)) {
                out[UsageKeys.relaySubsSent(mobile, fg)] = n++
                out[UsageKeys.relaySubsClosed(mobile, fg)] = n++
                out[UsageKeys.relaySubsReplay(mobile, fg)] = n++
            }
        }
        UsageKeys.NOTICE_REASONS.forEach { out[UsageKeys.relayNotice(it)] = n++ }
        for (mobile in listOf(true, false)) {
            for (fg in listOf(true, false)) {
                out[UsageKeys.relaySubsResent(mobile, fg)] = n++
                out[UsageKeys.relayEventsSeen(mobile, fg)] = n++
                out[UsageKeys.relayEventsDup(mobile, fg)] = n++
                out[UsageKeys.relayEventsDupBytes(mobile, fg)] = n++
                for (ms in listOf(0L, 200L, 1_000L, 5_000L, 20_000L, 60_000L)) {
                    out[UsageKeys.relayHandshake(ms, mobile, fg)] = n++
                    out[UsageKeys.relayDialGap(ms, mobile, fg)] = n++
                }
            }
        }
        (SubPurpose.entries.map { UsageKeys.purposeKeyPart(it) } + UsageKeys.PURPOSE_UNEXPLAINED + UsageKeys.PURPOSE_UNATTRIBUTED).forEach {
            out[UsageKeys.relayPurposeSent(it)] = n++
            out[UsageKeys.relayPurposeBytes(it)] = n++
            out[UsageKeys.relayPurposeDown(it)] = n++
            out[UsageKeys.relayPurposeDownCount(it)] = n++
            out[UsageKeys.relayPurposeDupBytes(it)] = n++
        }
        return out
    }

    /** A baseline of the pre-existing counters the summary is actually built from. */
    private fun baseline(): Map<String, Long> =
        mapOf(
            UsageKeys.relayMsg(mobile = true, foreground = false, received = true) to 500L,
            UsageKeys.relayMsg(mobile = true, foreground = false, received = false) to 60L,
            UsageKeys.relayMsg(mobile = false, foreground = true, received = true) to 900L,
            UsageKeys.net(UsageKeys.ROLE_IMAGE, mobile = true, foreground = true, received = true) to 70L,
            UsageKeys.netReqs(UsageKeys.ROLE_IMAGE, mobile = true, foreground = true) to 3L,
            UsageKeys.netActiveMs(UsageKeys.ROLE_IMAGE, mobile = true, foreground = true) to 40L,
            UsageKeys.radioBursts(mobile = true, foreground = true) to 2L,
            UsageKeys.relayConnMs(mobile = true, foreground = false) to 1_234L,
            UsageKeys.relayConnects(mobile = true, foreground = false) to 11L,
            UsageKeys.relayConnectFails(mobile = true, foreground = false) to 22L,
            UsageKeys.workerRuns("calendarReminder") to 1L,
        )

    @Test
    fun newKeysDoNotDisturbSummary() {
        val before = UsageSummary.from(baseline())
        val after = UsageSummary.from(baseline() + churnKeys())
        assertEquals(before, after)
    }

    @Test
    fun noNewKeyContainsAReservedSegment() {
        val reserved =
            setOf(
                UsageKeys.RX,
                UsageKeys.TX,
                "msg",
                "connms",
                "connects",
                "connfails",
                "reqs",
                "bursts",
                "activems",
                "worker",
                "runs",
            ) + UsageKeys.HTTP_ROLES

        churnKeys().keys.forEach { key ->
            val clash = key.split('.').filter { it in reserved }
            assertTrue("Key '$key' uses reserved segment(s) $clash", clash.isEmpty())
        }
    }

    companion object {
        /**
         * The verb segments taken straight from quartz's own wire labels — the same
         * source [RelayUsageListener] reads, so a new subtype cannot be tested against
         * a stale hand-written list.
         */
        val CMD_LABELS = listOf(ReqCmd.LABEL, EventCmd.LABEL, AuthCmd.LABEL, CloseCmd.LABEL, CountCmd.LABEL)
        val MSG_LABELS =
            listOf(
                EventMessage.LABEL,
                EoseMessage.LABEL,
                OkMessage.LABEL,
                NoticeMessage.LABEL,
                AuthMessage.LABEL,
                ClosedMessage.LABEL,
                CountMessage.LABEL,
                NotifyMessage.LABEL,
                LimitsMessage.LABEL,
            )
    }
}

class UsageKeyHelpersTest {
    @Test
    fun lifeBucketsAreHalfOpen() {
        assertEquals("lt5s", UsageKeys.lifeBucket(0))
        assertEquals("lt5s", UsageKeys.lifeBucket(4_999))
        assertEquals("lt30s", UsageKeys.lifeBucket(5_000))
        assertEquals("lt60s", UsageKeys.lifeBucket(59_999))
        // The one that matters: exactly the stability bar is NOT "under a minute".
        assertEquals("lt120s", UsageKeys.lifeBucket(60_000))
        assertEquals("lt300s", UsageKeys.lifeBucket(299_999))
        assertEquals("gte300s", UsageKeys.lifeBucket(300_000))
        assertEquals("gte300s", UsageKeys.lifeBucket(Long.MAX_VALUE))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RelayUsageListenerTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 0L

    private fun relay(url: String): IRelayClient {
        val r = mockk<IRelayClient>(relaxed = true)
        every { r.url } returns NormalizedRelayUrl(url)
        return r
    }

    private fun runLedger(block: suspend (ResourceUsageAccountant, RelayUsageListener) -> Unit) =
        runTest {
            val store = ResourceUsageStore(File(tmp.newFolder(), "usage.json"))
            // backgroundScope, as everywhere else in this file: the accountant's
            // debounced flush is auto-cancelled with the test instead of leaking a
            // pending 30s delay into the test body.
            val accountant = ResourceUsageAccountant(store, backgroundScope, epochDay = { 1L })
            val l =
                RelayUsageListener(
                    accountant = accountant,
                    isMobile = { false },
                    isForeground = { true },
                    nowMs = { now },
                )
            block(accountant, l)
        }

    private suspend fun counters(accountant: ResourceUsageAccountant): Map<String, Long> = accountant.allDaysIncludingLive()[1L].orEmpty()

    @Test
    fun bucketsASessionByHowLongItLived() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            now = 1_000
            l.onConnected(r, 10, false)
            now = 1_000 + 45_000
            l.onDisconnected(r)

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.relayLife(45_000, mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relayConnects(mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relayDisconnects(mobile = false, foreground = true)])
            assertNull(c[UsageKeys.RELAY_LIFE_ORPHAN])
            assertNull(c[UsageKeys.RELAY_LIFE_OVERWRITE])
        }

    @Test
    fun aDialThatNeverConnectedProducesNoLifetime() =
        runLedger { accountant, l ->
            val r = relay("wss://nos.lol/")
            l.onConnecting(r)
            l.onCannotConnect(r, "WebSocket Failure: timeout (SocketTimeoutException)")
            l.onDisconnected(r)

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.relayDials(mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relayConnectFails(mobile = false, foreground = true)])
            // No start stamp to consume: counted as an orphan rather than a 0ms session,
            // which would otherwise pile into lt5s and fake "instant failures".
            assertEquals(1L, c[UsageKeys.RELAY_LIFE_ORPHAN])
            assertNull(c[UsageKeys.relayLife(0, mobile = false, foreground = true)])
            assertNull(c[UsageKeys.relayConnects(mobile = false, foreground = true)])
        }

    @Test
    fun aSecondConnectBeforeDisconnectIsCountedAsAnOverwrite() =
        runLedger { accountant, l ->
            // The teardown race: disconnect(); connect() runs synchronously, so a stale
            // failure callback for the old socket can land after the new one is open.
            val r = relay("wss://relay.damus.io/")
            now = 0
            l.onConnected(r, 10, false)
            now = 500_000
            l.onConnected(r, 10, false)
            now = 500_100
            l.onDisconnected(r)

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.RELAY_LIFE_OVERWRITE])
            assertEquals(2L, c[UsageKeys.relayConnects(mobile = false, foreground = true)])
            // The long session was lost; only the short one is recorded. `connects` is the
            // denominator that makes that deficit visible instead of silent.
            assertEquals(1L, c[UsageKeys.relayLife(100, mobile = false, foreground = true)])
            assertNull(c[UsageKeys.relayLife(500_000, mobile = false, foreground = true)])
        }

    @Test
    fun twoRelaysDoNotShareASlot() =
        runLedger { accountant, l ->
            val a = relay("wss://relay.damus.io/")
            val b = relay("wss://nos.lol/")
            now = 0
            l.onConnected(a, 10, false)
            l.onConnected(b, 10, false)
            now = 90_000
            l.onDisconnected(a)
            now = 200_000
            l.onDisconnected(b)

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.relayLife(90_000, mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relayLife(200_000, mobile = false, foreground = true)])
            assertNull(c[UsageKeys.RELAY_LIFE_OVERWRITE])
        }

    @Test
    fun sentVerbSplitSumsBackToTheByteTotal() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            val req = ReqCmd("sub1", listOf())
            val event = EventCmd(mockk(relaxed = true))
            l.onSent(r, "0123456789", req, success = true)
            l.onSent(r, "01234", event, success = true)
            // A failed send is not counted at all, matching relay.msg.*.tx.
            l.onSent(r, "0123456789012345", req, success = false)

            val c = counters(accountant)
            val total = c[UsageKeys.relayMsg(mobile = false, foreground = true, received = false)]
            val split =
                c.filterKeys { it.startsWith("relay.verb.up.") }.values.sum()
            assertEquals(15L, total)
            assertEquals(total, split)
            assertEquals(10L, c[UsageKeys.relayVerb(ReqCmd.LABEL, received = false, mobile = false, foreground = true)])
            assertEquals(5L, c[UsageKeys.relayVerb(EventCmd.LABEL, received = false, mobile = false, foreground = true)])
            // The label is uppercase on the wire; the key segment is not.
            assertEquals("relay.verb.up.req.wifi.fg", UsageKeys.relayVerb(ReqCmd.LABEL, received = false, mobile = false, foreground = true))
        }

    @Test
    fun receivedVerbSplitSumsBackToTheByteTotal() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onIncomingMessage(r, "0123456789", EoseMessage("sub1"))
            l.onIncomingMessage(r, "012", NoticeMessage("hi"))

            val c = counters(accountant)
            val total = c[UsageKeys.relayMsg(mobile = false, foreground = true, received = true)]
            val split = c.filterKeys { it.startsWith("relay.verb.down.") }.values.sum()
            assertEquals(13L, total)
            assertEquals(total, split)
        }

    @Test
    fun reqsInsideTheConnectWindowCountAsReplay() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            now = 10_000
            l.onConnected(r, 10, false)
            // syncState's burst: sent immediately after the socket is ready.
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf()), success = true)
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub2", listOf()), success = true)
            // Well past the window — a user opening a screen, not a replay.
            now = 10_000 + UsageKeys.REPLAY_WINDOW_MS + 1
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub3", listOf()), success = true)
            l.onSent(r, "[\"CLOSE\"]", CloseCmd("sub1"), success = true)

            val c = counters(accountant)
            assertEquals(3L, c[UsageKeys.relaySubsSent(mobile = false, foreground = true)])
            assertEquals(2L, c[UsageKeys.relaySubsReplay(mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relaySubsClosed(mobile = false, foreground = true)])
        }

    @Test
    fun aReqOnANeverConnectedRelayIsNotReplay() =
        runLedger { accountant, l ->
            // No onConnected, so no start stamp: must not be attributed to a burst.
            val r = relay("wss://nos.lol/")
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf()), success = true)

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.relaySubsSent(mobile = false, foreground = true)])
            assertNull(c[UsageKeys.relaySubsReplay(mobile = false, foreground = true)])
        }

    @Test
    fun aFailedSendCountsNowhere() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf()), success = false)

            val c = counters(accountant)
            assertNull(c[UsageKeys.relaySubsSent(mobile = false, foreground = true)])
        }

    @Test
    fun aRefusalNoticeIsCountedByReason() =
        runLedger { accountant, l ->
            val r = relay("wss://nos.lol/")
            l.onIncomingMessage(r, "[\"NOTICE\",\"x\"]", NoticeMessage("ERROR: too many concurrent REQs"))
            l.onIncomingMessage(r, "[\"NOTICE\",\"y\"]", NoticeMessage("hello"))

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.relayNotice(UsageKeys.NOTICE_TOO_MANY_SUBS)])
            assertEquals(1L, c[UsageKeys.relayNotice(UsageKeys.NOTICE_UNCLASSIFIED)])
            // Still part of the byte total, so the verb invariant is unaffected.
            assertEquals(
                c[UsageKeys.relayMsg(mobile = false, foreground = true, received = true)],
                c.filterKeys { it.startsWith("relay.verb.down.") }.values.sum(),
            )
        }

    @Test
    fun aReqForAnAlreadyOpenSubscriptionCountsAsAResend() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf(Filter())), success = true)
            // Same subId, still open: the assembler changed its mind.
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf(Filter())), success = true)
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub2", listOf(Filter())), success = true)

            val c = counters(accountant)
            assertEquals(3L, c[UsageKeys.relaySubsSent(mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relaySubsResent(mobile = false, foreground = true)])
        }

    @Test
    fun aClosedSubscriptionCanBeReopenedWithoutCountingAsAResend() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf(Filter())), success = true)
            l.onSent(r, "[\"CLOSE\"]", CloseCmd("sub1"), success = true)
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf(Filter())), success = true)

            assertNull(counters(accountant)[UsageKeys.relaySubsResent(mobile = false, foreground = true)])
        }

    @Test
    fun aReconnectForgetsOpenSubscriptions() =
        runLedger { accountant, l ->
            // The relay forgets them too, so the post-reconnect replay is legitimately
            // new work and must not be booked as the client changing its mind.
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf(Filter())), success = true)
            l.onDisconnected(r)
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("sub1", listOf(Filter())), success = true)

            assertNull(counters(accountant)[UsageKeys.relaySubsResent(mobile = false, foreground = true)])
        }

    @Test
    fun reqsAreAttributedToTheirSubscriptionPurpose() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "0123456789", ReqCmd("a", listOf(ExplainedFilter(purpose = SubPurpose.HOME_FEED))), success = true)
            l.onSent(r, "01234", ReqCmd("b", listOf(ExplainedFilter(purpose = SubPurpose.OTHER))), success = true)
            // An assembler #3832 never tagged: its own bucket, not a guess.
            l.onSent(r, "012", ReqCmd("c", listOf(Filter())), success = true)

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.relayPurposeSent("home_feed")])
            assertEquals(10L, c[UsageKeys.relayPurposeBytes("home_feed")])
            assertEquals(1L, c[UsageKeys.relayPurposeSent(UsageKeys.PURPOSE_OTHER)])
            assertEquals(1L, c[UsageKeys.relayPurposeSent(UsageKeys.PURPOSE_UNEXPLAINED)])
            // Purpose bytes reconcile with the REQ verb total.
            assertEquals(
                c[UsageKeys.relayVerb("REQ", received = false, mobile = false, foreground = true)],
                c.filterKeys { it.startsWith("relay.purpose.") && it.endsWith(".bytes") }.values.sum(),
            )
        }

    @Test
    fun handshakeAndDialGapSeparateTheRelayFromOurselves() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            now = 0
            l.onConnecting(r)
            // 3s of wall clock to get connected, of which the transport says the
            // upgrade round trip was 150ms — the other 2850ms is DNS/TCP/TLS/queueing.
            now = 3_000
            l.onConnected(r, pingMillis = 150, compressed = false)

            val c = counters(accountant)
            assertEquals(1L, c[UsageKeys.relayHandshake(150, mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relayDialGap(2_850, mobile = false, foreground = true)])
        }

    @Test
    fun anUntimeableHandshakeRecordsNothingRatherThanZero() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnecting(r)
            l.onConnected(r, pingMillis = 0, compressed = false)

            val c = counters(accountant)
            assertNull(c[UsageKeys.relayHandshake(0, mobile = false, foreground = true)])
            assertNull(c[UsageKeys.relayDialGap(0, mobile = false, foreground = true)])
            // The connection itself is still counted.
            assertEquals(1L, c[UsageKeys.relayConnects(mobile = false, foreground = true)])
        }

    @Test
    fun inboundBytesAreAttributedToTheSubscriptionThatAskedForThem() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("mod1", listOf(ExplainedFilter(purpose = SubPurpose.MODERATION))), success = true)
            l.onSent(r, "[\"REQ\"]", ReqCmd("feed1", listOf(ExplainedFilter(purpose = SubPurpose.HOME_FEED))), success = true)

            l.onIncomingMessage(r, "0123456789", EventMessage("mod1", mockk(relaxed = true)))
            l.onIncomingMessage(r, "01234", EventMessage("feed1", mockk(relaxed = true)))
            l.onIncomingMessage(r, "012", EoseMessage("mod1"))

            val c = counters(accountant)
            assertEquals(13L, c[UsageKeys.relayPurposeDown("moderation")])
            assertEquals(5L, c[UsageKeys.relayPurposeDown("home_feed")])
            // Frames, not just bytes: 13 bytes of moderation arrived as two frames, so
            // the average frame size is recoverable per purpose.
            assertEquals(2L, c[UsageKeys.relayPurposeDownCount("moderation")])
            assertEquals(1L, c[UsageKeys.relayPurposeDownCount("home_feed")])
        }

    @Test
    fun framesStillInFlightAfterACloseAreStillAttributed() =
        runLedger { accountant, l ->
            // The bug this replaced: CLOSE removed the id, so events the relay had
            // already queued landed in `unattributed`. 8,159 CLOSEs in one session
            // sent 41% of the download there.
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("s1", listOf(ExplainedFilter(purpose = SubPurpose.HOME_FEED))), success = true)
            l.onSent(r, "[\"CLOSE\"]", CloseCmd("s1"), success = true)
            l.onIncomingMessage(r, "0123456789", EventMessage("s1", mockk(relaxed = true)))

            assertEquals(10L, counters(accountant)[UsageKeys.relayPurposeDown("home_feed")])
        }

    @Test
    fun aFrameArrivingAfterAReconnectIsStillAttributed() =
        runLedger { accountant, l ->
            // Purpose is a property of the subscription id, not of the socket, so a
            // disconnect must not forget it.
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("s1", listOf(ExplainedFilter(purpose = SubPurpose.ENGAGEMENT))), success = true)
            l.onDisconnected(r)
            l.onConnected(r, 10, false)
            l.onIncomingMessage(r, "01234", EventMessage("s1", mockk(relaxed = true)))

            assertEquals(5L, counters(accountant)[UsageKeys.relayPurposeDown("engagement")])
        }

    @Test
    fun aReconnectStillForgetsWhichSubscriptionsAreOpen() =
        runLedger { accountant, l ->
            // The other half of the split: the relay forgot them, so the replay is
            // new work and must not read as the client changing its mind.
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("s1", listOf(ExplainedFilter(purpose = SubPurpose.HOME_FEED))), success = true)
            l.onDisconnected(r)
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("s1", listOf(ExplainedFilter(purpose = SubPurpose.HOME_FEED))), success = true)

            assertNull(counters(accountant)[UsageKeys.relaySubsResent(mobile = false, foreground = true)])
        }

    @Test
    fun anInboundFrameForAnUnknownSubscriptionIsNotGuessedAt() =
        runLedger { accountant, l ->
            // Counters wiped mid-session, or a subscription opened before this
            // connection: attributing it to a purpose would be an invention.
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onIncomingMessage(r, "0123456789", EventMessage("ghost", mockk(relaxed = true)))

            val c = counters(accountant)
            assertEquals(10L, c[UsageKeys.relayPurposeDown(UsageKeys.PURPOSE_UNATTRIBUTED)])
            assertEquals(1L, c[UsageKeys.relayPurposeDownCount(UsageKeys.PURPOSE_UNATTRIBUTED)])
        }

    @Test
    fun relayWideFramesAreLeftOutRatherThanMisattributed() =
        runLedger { accountant, l ->
            // NOTICE and OK name no subscription, so nothing may be booked for them.
            // This is why purpose.down deliberately does not reconcile to msg.rx.
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onIncomingMessage(r, "0123456789", NoticeMessage("hello"))
            l.onIncomingMessage(r, "01234", OkMessage("id", true, ""))

            val c = counters(accountant)
            assertTrue(c.keys.none { it.startsWith("relay.purpose.") && it.endsWith(".down") })
            // Still counted in the byte totals and the verb split.
            assertEquals(15L, c[UsageKeys.relayMsg(mobile = false, foreground = true, received = true)])
        }

    private fun eventWithId(id: String): EventMessage {
        val ev = mockk<Event>(relaxed = true)
        every { ev.id } returns id
        val msg = mockk<EventMessage>(relaxed = true)
        every { msg.subId } returns "s1"
        every { msg.event } returns ev
        every { msg.label() } returns EventMessage.LABEL
        return msg
    }

    @Test
    fun theSameEventFromTwoRelaysIsCountedOnceAsNewAndOnceAsDuplicate() =
        runLedger { accountant, l ->
            // The outbox fan-out asks many relays for the same authors, so one event
            // arrives once per relay carrying it. That repetition is the measurement.
            val a = relay("wss://relay.damus.io/")
            val b = relay("wss://nos.lol/")
            l.onConnected(a, 10, false)
            l.onConnected(b, 10, false)
            val id = "a".repeat(64)
            l.onIncomingMessage(a, "0123456789", eventWithId(id))
            l.onIncomingMessage(b, "0123456789", eventWithId(id))

            val c = counters(accountant)
            assertEquals(2L, c[UsageKeys.relayEventsSeen(mobile = false, foreground = true)])
            assertEquals(1L, c[UsageKeys.relayEventsDup(mobile = false, foreground = true)])
            assertEquals(10L, c[UsageKeys.relayEventsDupBytes(mobile = false, foreground = true)])
        }

    @Test
    fun duplicateBytesAreAttributedToThePurposeThatReceivedThem() =
        runLedger { accountant, l ->
            // Global duplication says how much is wasted; this says where, which is
            // what separates "suppress redundant delivery" from "stop fetching it".
            val a = relay("wss://relay.damus.io/")
            val b = relay("wss://nos.lol/")
            l.onConnected(a, 10, false)
            l.onConnected(b, 10, false)
            l.onSent(a, "[\"REQ\"]", ReqCmd("s1", listOf(ExplainedFilter(purpose = SubPurpose.USER_PROFILE))), success = true)
            l.onSent(b, "[\"REQ\"]", ReqCmd("s1", listOf(ExplainedFilter(purpose = SubPurpose.USER_PROFILE))), success = true)

            val id = "b".repeat(64)
            l.onIncomingMessage(a, "0123456789", eventWithId(id))
            l.onIncomingMessage(b, "0123456789", eventWithId(id))

            val c = counters(accountant)
            assertEquals(20L, c[UsageKeys.relayPurposeDown("user_profile")])
            // Only the second copy is waste.
            assertEquals(10L, c[UsageKeys.relayPurposeDupBytes("user_profile")])
            assertEquals(10L, c[UsageKeys.relayEventsDupBytes(mobile = false, foreground = true)])
        }

    @Test
    fun aFirstDeliveryIsNeverBookedAsDuplicate() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onSent(r, "[\"REQ\"]", ReqCmd("s1", listOf(ExplainedFilter(purpose = SubPurpose.HOME_FEED))), success = true)
            l.onIncomingMessage(r, "0123456789", eventWithId("c".repeat(64)))

            val c = counters(accountant)
            assertEquals(10L, c[UsageKeys.relayPurposeDown("home_feed")])
            assertNull(c[UsageKeys.relayPurposeDupBytes("home_feed")])
        }

    @Test
    fun distinctEventsAreNotDuplicates() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            // Differ only past the 64-bit prefix would be a collision; differ within it.
            l.onIncomingMessage(r, "01234", eventWithId("1".repeat(64)))
            l.onIncomingMessage(r, "01234", eventWithId("2".repeat(64)))

            val c = counters(accountant)
            assertEquals(2L, c[UsageKeys.relayEventsSeen(mobile = false, foreground = true)])
            assertNull(c[UsageKeys.relayEventsDup(mobile = false, foreground = true)])
        }

    @Test
    fun aMalformedEventIdIsCountedButNeverDeduplicated() =
        runLedger { accountant, l ->
            val r = relay("wss://relay.damus.io/")
            l.onConnected(r, 10, false)
            l.onIncomingMessage(r, "01234", eventWithId("short"))
            l.onIncomingMessage(r, "01234", eventWithId("short"))
            l.onIncomingMessage(r, "01234", eventWithId("zzzz".repeat(16)))

            val c = counters(accountant)
            assertEquals(3L, c[UsageKeys.relayEventsSeen(mobile = false, foreground = true)])
            assertNull(c[UsageKeys.relayEventsDup(mobile = false, foreground = true)])
        }

    @Test
    fun everyCommandAndMessageSubtypeHasItsOwnVerb() {
        // Labels are read off *instances*, because `cmd.label()` is what
        // RelayUsageListener calls — a subtype whose label() didn't return its own
        // LABEL would be invisible to a constant-only check. Asserting these against
        // UsageKeyGrammarTest's lists then keeps the two inventories from drifting,
        // which they had already started to do.
        val cmdVerbs =
            listOf(
                ReqCmd("s", listOf()),
                CloseCmd("s"),
                EventCmd(mockk(relaxed = true)),
                AuthCmd(mockk(relaxed = true)),
                CountCmd("s", listOf()),
            ).map { it.label() }
        val msgVerbs =
            listOf(
                EventMessage("s", mockk(relaxed = true)),
                EoseMessage("s"),
                OkMessage("id", true, ""),
                NoticeMessage("m"),
                AuthMessage("challenge"),
                ClosedMessage("s", "m"),
                CountMessage("s", mockk(relaxed = true)),
                NotifyMessage("m"),
                LimitsMessage(),
            ).map { it.label() }

        assertEquals(UsageKeyGrammarTest.CMD_LABELS.toSet(), cmdVerbs.toSet())
        assertEquals(UsageKeyGrammarTest.MSG_LABELS.toSet(), msgVerbs.toSet())

        // No two labels may collide within a direction, and none may be key-hostile
        // (a dot would inject uncontrolled segments — see UsageKeys.sumMatching).
        assertEquals(cmdVerbs.size, cmdVerbs.distinct().size)
        assertEquals(msgVerbs.size, msgVerbs.distinct().size)
        (cmdVerbs + msgVerbs).forEach {
            assertFalse("Label '$it' is not usable as a counter key segment", it.isEmpty() || it.contains('.'))
        }
    }
}

/**
 * NOTICE classification. The reason must come from a fixed set: this key is
 * persisted for 30 days and the text behind it is written by the relay.
 */
class NoticeReasonTest {
    @Test
    fun refusalsAreRecognisedWhateverTheRelayCallsThem() {
        // strfry's, the one Hypothesis N is about. Its NIP-01 prefix is just
        // "error", so prefix matching alone would not have caught it.
        assertEquals(UsageKeys.NOTICE_TOO_MANY_SUBS, UsageKeys.noticeReason("ERROR: too many concurrent REQs"))
        assertEquals(UsageKeys.NOTICE_TOO_MANY_SUBS, UsageKeys.noticeReason("too many concurrent NEG requests"))
        assertEquals(UsageKeys.NOTICE_TOO_MANY_SUBS, UsageKeys.noticeReason("blocked: too many subscriptions"))
    }

    @Test
    fun standardPrefixesAreCategorised() {
        assertEquals(UsageKeys.NOTICE_AUTH_REQUIRED, UsageKeys.noticeReason("auth-required: we need to know you"))
        assertEquals(UsageKeys.NOTICE_RATE_LIMITED, UsageKeys.noticeReason("rate-limited: slow down"))
        assertEquals(UsageKeys.NOTICE_RESTRICTED, UsageKeys.noticeReason("restricted: not on the allowlist"))
        assertEquals(UsageKeys.NOTICE_INVALID, UsageKeys.noticeReason("invalid: bad filter"))
        assertEquals(UsageKeys.NOTICE_BLOCKED, UsageKeys.noticeReason("blocked: you are banned"))
        assertEquals(UsageKeys.NOTICE_ERROR, UsageKeys.noticeReason("error: something broke"))
    }

    /** Verbatim from a device on 2026-08-02 — the wordings the allowlist was missing. */
    @Test
    fun realWorldNoticesAreClassified() {
        // Refusals. Each one drops a REQ that then never EOSEs, so its `since` never
        // advances and syncState re-sends it on every reconnect.
        assertEquals(UsageKeys.NOTICE_QUERY_COST, UsageKeys.noticeReason("Kgo0HH: closed: too many steps"))
        assertEquals(UsageKeys.NOTICE_QUERY_COST, UsageKeys.noticeReason("too many kinds"))
        assertEquals(UsageKeys.NOTICE_REQ_REFUSED, UsageKeys.noticeReason("Denied! This relay does not accept REQs."))

        // Chatter that costs bytes and means nothing.
        assertEquals(UsageKeys.NOTICE_BENIGN, UsageKeys.noticeReason("keepalive"))
        assertEquals(UsageKeys.NOTICE_BENIGN, UsageKeys.noticeReason("as7rp4: PERF: [/!\\ LS] 1087 scan, 0 dedup, 500 match"))

        // A bare subscription id carries no meaning and must stay unclassified rather
        // than being read as a machine-readable prefix.
        assertEquals(UsageKeys.NOTICE_UNCLASSIFIED, UsageKeys.noticeReason("AccountFollowsLoaderSubAssemblerxE1r8A"))
        assertEquals(UsageKeys.NOTICE_UNCLASSIFIED, UsageKeys.noticeReason("Kgo0HH"))
    }

    @Test
    fun aSubscriptionIdPrefixDoesNotHideTheRealOne() {
        // These relays send "<subId>: <prefix>: <text>", which makes the subId the
        // prefix and buries the standard one behind it.
        assertEquals(UsageKeys.NOTICE_AUTH_REQUIRED, UsageKeys.noticeReason("sub123: auth-required: need auth"))
        assertEquals(UsageKeys.NOTICE_RATE_LIMITED, UsageKeys.noticeReason("sub123: rate-limited: slow down"))
        // Still works without the prefix.
        assertEquals(UsageKeys.NOTICE_AUTH_REQUIRED, UsageKeys.noticeReason("auth-required: need auth"))
    }

    @Test
    fun freeFormProseNeverBecomesAKey() {
        // The bug RelayObserver had to fix: without a fixed output set, cardinality
        // grows with the number of distinct sentences relays happen to write.
        listOf(
            "hello there",
            "Please contact admin@example.com for access",
            "",
            "::::",
            "a".repeat(5000),
        ).forEach {
            val reason = UsageKeys.noticeReason(it)
            assertTrue("'$it' produced unlisted reason '$reason'", reason in UsageKeys.NOTICE_REASONS)
        }
        assertEquals(UsageKeys.NOTICE_UNCLASSIFIED, UsageKeys.noticeReason("hello there"))
    }

    @Test
    fun anUnlistedReasonCannotMintAKey() {
        assertEquals(UsageKeys.relayNotice(UsageKeys.NOTICE_UNCLASSIFIED), UsageKeys.relayNotice("something-invented"))
    }
}

/**
 * The report is sent as a NIP-17 DM, so its size is a correctness property, not a
 * cosmetic one: relays cap events and an oversized report is simply never delivered.
 */
class ReportSizeTest {
    private fun day(keys: Int) = (0 until keys).associate { "relay.purpose.p$it.bytes" to 123_456L }

    @Test
    fun aFullLedgerStaysSendable() {
        // 30 retained days at the density a busy day now produces.
        val days = (1L..30L).associateWith { day(1_200) }
        val report = ResourceUsageReportAssembler().buildReport(days, today = 30L)

        assertTrue("report was ${report.length} chars", report.length < 100_000)
        assertTrue("nothing was said about the omission", report.contains("omitted to keep this report sendable"))
    }

    @Test
    fun aSmallLedgerIsNotTruncatedAndSaysNothingAboutOmission() {
        val days = (1L..3L).associateWith { day(20) }
        val report = ResourceUsageReportAssembler().buildReport(days, today = 3L)

        assertTrue(report.contains("day 1 "))
        assertTrue(report.contains("day 3 "))
        assertFalse(report.contains("omitted"))
    }

    @Test
    fun theNewestDayIsKeptEvenIfItAloneExceedsTheBudget() {
        // Dropping everything would leave a report that says nothing at all.
        val days = mapOf(1L to day(50), 2L to day(20_000))
        val report = ResourceUsageReportAssembler().buildReport(days, today = 2L)

        assertTrue("newest day missing", report.contains("day 2 "))
        assertTrue(report.contains("1 earlier day(s) omitted"))
    }

    @Test
    fun omittedDaysStillCountTowardTheSummaryTables() {
        // The tables are built from every day; only the raw dump is bounded.
        val days = (1L..30L).associateWith { mapOf(UsageKeys.relayConnects(mobile = false, foreground = true) to 10L) }
        val report = ResourceUsageReportAssembler().buildReport(days, today = 30L)

        // 7 days x 10 connections in the week table.
        assertTrue(report.contains("| Relay reconnections | 70 "))
    }
}
