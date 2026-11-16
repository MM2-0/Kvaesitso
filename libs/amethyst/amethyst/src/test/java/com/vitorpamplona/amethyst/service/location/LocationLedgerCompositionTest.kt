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
package com.vitorpamplona.amethyst.service.location

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import com.vitorpamplona.amethyst.commons.service.resourceusage.RefCountedSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wires the *real* [LocationState] -> [LocationFlow] -> [RefCountedSession]
 * seam together, exercising [LocationState]'s **default** `locationSource`
 * rather than overriding it the way [LocationStateTest] and [LocationFlowTest]
 * do. Each of those files mocks the other layer out, so neither can catch a
 * regression in the composition itself — e.g. a refcount that gets pinned
 * open (or closed) because `onListening` fires in an order or multiplicity
 * the two independent [LocationState] flows didn't anticipate. That failure
 * mode is unrecoverable at runtime: a pinned-open refcount means
 * `location.ms` accrues in the resource-usage ledger forever, with nothing
 * actually listening.
 *
 * Same dispatcher note as [LocationStateTest]: `runTest {}`'s default
 * `StandardTestDispatcher` does not drive this `combine` + `transformLatest` +
 * `stateIn(WhileSubscribed)` + `backgroundScope.launch { collect }` chain to
 * completion via `advanceUntilIdle()` alone, so every test here runs on
 * `UnconfinedTestDispatcher`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationLedgerCompositionTest {
    /** A [LocationManager] that reports two providers and never denies registration. */
    private fun locationManager(): LocationManager = mockLocationManager(providers = listOf("fused", "network"))

    /** A [Context] whose `LOCATION_SERVICE` lookup returns [locationManager]. */
    private fun contextWithLocationService(locationManager: LocationManager): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        return context
    }

    /**
     * Wires a real [LocationState] to a real [RefCountedSession], standing in
     * for the resource-usage ledger session. Deliberately does NOT override
     * `locationSource` — that is the whole point of this file.
     *
     * Exposes [locationManager] so a test can `verify` against it directly —
     * e.g. that exactly one of two concurrent registrations was torn down —
     * rather than inferring release from the ledger alone, which cannot tell
     * "released" from "never released" on its own (see
     * `bothFlowsActiveThenOneStopsKeepsTheLedgerOpen`).
     */
    private class Harness(
        scope: CoroutineScope,
        context: Context,
        val locationManager: LocationManager,
    ) {
        /** Stand-in for `SessionTimeIntegrator.setActive`, i.e. the ledger. */
        val ledger = mutableListOf<Boolean>()
        private val refCount = RefCountedSession { ledger.add(it) }
        val foreground = MutableStateFlow(true)
        val state =
            LocationState(
                context = context,
                scope = scope,
                isForeground = foreground,
                onListening = { refCount.setActive(it) },
            )
    }

    private fun harness(scope: CoroutineScope): Harness {
        val locationManager = locationManager()
        return Harness(scope, contextWithLocationService(locationManager), locationManager)
    }

    /**
     * The interleaving [RefCountedSession] exists for. Two independent
     * [LocationState] flows both register with the OS; the ledger must open
     * once, not twice. Stopping one of the two must NOT close the ledger,
     * because the other is still listening — this half is exactly what a
     * per-flow (rather than refcounted) `onListening` -> ledger wiring would
     * get wrong, and what a per-layer test (mocking `locationSource`, or
     * driving `RefCountedSession` directly with synthetic booleans) cannot
     * see, because it never lets two real [LocationFlow] registrations race
     * each other through the shared hook.
     *
     * Asserts its premise, not just its consequence. The ledger staying at
     * `[true]` after the precise flow stops is also what you'd see if that
     * flow's registration never released at all — `onListening` deleted from
     * [LocationFlow]'s `finally`, cleanup moved somewhere unreached, or the
     * [LocationState.SUBSCRIPTION_STOP_TIMEOUT_MS] teardown simply hadn't
     * elapsed. The
     * `removeUpdates` verify below rules out the OS-registration side of
     * that; cancelling the coarse flow too and checking the full `[true,
     * false]` sequence at the end rules out the `onListening` side, since
     * `removeUpdates` alone fires unconditionally in the `finally` and would
     * not by itself notice `onListening` going missing.
     */
    @Test
    fun bothFlowsActiveThenOneStopsKeepsTheLedgerOpen() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = harness(backgroundScope)
            harness.state.setLocationPermission(true)

            val preciseJob = backgroundScope.launch { harness.state.preciseGeohashStateFlow.collect { } }
            val coarseJob = backgroundScope.launch { harness.state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            assertEquals(
                "both flows registering with the OS must open the ledger exactly once, not twice",
                listOf(true),
                harness.ledger,
            )

            preciseJob.cancelAndJoin()
            // The cancelled collector alone doesn't tear down the upstream
            // registration — WhileSubscribed keeps it alive for a grace
            // period first. Advance past it so the release below is
            // actually observable, not just "not yet due".
            advanceTimeBy(LocationState.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
            advanceUntilIdle()

            // Exactly one of the two OS registrations should have been torn
            // down at this point — the precise one — while the coarse one is
            // still live.
            verify(exactly = 1) { harness.locationManager.removeUpdates(any<LocationListener>()) }

            assertEquals(
                "the coarse flow is still listening; stopping the precise one alone must not close the ledger",
                listOf(true),
                harness.ledger,
            )

            // Stop the coarse flow too, so the ledger closing here proves
            // the release path genuinely works end-to-end for this test's
            // own harness — not merely that the assertions above never
            // exercised it.
            coarseJob.cancelAndJoin()
            advanceTimeBy(LocationState.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
            advanceUntilIdle()

            verify(exactly = 2) { harness.locationManager.removeUpdates(any<LocationListener>()) }

            assertEquals(
                "both flows stopping must close the ledger exactly once, proving the precise flow's earlier release was real",
                listOf(true, false),
                harness.ledger,
            )
        }

    /**
     * The full foreground -> background cycle with both flows registered.
     * The ledger must see exactly `[true, false]`: one open when the first
     * flow registers (the second joining must not re-open it), one close
     * once BOTH flows have released (not one close per flow, and not before
     * the background grace period each flow independently waits out).
     */
    @Test
    fun fullCycleWithBothFlowsOpensAndClosesTheLedgerExactlyOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = harness(backgroundScope)
            harness.state.setLocationPermission(true)

            backgroundScope.launch { harness.state.geohashStateFlow.collect { } }
            backgroundScope.launch { harness.state.preciseGeohashStateFlow.collect { } }
            advanceUntilIdle()

            assertEquals(listOf(true), harness.ledger)

            harness.foreground.value = false
            // Real grace-period wait, not a test artefact: settledForeground
            // delays BACKGROUND_GRACE_MS before the gate reaches Paused for
            // either flow. See LocationStateTest for the worked example this
            // follows.
            advanceTimeBy(LocationState.BACKGROUND_GRACE_MS + 1)
            advanceUntilIdle()

            assertEquals(
                "backgrounding must close the ledger exactly once, not once per flow, and not before the grace period",
                listOf(true, false),
                harness.ledger,
            )
        }

    /**
     * After a full open/close cycle, returning to the foreground must reopen
     * the ledger exactly once — not once per flow, and not a second time on
     * top of a close that never actually happened.
     */
    @Test
    fun returningToForegroundReopensTheLedgerExactlyOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val harness = harness(backgroundScope)
            harness.state.setLocationPermission(true)

            backgroundScope.launch { harness.state.geohashStateFlow.collect { } }
            backgroundScope.launch { harness.state.preciseGeohashStateFlow.collect { } }
            advanceUntilIdle()

            harness.foreground.value = false
            advanceTimeBy(LocationState.BACKGROUND_GRACE_MS + 1)
            advanceUntilIdle()

            harness.foreground.value = true
            advanceUntilIdle()

            assertEquals(
                "a return to foreground after a full close must reopen the ledger exactly once",
                listOf(true, false, true),
                harness.ledger,
            )
        }
}
