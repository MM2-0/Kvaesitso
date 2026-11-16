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
import android.location.Location
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All tests run on [UnconfinedTestDispatcher] rather than the `runTest {}` default
 * ([StandardTestDispatcher]): this suite's `combine` + `transformLatest` +
 * `stateIn(WhileSubscribed)` + `backgroundScope.launch { collect }` chain does not
 * get driven to completion by `advanceUntilIdle()` alone under the standard
 * dispatcher — the producer side never runs. Same root cause, same fix as
 * `amethyst/src/test/.../ui/tor/TorManagerTest.kt`, which hits it for the same
 * reason (see its `ioDispatcher = UnconfinedTestDispatcher(...)` comment).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationStateTest {
    private fun locationAt(
        lat: Double,
        lon: Double,
    ): Location =
        mockk<Location> {
            every { latitude } returns lat
            every { longitude } returns lon
        }

    /** Counts subscriptions and completions of the underlying location source. */
    private class SourceProbe(
        private val body: suspend FlowCollector<Location>.() -> Unit,
    ) {
        var subscriptions = 0
            private set
        var completions = 0
            private set

        /** The (minTimeMs, minDistanceM) pair the source function was called with. */
        var requestedProfile: Pair<Long, Float>? = null
            private set

        val live: Int get() = subscriptions - completions

        fun source(): (Long, Float) -> Flow<Location> =
            { minTimeMs, minDistanceM ->
                requestedProfile = minTimeMs to minDistanceM
                flow(body)
                    .onStart { subscriptions++ }
                    .onCompletion { completions++ }
            }
    }

    private fun neverEmits() = SourceProbe { awaitCancellation() }

    private fun emitsOnceThenHangs(
        lat: Double,
        lon: Double,
    ) = SourceProbe {
        // Yield before emitting so the fix arrives as a genuine suspension
        // point (as a real LocationManager callback would) instead of a
        // synchronous burst. Without this, under UnconfinedTestDispatcher a
        // fix that lands in the same undispatched execution as the
        // immediately-preceding Loading emission can be conflated away by
        // the downstream StateFlow before a collector observes either value.
        yield()
        emit(locationAt(lat, lon))
        awaitCancellation()
    }

    private fun stateWith(
        scope: CoroutineScope,
        foreground: MutableStateFlow<Boolean>,
        probe: SourceProbe,
    ) = LocationState(
        context = mockk<Context>(relaxed = true),
        scope = scope,
        isForeground = foreground,
        locationSource = probe.source(),
    )

    @Test
    fun doesNotListenWhileBackgrounded() =
        runTest(UnconfinedTestDispatcher()) {
            val probe = neverEmits()
            val foreground = MutableStateFlow(false)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            // Not a test artefact: `gate` is a `combine`, so it produces nothing
            // until `settledForeground` first emits, and this test starts
            // backgrounded — `settledForeground` waits out BACKGROUND_GRACE_MS
            // before its first emission (ForegroundTracker starts at `false`).
            // LocationState's own KDoc calls this "known and harmless"; without
            // advancing past it here, the gate never resolves and the assertion
            // below would pass vacuously regardless of the implementation.
            advanceTimeBy(LocationState.BACKGROUND_GRACE_MS + 1)
            advanceUntilIdle()

            assertEquals(0, probe.subscriptions)
        }

    @Test
    fun listensOnceWhileForegrounded() =
        runTest(UnconfinedTestDispatcher()) {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            assertEquals(1, probe.subscriptions)
            assertEquals(1, probe.live)
        }

    @Test
    fun releasesTheSourceAfterTheGracePeriodAndKeepsTheLastFix() =
        runTest(UnconfinedTestDispatcher()) {
            val probe = emitsOnceThenHangs(56.048839, 12.721029)
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            val fixWhileForeground = state.geohashStateFlow.value
            assertTrue("expected a Success, got $fixWhileForeground", fixWhileForeground is LocationResult.Success)

            foreground.value = false
            // Real grace-period wait, not a test artefact: `settledForeground`
            // delays BACKGROUND_GRACE_MS before it settles to `false`, so the
            // gate has genuinely not transitioned to Paused until this elapses.
            advanceTimeBy(LocationState.BACKGROUND_GRACE_MS + 1)
            advanceUntilIdle()

            assertEquals("source must be released once backgrounded", 0, probe.live)
            assertEquals(
                "the last geohash must survive the release for the ~60 synchronous .value readers",
                fixWhileForeground,
                state.geohashStateFlow.value,
            )
        }

    @Test
    fun keepsListeningAcrossABackgroundEdgeShorterThanTheGracePeriod() =
        runTest(UnconfinedTestDispatcher()) {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()
            assertEquals(1, probe.subscriptions)

            foreground.value = false
            advanceTimeBy(LocationState.BACKGROUND_GRACE_MS / 2)
            foreground.value = true
            advanceUntilIdle()

            assertEquals("a brief app switch must not tear down the registration", 1, probe.subscriptions)
            assertEquals(1, probe.live)
        }

    @Test
    fun doesNotReemitLoadingWhenReturningToForegroundWithACachedFix() =
        runTest(UnconfinedTestDispatcher()) {
            val probe = emitsOnceThenHangs(56.048839, 12.721029)
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            val seen = mutableListOf<LocationResult>()
            backgroundScope.launch { state.geohashStateFlow.collect { seen.add(it) } }
            advanceUntilIdle()

            foreground.value = false
            // Same real grace-period wait as above — needed so the gate actually
            // reaches Paused before we flip back to foreground and look for a
            // stray Loading.
            advanceTimeBy(LocationState.BACKGROUND_GRACE_MS + 1)
            advanceUntilIdle()
            val afterBackground = seen.size

            foreground.value = true
            advanceUntilIdle()

            assertTrue(
                "returning to foreground must not flash Loading — AroundMeFeedFlow renders an empty feed for it. Saw: ${seen.drop(afterBackground)}",
                seen.drop(afterBackground).none { it is LocationResult.Loading },
            )
        }

    /**
     * NOTE — does not discriminate the `Gate.Listen` Loading-guard it might sound
     * like it covers: `geohashStateFlow` is seeded via
     * `stateIn(..., latestLocation)`, and `latestLocation` defaults to `Loading`
     * at construction, so *every* fresh collector on a brand-new [LocationState]
     * sees `Loading` first purely from that seed — regardless of whether
     * `Gate.Listen`'s own `if (latest() !is Success) emit(Loading)` line exists,
     * is guarded, or is deleted outright. Verified: deleting that emit still
     * leaves this test green. The guard's "only when nothing is cached" half IS
     * covered, by [doesNotReemitLoadingWhenReturningToForegroundWithACachedFix].
     * The "emits Loading when nothing is cached" half is not protected by any
     * test in this file. What this test does verify — honestly — is that a
     * fresh [LocationState] surfaces `Loading` before any fix arrives.
     */
    @Test
    fun freshStateSurfacesLoadingBeforeAnyFix() =
        runTest(UnconfinedTestDispatcher()) {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            val seen = mutableListOf<LocationResult>()
            backgroundScope.launch { state.geohashStateFlow.collect { seen.add(it) } }
            advanceUntilIdle()

            assertTrue("expected Loading, saw $seen", seen.any { it is LocationResult.Loading })
        }

    @Test
    fun reportsLackPermissionRegardlessOfForeground() =
        runTest(UnconfinedTestDispatcher()) {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(false)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            assertEquals(LocationResult.LackPermission, state.geohashStateFlow.value)
            assertEquals(0, probe.subscriptions)
        }

    @Test
    fun requestsTheDocumentedPollingProfilePerFlow() =
        runTest(UnconfinedTestDispatcher()) {
            val foreground = MutableStateFlow(true)

            val coarseProbe = neverEmits()
            val coarseState = stateWith(backgroundScope, foreground, coarseProbe)
            coarseState.setLocationPermission(true)
            backgroundScope.launch { coarseState.geohashStateFlow.collect { } }

            val preciseProbe = neverEmits()
            val preciseState = stateWith(backgroundScope, foreground, preciseProbe)
            preciseState.setLocationPermission(true)
            backgroundScope.launch { preciseState.preciseGeohashStateFlow.collect { } }

            advanceUntilIdle()

            assertEquals(
                "geohashStateFlow must poll at the coarse profile",
                LocationState.COARSE_MIN_TIME to LocationState.COARSE_MIN_DISTANCE,
                coarseProbe.requestedProfile,
            )
            assertEquals(
                "preciseGeohashStateFlow must poll at the precise profile",
                LocationState.PRECISE_MIN_TIME to LocationState.PRECISE_MIN_DISTANCE,
                preciseProbe.requestedProfile,
            )
        }
}
