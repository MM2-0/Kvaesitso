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

import com.vitorpamplona.amethyst.commons.tor.TorPreferencesPort
import com.vitorpamplona.amethyst.commons.tor.TorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TorManager]'s self-heal logic. Drives the manager with in-memory
 * [TorBackend] + [TorPreferencesPort] fakes and a virtual clock so the 45s watchdog
 * delay and 5-min cooldown can be exercised in milliseconds.
 *
 * Companion integration test in `amethyst/src/androidTest/.../tor/TorBootstrapInstrumentedTest.kt`
 * covers the real-Arti bootstrap path on-device (currently @Ignore'd; see file for enable steps).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TorManagerTest {
    // ------------------------------------------------------------------
    // construction + persisted state
    // ------------------------------------------------------------------

    @Test
    fun `init loads persisted bypass approval`() =
        runTest(UnconfinedTestDispatcher()) {
            val recent = 1_000_000_000_000L
            val prefs = FakeTorPreferences(initialApprovalMs = recent)
            val manager = buildManager(prefs = prefs, clock = { recent + 1_000L })

            advanceUntilIdle()

            assertTrue(manager.rememberedApprovalActive())
        }

    @Test
    fun `init does not flag approval when none persisted`() =
        runTest(UnconfinedTestDispatcher()) {
            val manager = buildManager()
            advanceUntilIdle()

            assertFalse(manager.rememberedApprovalActive())
        }

    @Test
    fun `rememberedApprovalActive is false once outside the 1h window`() =
        runTest(UnconfinedTestDispatcher()) {
            val now = 1_000_000_000_000L
            val tooOld = now - TorManager.APPROVAL_REMEMBER_MS - 1L
            val prefs = FakeTorPreferences(initialApprovalMs = tooOld)
            val manager = buildManager(prefs = prefs, clock = { now })

            advanceUntilIdle()

            assertFalse(manager.rememberedApprovalActive())
        }

    // ------------------------------------------------------------------
    // torType change clears the bypass loop
    // ------------------------------------------------------------------

    @Test
    fun `torType change clears in-memory bypass and persisted approval`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialApprovalMs = 999L)
            val manager = buildManager(prefs = prefs)
            advanceUntilIdle()

            manager.sessionBypass.value = true

            prefs.setTorType(TorType.OFF)
            advanceUntilIdle()

            assertFalse(manager.sessionBypass.value)
            assertEquals(0L, prefs.lastBypassApprovalMs)
        }

    @Test
    fun `approveBypassForOneHour sets sessionBypass and persists timestamp`() =
        runTest(UnconfinedTestDispatcher()) {
            val now = 1_000_000_000_000L
            val prefs = FakeTorPreferences()
            val manager = buildManager(prefs = prefs, clock = { now })
            advanceUntilIdle()

            manager.approveBypassForOneHour()
            advanceUntilIdle()

            assertTrue(manager.sessionBypass.value)
            assertEquals(now, prefs.lastBypassApprovalMs)
            assertTrue(manager.rememberedApprovalActive())
        }

    // ------------------------------------------------------------------
    // onNetworkChange — drops client + clears bypass + primes cooldown
    // ------------------------------------------------------------------

    @Test
    fun `onNetworkChange clears bypass and persisted approval and resets backend`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialApprovalMs = 12345L)
            val backend = FakeTorBackend()
            val manager = buildManager(prefs = prefs, backend = backend)
            advanceUntilIdle()
            manager.sessionBypass.value = true

            manager.onNetworkChange()
            advanceUntilIdle()

            assertFalse(manager.sessionBypass.value)
            assertEquals(0L, prefs.lastBypassApprovalMs)
            assertTrue("onNetworkChange should reset backend at least once", backend.resetCount >= 1)
        }

    @Test
    fun `onNetworkChange primes cooldown so the watchdog does not double-reset`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // Constant clock — the only way self-heal would fire is if onNetworkChange
            // failed to prime lastSelfHealAtMs.
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()
            val resetCountBefore = backend.resetCount

            manager.onNetworkChange()
            advanceUntilIdle()

            // Status is back at Connecting after the network-change reset cycle.
            // Advance past the 45s watchdog; cooldown must suppress a second reset.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            // Exactly one extra reset from onNetworkChange itself, none from the watchdog.
            assertEquals(resetCountBefore + 1, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    // ------------------------------------------------------------------
    // onTorCircuitsDead — Active-but-failing self-heal
    // ------------------------------------------------------------------

    @Test
    fun `onTorCircuitsDead rotates exits with a warm reset when Active`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()
            backend.setActive(17392)
            advanceUntilIdle()
            val resetCountBefore = backend.resetCount

            manager.onTorCircuitsDead()
            advanceUntilIdle()

            // Exit failures are exit-side: warm reset (keep guards + cache) to rotate exits,
            // never a state wipe (which only forces a 60s cold bootstrap).
            assertEquals("Active-but-failing recovery rotates exits warm", resetCountBefore + 1, backend.resetCount)
            assertEquals("must not wipe state for an exit-side failure", 0, backend.resetWithCleanStateCount)
            // resetEpoch bump re-enters INTERNAL → start() runs again.
            assertTrue("re-init should call start() again", backend.startCount >= 2)
        }

    @Test
    fun `onTorCircuitsDead is a no-op while not Active`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()
            // Started but never reached a working Tor.
            assertFalse(manager.status.value.isFullyBootstrapped)
            val resetCountBefore = backend.resetCount

            manager.onTorCircuitsDead()
            advanceUntilIdle()

            assertEquals("no rotation while not Active", resetCountBefore, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    @Test
    fun `onTorCircuitsDead is a no-op while bypassing`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()
            backend.setActive(17392)
            advanceUntilIdle()
            manager.sessionBypass.value = true
            advanceUntilIdle()
            val resetCountBefore = backend.resetCount

            manager.onTorCircuitsDead()
            advanceUntilIdle()

            assertEquals("no rotation while bypassing", resetCountBefore, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    @Test
    fun `onTorCircuitsDead shares the self-heal cooldown`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            var clockNow = 1_000_000_000_000L
            val manager = buildManager(backend = backend, clock = { clockNow })
            advanceUntilIdle()
            backend.setActive(17392)
            advanceUntilIdle()
            val resetCountBefore = backend.resetCount

            manager.onTorCircuitsDead()
            advanceUntilIdle()
            assertEquals(resetCountBefore + 1, backend.resetCount)

            // Backend is Active again (re-init bootstrapped). A second call inside the cooldown
            // window must be suppressed.
            backend.setActive(17392)
            advanceUntilIdle()
            manager.onTorCircuitsDead()
            advanceUntilIdle()
            assertEquals("cooldown should suppress the second self-heal", resetCountBefore + 1, backend.resetCount)

            // Past the cooldown it can fire again.
            clockNow += TorManager.SELF_HEAL_COOLDOWN_MS + 1_000L
            backend.setActive(17392)
            advanceUntilIdle()
            manager.onTorCircuitsDead()
            advanceUntilIdle()
            assertEquals("after cooldown elapses, self-heal fires again", resetCountBefore + 2, backend.resetCount)
            assertEquals("exit-side rotations never wipe state", 0, backend.resetWithCleanStateCount)
        }

    // ------------------------------------------------------------------
    // stuck-Connecting watchdog
    // ------------------------------------------------------------------

    @Test
    fun `watchdog uses gentle reset before first Active`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // No client at all: this is the fast 45s no-client cadence, not a running download.
            backend.startFailsToConnect = true
            // Big constant clock so (now - lastSelfHealAtMs=0) is well past cooldown.
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()

            // Big constant clock so (now - lastSelfHealAtMs=0) is well past cooldown.
            assertFalse(manager.status.value.isFullyBootstrapped)
            assertEquals(0, backend.resetCount)

            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertEquals("gentle reset only — no state wipe before first Active", 1, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    @Test
    fun `watchdog wipes state on first stuck-Connecting when guards prove prior bootstrap`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend().apply { bootstrappedBefore = true }
            // No client at all: this is the fast 45s no-client cadence, not a running download.
            backend.startFailsToConnect = true
            // Never reaches Active in this session, but on-disk state proves a prior bootstrap.
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()

            // Never reaches Active in this session, but on-disk state proves a prior bootstrap.
            assertFalse(manager.status.value.isFullyBootstrapped)

            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertEquals("seeded from disk: must wipe stale/poisoned state, not gentle-reset", 0, backend.resetCount)
            assertEquals(1, backend.resetWithCleanStateCount)
        }

    @Test
    fun `watchdog uses full reset after first Active`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()

            // Drive backend to Active so hasEverBootstrapped flips.
            backend.setActive(9050)
            advanceUntilIdle()
            assertTrue(manager.status.value is TorServiceStatus.Active)

            // Back to Connecting — watchdog timer (re-)starts.
            backend.setConnecting()
            advanceUntilIdle()

            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertEquals(0, backend.resetCount)
            assertEquals("after Active, watchdog wipes state too", 1, backend.resetWithCleanStateCount)
        }

    @Test
    fun `watchdog cancels its delay when status leaves Connecting`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend, clock = { 1_000_000_000_000L })
            advanceUntilIdle()

            // Halfway through the watchdog delay, the bootstrap succeeds.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS / 2)
            backend.setActive(9050)
            advanceUntilIdle()

            // Past the original deadline — must NOT fire.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS)
            runCurrent()

            assertEquals(0, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
        }

    @Test
    fun `watchdog cooldown blocks a second fire within the window`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // No client at all: this is the fast 45s no-client cadence, not a running download.
            backend.startFailsToConnect = true
            var clockNow = 1_000_000_000_000L
            val manager = buildManager(backend = backend, clock = { clockNow })
            advanceUntilIdle()

            // First fire.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()
            assertEquals(1, backend.resetCount)

            // Status returns to Connecting via the reset → re-start cycle. Advance another
            // 45s of virtual time — clock has barely moved, so cooldown must block.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()
            assertEquals("cooldown should suppress the second fire", 1, backend.resetCount)
        }

    @Test
    fun `watchdog can fire again once the cooldown elapses`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // No client at all: this is the fast 45s no-client cadence, not a running download.
            backend.startFailsToConnect = true
            var clockNow = 1_000_000_000_000L
            val manager = buildManager(backend = backend, clock = { clockNow })
            advanceUntilIdle()

            // First fire.
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()
            assertEquals(1, backend.resetCount)

            // Move wall-clock past the cooldown window.
            clockNow += TorManager.SELF_HEAL_COOLDOWN_MS + 1_000L
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertEquals("after cooldown elapses, watchdog fires again", 2, backend.resetCount)
        }

    // ------------------------------------------------------------------
    // top-level status routing
    // ------------------------------------------------------------------

    @Test
    fun `status emits Off when torType is OFF`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialTorType = TorType.OFF)
            val backend = FakeTorBackend()
            val manager = buildManager(prefs = prefs, backend = backend)
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Off, manager.status.value)
            assertEquals(0, backend.startCount)
        }

    @Test
    fun `status emits Active(port) for EXTERNAL with valid port`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialTorType = TorType.EXTERNAL, initialPort = 9150)
            val manager = buildManager(prefs = prefs)
            advanceUntilIdle()

            val status = manager.status.value
            assertTrue(status is TorServiceStatus.Active)
            assertEquals(9150, (status as TorServiceStatus.Active).port)
        }

    @Test
    fun `status emits Off for EXTERNAL when port is invalid`() =
        runTest(UnconfinedTestDispatcher()) {
            val prefs = FakeTorPreferences(initialTorType = TorType.EXTERNAL, initialPort = 0)
            val manager = buildManager(prefs = prefs)
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Off, manager.status.value)
        }

    @Test
    fun `status follows backend status under INTERNAL`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Bootstrapping(17392), manager.status.value)

            backend.setActive(17392)
            advanceUntilIdle()
            assertEquals(TorServiceStatus.Active(17392), manager.status.value)
        }

    @Test
    fun `activePortOrNull mirrors the Active port`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            // activePortOrNull is WhileSubscribed — give it a subscriber for the test.
            val portJob = backgroundScope.launch { manager.activePortOrNull.collect {} }
            advanceUntilIdle()

            backend.setActive(17392)
            advanceUntilIdle()

            assertEquals(17392, manager.activePortOrNull.value)
            portJob.cancel()
        }

    @Test
    fun `sessionBypass forces Off even with torType INTERNAL`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            advanceUntilIdle()
            backend.setActive(17392)
            advanceUntilIdle()
            assertNotEquals(TorServiceStatus.Off, manager.status.value)

            manager.sessionBypass.value = true
            advanceUntilIdle()

            assertEquals(TorServiceStatus.Off, manager.status.value)
            assertTrue(backend.stopCount >= 1)
        }

    // ------------------------------------------------------------------
    // fresh install: the bootstrap-timeout retry loop
    // ------------------------------------------------------------------

    /**
     * The regression that stranded brand-new installs on "Connecting" indefinitely.
     *
     * On a native bootstrap timeout `TorService.start()` deliberately leaves status at Connecting
     * and delegates the retry to this watchdog. The watchdog used to fire once per Connecting
     * *span* — and a timeout produces no status change, so no new span ever began. Two attempts
     * were made and then the app stopped trying, permanently: no retry, no recovery short of a
     * network-identity change or a process restart.
     */
    @Test
    fun `keeps retrying when the bootstrap keeps timing out`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // No client at all: this is the fast 45s no-client cadence, not a running download.
            backend.startFailsToConnect = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            advanceTimeBy(10L * 60_000L)
            runCurrent()

            assertTrue(
                "expected repeated bootstrap retries over 10 stuck minutes, got ${backend.startCount}",
                backend.startCount >= 4,
            )
            sub.cancel()
        }

    /**
     * A bootstrap that is still running is not stuck. The native call can hold its lifecycle lock
     * for its full timeout, so a reset issued while it runs queues behind it and lands the instant
     * the attempt finishes — tearing down a client that may have just succeeded. On a fresh install
     * with a cold consensus cache that window is the common case, not a corner case.
     */
    @Test
    fun `watchdog leaves an in-flight bootstrap alone`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.holdBootstrapInFlight = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            // Well past SELF_HEAL_AFTER_MS, but the attempt is still running.
            advanceTimeBy(3L * TorManager.SELF_HEAL_AFTER_MS)
            runCurrent()

            assertEquals(0, backend.resetCount)
            assertEquals(0, backend.resetWithCleanStateCount)
            assertEquals(1, backend.startCount)

            // The attempt returns without reaching Active — now it is genuinely stuck.
            backend.finishBootstrapAttempt()
            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS + 1_000L)
            runCurrent()

            assertTrue("watchdog should fire once the attempt returned", backend.resetCount >= 1)
            sub.cancel()
        }

    /** A fresh install has no working state to protect, so it must not wait out the 5-min cooldown. */
    @Test
    fun `first-bootstrap retries use the short cooldown`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // No client at all: this is the fast 45s no-client cadence, not a running download.
            backend.startFailsToConnect = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            // Two watchdog windows: with the 5-min cooldown only one reset could land.
            advanceTimeBy(3L * TorManager.SELF_HEAL_AFTER_MS)
            runCurrent()

            assertTrue(
                "expected more than one retry inside 3 watchdog windows, got ${backend.resetCount}",
                backend.resetCount >= 2,
            )
            sub.cancel()
        }

    /** Once Tor has worked, resets stay rate-limited — a broken network must not cause a reset loop. */
    @Test
    fun `after a successful bootstrap the long cooldown still applies`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            backend.setActive(17392)
            advanceUntilIdle()
            backend.setConnecting()
            advanceUntilIdle()

            val before = backend.resetWithCleanStateCount
            advanceTimeBy(3L * TorManager.SELF_HEAL_AFTER_MS)
            runCurrent()

            assertEquals(
                "post-bootstrap self-heal must stay on the long cooldown",
                1,
                backend.resetWithCleanStateCount - before,
            )
            sub.cancel()
        }

    /**
     * `start()` blocks for a whole native bootstrap attempt. Awaiting it before subscribing to the
     * backend's status meant nothing observed Connecting until that attempt was already over, so
     * every timer keyed on the Connecting span — the stuck watchdog, the connection-failure
     * dialog — started one full attempt late.
     */
    @Test
    fun `status is observable while the first bootstrap is still running`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.startNeverReturns = true
            val manager = buildManager(backend = backend)
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            assertFalse(manager.status.value.isFullyBootstrapped)
            assertNotEquals(TorServiceStatus.Off, manager.status.value)
            sub.cancel()
        }

    /**
     * The bypass prompt asks the user to give up on Tor. Asking that while the first bootstrap
     * attempt is still downloading a cold consensus offers to abandon something that is working.
     */
    @Test
    fun `connection-failure prompt waits for the bootstrap attempt to return`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.holdBootstrapInFlight = true
            val manager = buildManager(backend = backend)
            val sub = launch { manager.status.collect { } }
            val fail = mutableListOf<Boolean>()
            val subFail = launch { manager.connectionFailure.collect { fail.add(it) } }
            advanceUntilIdle()

            advanceTimeBy(TorManager.BOOTSTRAP_TIMEOUT_MS * 2)
            runCurrent()
            assertFalse("must not prompt while the attempt is still running", fail.contains(true))

            // The first attempt returning without connecting triggers the automatic restart.
            val resetsBefore = backend.resetCount + backend.resetWithCleanStateCount
            backend.finishBootstrapAttempt()
            advanceTimeBy(1_000L)
            runCurrent()
            assertFalse("the first failure restarts Tor instead of prompting", fail.contains(true))
            assertEquals(resetsBefore + 1, backend.resetCount + backend.resetWithCleanStateCount)

            // The restarted attempt is held in flight too, so it must also be waited on.
            advanceTimeBy(TorManager.BOOTSTRAP_TIMEOUT_MS * 2)
            runCurrent()
            assertFalse("must not prompt while the restarted attempt is still running", fail.contains(true))

            backend.finishBootstrapAttempt()
            advanceTimeBy(1_000L)
            runCurrent()
            assertTrue("must prompt once the restarted attempt returned without connecting", fail.contains(true))

            sub.cancel()
            subFail.cancel()
        }

    // ------------------------------------------------------------------
    // Bootstrapping: routable, not yet ready
    // ------------------------------------------------------------------

    /**
     * The regression this state exists to prevent. On-demand bootstrap leaves Connecting in ~130ms
     * and spends the whole 12-34s directory download in Bootstrapping, so a watchdog keyed on
     * Connecting would never fire again — a download that never completes would sit there forever
     * with nothing watching it.
     */
    @Test
    fun `watchdog still fires while stuck Bootstrapping`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            assertTrue(
                "start() should leave us routable but not bootstrapped",
                manager.status.value is TorServiceStatus.Bootstrapping,
            )

            advanceTimeBy(TorManager.BOOTSTRAP_STALL_MS + 2L * TorManager.SELF_HEAL_AFTER_MS)
            runCurrent()

            assertTrue("watchdog must arm on Bootstrapping, not just Connecting", backend.resetCount >= 1)
            sub.cancel()
        }

    /** The bypass prompt must also survive the state it now spends its time in. */
    @Test
    fun `connection-failure prompt fires from a stuck Bootstrapping span`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // Virtual clock, not the default constant one: the timer now measures elapsed
            // wall-clock across the watchdog's retries, so a frozen clock makes it un-expirable.
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            val fail = mutableListOf<Boolean>()
            val subFail = launch { manager.connectionFailure.collect { fail.add(it) } }
            advanceUntilIdle()

            advanceTimeBy(2 * TorManager.BOOTSTRAP_TIMEOUT_MS + 1_000L)
            runCurrent()

            assertTrue(
                "the prompt must survive the self-heal watchdog restarting the status span at 45s",
                fail.contains(true),
            )
            sub.cancel()
            subFail.cancel()
        }

    /**
     * The whole point of the split: a dial issued during the download must be routed through the
     * proxy, not dropped to the Orbot default port where nothing listens.
     */
    @Test
    fun `port is routable while still bootstrapping`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            val sub = launch { manager.status.collect { } }
            val port = launch { manager.activePortOrNull.collect { } }
            advanceUntilIdle()

            backend.setBootstrapping(17392)
            advanceUntilIdle()

            assertEquals(17392, manager.activePortOrNull.value)
            assertTrue(manager.isSocksReady())
            sub.cancel()
            port.cancel()
        }

    /** ...but it must not be reported to the user, or to the exit-rotation path, as working Tor. */
    @Test
    fun `bootstrapping does not count as fully bootstrapped`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            backend.setBootstrapping(17392)
            advanceUntilIdle()
            assertFalse(manager.status.value.isFullyBootstrapped)

            // onTorCircuitsDead is about dead exits behind a working Tor; a download in progress is
            // not that, and resetting here would restart the very download we are waiting on.
            manager.onTorCircuitsDead()
            advanceUntilIdle()
            assertEquals(0, backend.resetCount)

            backend.setActive(17392)
            advanceUntilIdle()
            assertTrue(manager.status.value.isFullyBootstrapped)
            sub.cancel()
        }

    // ------------------------------------------------------------------
    // audit regressions
    // ------------------------------------------------------------------

    /**
     * The worst bug the audit found, now guarded by progress rather than a timer.
     *
     * A cold directory download legitimately takes 12.6-34.4s (measured), and a reset discards the
     * partial consensus — so a watchdog firing on the 45s no-client cadence could stop the download
     * ever completing. A download that is still advancing must be left alone no matter how long it
     * takes.
     */
    @Test
    fun `a download that keeps making progress is never reset`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.bootstrappedBefore = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            assertTrue(manager.status.value is TorServiceStatus.Bootstrapping)

            // Ten minutes of slow-but-real progress — far past any fixed patience window.
            repeat(20) { step ->
                advanceTimeBy(30_000L)
                backend.advanceBootstrapProgress(step * 50)
                runCurrent()
            }

            assertEquals("a progressing download must never be reset", 0, backend.resetCount)
            assertEquals("and never wiped", 0, backend.resetWithCleanStateCount)
            sub.cancel()
        }

    /** A download that stops moving is reset promptly — and still never has its cache wiped. */
    @Test
    fun `a download that stops progressing is reset but never wiped`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.bootstrappedBefore = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            backend.advanceBootstrapProgress(120)
            runCurrent()

            // Nothing moves from here on.
            advanceTimeBy(TorManager.BOOTSTRAP_STALL_MS + TorManager.SELF_HEAL_AFTER_MS)
            runCurrent()

            assertTrue("a stalled download must be escaped", backend.resetCount >= 1)
            assertEquals(
                "but the consensus cache is what it is trying to fetch — never wipe it",
                0,
                backend.resetWithCleanStateCount,
            )
            sub.cancel()
        }

    /**
     * A fresh install with corrupt on-disk state can never produce a client, and `guards.json`
     * (the only thing [ArtiGuardState] inspects) may look fine. Without an escalation the gentle
     * branch drop-and-retries forever and never clears what is actually blocking it.
     */
    @Test
    fun `a fresh install that never gets a client eventually wipes state`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            // Never bootstrapped here, and start() cannot even bind a proxy.
            backend.startFailsToConnect = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()

            advanceTimeBy(TorManager.SELF_HEAL_AFTER_MS * 12)
            runCurrent()

            assertTrue(
                "gentle resets alone never clear corrupt state; expected an escalation",
                backend.resetWithCleanStateCount >= 1,
            )
            sub.cancel()
        }

    /**
     * Each self-heal drives Bootstrapping -> Off -> Bootstrapping. If the prompt drops on the
     * transient Off and re-raises immediately after, the user gets a modal blinking at them on
     * every watchdog tick instead of a stable choice.
     */
    @Test
    fun `the bypass prompt stays up across a self-heal reset`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            val seen = mutableListOf<Boolean>()
            val subFail = launch { manager.connectionFailure.collect { seen.add(it) } }
            advanceUntilIdle()

            advanceTimeBy(2 * TorManager.BOOTSTRAP_TIMEOUT_MS + 1_000L)
            runCurrent()
            assertTrue("prompt should be up", manager.connectionFailure.value)

            // Drive several watchdog cycles; the prompt must not drop back to false.
            val raisedAt = seen.size
            advanceTimeBy(TorManager.BOOTSTRAP_STALL_MS * 3)
            runCurrent()

            assertFalse(
                "prompt blinked off during a self-heal cycle",
                seen.drop(raisedAt).contains(false),
            )
            sub.cancel()
            subFail.cancel()
        }

    // ------------------------------------------------------------------
    // automatic restart before the prompt
    // ------------------------------------------------------------------

    @Test
    fun `first timeout restarts Tor instead of prompting`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.startFailsToConnect = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            val seen = mutableListOf<Boolean>()
            val subFail = launch { manager.connectionFailure.collect { seen.add(it) } }
            advanceUntilIdle()

            advanceTimeBy(TorManager.BOOTSTRAP_TIMEOUT_MS - 1_000L)
            runCurrent()
            val startsBefore = backend.startCount
            val wipesBefore = backend.resetWithCleanStateCount

            advanceTimeBy(2_000L)
            runCurrent()
            assertFalse("the first timeout must not prompt", seen.contains(true))
            assertEquals("no client came up, so the restart wipes state", wipesBefore + 1, backend.resetWithCleanStateCount)
            assertTrue("the restart re-inits Tor", backend.startCount > startsBefore)

            // The restarted attempt gets a full timeout of its own...
            advanceTimeBy(TorManager.BOOTSTRAP_TIMEOUT_MS - 2_000L)
            runCurrent()
            assertFalse("prompted before the restarted attempt timed out", seen.contains(true))

            // ...and only when it also fails is the user asked.
            advanceTimeBy(2_000L)
            runCurrent()
            assertTrue("the restarted attempt failed too, so the user must be asked", manager.connectionFailure.value)

            sub.cancel()
            subFail.cancel()
        }

    @Test
    fun `a successful bootstrap re-arms the automatic restart`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.startFailsToConnect = true
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            val subFail = launch { manager.connectionFailure.collect { } }
            advanceUntilIdle()

            // Outage one: auto-restart, then Tor comes up.
            advanceTimeBy(TorManager.BOOTSTRAP_TIMEOUT_MS + 1_000L)
            runCurrent()
            assertFalse(manager.connectionFailure.value)
            backend.setActive(17392)
            runCurrent()

            // Outage two starts fresh: it also gets its automatic restart before any prompt.
            backend.setConnecting()
            runCurrent()
            val resetsBefore = backend.resetCount + backend.resetWithCleanStateCount
            advanceTimeBy(TorManager.BOOTSTRAP_TIMEOUT_MS + 1_000L)
            runCurrent()
            assertFalse("a new outage must be restarted before prompting", manager.connectionFailure.value)
            assertTrue(backend.resetCount + backend.resetWithCleanStateCount > resetsBefore)

            sub.cancel()
            subFail.cancel()
        }

    // ------------------------------------------------------------------
    // restartTor — the dialog's "Restart Tor" button
    // ------------------------------------------------------------------

    @Test
    fun `restartTor closes the prompt and re-inits Tor`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val epoch = 1_700_000_000_000L
            val manager = buildManager(backend = backend, clock = { epoch + testScheduler.currentTime })
            val sub = launch { manager.status.collect { } }
            val subFail = launch { manager.connectionFailure.collect { } }
            advanceUntilIdle()

            advanceTimeBy(2 * TorManager.BOOTSTRAP_TIMEOUT_MS + 1_000L)
            runCurrent()
            assertTrue("prompt should be up", manager.connectionFailure.value)
            val startsBefore = backend.startCount
            val resetsBefore = backend.resetCount + backend.resetWithCleanStateCount

            manager.restartTor()
            runCurrent()

            assertFalse("restart should close the prompt", manager.connectionFailure.value)
            assertEquals(resetsBefore + 1, backend.resetCount + backend.resetWithCleanStateCount)
            assertTrue("re-init should call start() again", backend.startCount > startsBefore)

            // The new attempt earns a full timeout of its own before the prompt can return.
            advanceTimeBy(TorManager.BOOTSTRAP_TIMEOUT_MS - 1_000L)
            runCurrent()
            assertFalse("prompt came back before the new attempt timed out", manager.connectionFailure.value)

            sub.cancel()
            subFail.cancel()
        }

    @Test
    fun `restartTor keeps the cache for a stalled download`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            val manager = buildManager(backend = backend)
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()
            backend.setBootstrapping()
            runCurrent()
            val resetsBefore = backend.resetCount

            manager.restartTor()
            runCurrent()

            assertEquals(resetsBefore + 1, backend.resetCount)
            assertEquals("must not wipe a partial consensus", 0, backend.resetWithCleanStateCount)
            sub.cancel()
        }

    @Test
    fun `restartTor wipes state when no client comes up`() =
        runTest(UnconfinedTestDispatcher()) {
            val backend = FakeTorBackend()
            backend.startFailsToConnect = true
            val manager = buildManager(backend = backend)
            val sub = launch { manager.status.collect { } }
            advanceUntilIdle()
            runCurrent()

            manager.restartTor()
            runCurrent()

            assertEquals(1, backend.resetWithCleanStateCount)
            sub.cancel()
        }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun TestScope.buildManager(
        prefs: FakeTorPreferences = FakeTorPreferences(),
        backend: FakeTorBackend = FakeTorBackend(),
        clock: () -> Long = { 1_000_000_000_000L },
    ): TorManager =
        TorManager(
            torPrefs = prefs,
            service = backend,
            scope = backgroundScope,
            // Unconfined so `MutableStateFlow.value = …` propagates through `flowOn`
            // synchronously — otherwise advanceUntilIdle never settles the cross-dispatcher
            // channel and `manager.status.value` is observed as the stateIn initial (Off).
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            nowMs = clock,
        )
}

/** In-memory [TorBackend] driven by tests. */
private class FakeTorBackend : TorBackend {
    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    private val _bootstrapInFlight = MutableStateFlow(false)
    override val bootstrapInFlight: StateFlow<Boolean> = _bootstrapInFlight.asStateFlow()

    private val _bootstrapProgress = MutableStateFlow(-1)
    override val bootstrapProgress: StateFlow<Int> = _bootstrapProgress.asStateFlow()

    /** Models the directory download advancing. Only distinct values count as progress. */
    fun advanceBootstrapProgress(permille: Int) {
        _bootstrapProgress.value = permille
    }

    /**
     * When true, [start] models a native bootstrap that is still running: status goes Connecting
     * and [bootstrapInFlight] stays true until [finishBootstrapAttempt] is called.
     */
    var holdBootstrapInFlight = false

    /** Models the native bootstrap attempt returning without reaching Active (Arti's own timeout). */
    fun finishBootstrapAttempt() {
        _bootstrapInFlight.value = false
    }

    var startCount = 0
        private set
    var stopCount = 0
        private set
    var resetCount = 0
        private set
    var resetWithCleanStateCount = 0
        private set

    /** Simulates a persisted confirmed guard on disk (prior successful bootstrap). */
    var bootstrappedBefore = false

    /** Lets a test fire Arti's "every guard was rejected" signal (see [TorBackend.guardsDownSignal]). */
    val guardsDown = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val guardsDownSignal: Flow<Unit> = guardsDown

    override suspend fun hasBootstrappedBefore(): Boolean = bootstrappedBefore

    /**
     * Set to model the real backend, whose `start()` does not return until the blocking native
     * bootstrap attempt has finished. The status is published as soon as the attempt begins, the
     * same as [TorService] does.
     */
    var startNeverReturns = false

    /** When true, start() models a client that cannot be created at all: status stays Connecting. */
    var startFailsToConnect = false

    /**
     * Mirrors [TorService]: the native client is created in ~130ms and the proxy binds, so a real
     * start lands in [TorServiceStatus.Bootstrapping] — routable, directory still downloading — not
     * in Connecting. Tests that want the pre-proxy state call [setConnecting].
     */
    override suspend fun start() {
        startCount++
        if (startFailsToConnect) {
            _status.value = TorServiceStatus.Connecting
            return
        }
        _status.value = TorServiceStatus.Bootstrapping(17392)
        if (holdBootstrapInFlight) _bootstrapInFlight.value = true
        if (startNeverReturns) awaitCancellation()
    }

    override suspend fun stop() {
        stopCount++
        _status.value = TorServiceStatus.Off
    }

    override suspend fun reset() {
        resetCount++
        _bootstrapInFlight.value = false
        _status.value = TorServiceStatus.Off
    }

    override suspend fun resetWithCleanState() {
        resetWithCleanStateCount++
        _bootstrapInFlight.value = false
        _status.value = TorServiceStatus.Off
    }

    fun setActive(port: Int) {
        _bootstrapInFlight.value = false
        _status.value = TorServiceStatus.Active(port)
    }

    fun setConnecting() {
        _status.value = TorServiceStatus.Connecting
    }

    fun setBootstrapping(port: Int = 17392) {
        _status.value = TorServiceStatus.Bootstrapping(port)
    }
}

/** In-memory [TorPreferencesPort] driven by tests. */
private class FakeTorPreferences(
    initialTorType: TorType = TorType.INTERNAL,
    initialPort: Int = 9050,
    initialApprovalMs: Long = 0L,
) : TorPreferencesPort {
    private val _torType = MutableStateFlow(initialTorType)
    private val _externalSocksPort = MutableStateFlow(initialPort)

    override val torType: StateFlow<TorType> = _torType.asStateFlow()
    override val externalSocksPort: StateFlow<Int> = _externalSocksPort.asStateFlow()

    var lastBypassApprovalMs: Long = initialApprovalMs

    override suspend fun loadLastBypassApprovalMs(): Long = lastBypassApprovalMs

    override suspend fun saveLastBypassApprovalMs(value: Long) {
        lastBypassApprovalMs = value
    }

    fun setTorType(value: TorType) {
        _torType.value = value
    }
}
