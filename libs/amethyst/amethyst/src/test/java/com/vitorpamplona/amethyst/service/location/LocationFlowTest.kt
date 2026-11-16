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

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationFlowTest {
    private fun manager(
        providers: List<String>,
        denied: Set<String> = emptySet(),
    ) = mockLocationManager(providers, denied)

    @Test
    fun firesNeitherEdgeWhenNoProviderExists() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val flow = LocationFlow(manager(providers = emptyList()), sdkInt = 37).get(60_000L, 500f) { edges.add(it) }

            val failure = runCatching { flow.collect { } }.exceptionOrNull()

            assertTrue("expected SecurityException, got $failure", failure is SecurityException)
            assertEquals(emptyList<Boolean>(), edges)
        }

    @Test
    fun firesNeitherEdgeWhenEveryRungIsDenied() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("fused", "network"), denied = setOf("fused", "network"))
            val flow = LocationFlow(lm, sdkInt = 37).get(60_000L, 500f) { edges.add(it) }

            val failure = runCatching { flow.collect { } }.exceptionOrNull()

            assertTrue("expected SecurityException, got $failure", failure is SecurityException)
            assertEquals(emptyList<Boolean>(), edges)
        }

    @Test
    fun fallsThroughToTheNextRungWhenOneIsDenied() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("fused", "network"), denied = setOf("fused"))
            val job = launch { LocationFlow(lm, sdkInt = 37).get(60_000L, 500f) { edges.add(it) }.collect { } }

            runCurrent()

            assertEquals(listOf(true), edges)
            verify { lm.requestLocationUpdates("network", 60_000L, 500f, any<LocationListener>(), any()) }

            job.cancelAndJoin()
        }

    @Test
    fun pairsTheListeningEdgesAroundASuccessfulRegistration() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("network"))
            val job = launch { LocationFlow(lm, sdkInt = 30).get(60_000L, 500f) { edges.add(it) }.collect { } }

            runCurrent()
            assertEquals(listOf(true), edges)

            job.cancelAndJoin()

            assertEquals(listOf(true, false), edges)
            verify { lm.removeUpdates(any<LocationListener>()) }
        }

    @Test
    fun releasesTheRegistrationWhenTheSeedThrows() =
        runTest {
            // Deterministic discriminator for the try/finally: make the seed
            // sweep throw. A plain RuntimeException is used rather than
            // SecurityException, because freshestLastKnownLocation() catches
            // SecurityException internally per-provider and it would never
            // propagate out of the try block at all. With cleanup living in
            // try/finally, onListening(false)/removeUpdates still run even
            // though the block exits via an exception; if cleanup lived in
            // awaitClose instead, the throw would unwind past it before
            // awaitClose is ever reached and the release would be skipped.
            val edges = mutableListOf<Boolean>()
            val lm = mockk<LocationManager>(relaxed = true)
            every { lm.allProviders } returns listOf("network")
            every { lm.getLastKnownLocation(any()) } throws RuntimeException("boom")

            val failure =
                runCatching {
                    LocationFlow(lm, sdkInt = 30).get(60_000L, 500f) { edges.add(it) }.collect { }
                }.exceptionOrNull()

            assertTrue("expected the seed's RuntimeException to surface, got $failure", failure is RuntimeException)
            assertEquals("the acquire must be released even when the seed throws", listOf(true, false), edges)
            verify { lm.removeUpdates(any<LocationListener>()) }
        }

    @Test
    fun emitsNothingBeforeThrowingWhenNoProviderRegisters() =
        runTest {
            // Discriminates the seed-after-registration ordering: every
            // provider is denied, so the registration loop never succeeds and
            // the flow must throw before ever reaching the seed. A cached fix
            // is deliberately made available (non-null, with a real `time`)
            // so that a seed-first implementation — which would emit it before
            // discovering no provider registers — fails this test.
            //
            // The `values` assertion below is necessary but, on its own, is
            // not sufficient to catch a seed-first regression here: a value
            // `send`-ed into the callbackFlow channel immediately before the
            // producer coroutine throws can be dropped by structured-
            // concurrency teardown before this collector's suspended
            // `receive` is ever resumed, so the collector may see zero
            // values purely as a scheduling artifact, independent of
            // ordering. The `getLastKnownLocation` verify is what actually
            // discriminates: it fails deterministically whenever the seed is
            // attempted at all, regardless of whether registration
            // eventually succeeds — which is exactly what a seed-first
            // implementation does and what the current try/finally-after-
            // registration implementation must never do on this path.
            val values = mutableListOf<Location>()
            val lm = manager(providers = listOf("fused", "network"), denied = setOf("fused", "network"))
            val cached = mockk<Location>()
            every { cached.time } returns 1_000L
            every { lm.getLastKnownLocation(any()) } returns cached

            val failure =
                runCatching {
                    LocationFlow(lm, sdkInt = 37).get(60_000L, 500f).collect { values.add(it) }
                }.exceptionOrNull()

            assertTrue("expected SecurityException, got $failure", failure is SecurityException)
            assertEquals("no value should be emitted before the throw", emptyList<Location>(), values)
            verify(exactly = 0) { lm.getLastKnownLocation(any()) }
        }

    @Test
    fun seedsOnlyFromTheProvidersTheLadderDeemedUsable() =
        runTest {
            // On the coarse-only legacy path the ladder narrows to `network`,
            // and gps/passive/fused would each throw SecurityException. Sweeping
            // every provider the device reports would pay those guaranteed
            // throws — stack trace plus an eagerly-interpolated Log.w — on every
            // flow start, i.e. on every foreground return.
            val lm = manager(providers = listOf("fused", "network", "gps", "passive"))
            val job = launch { LocationFlow(lm, sdkInt = 30).get(60_000L, 500f).collect { } }

            runCurrent()

            verify(exactly = 1) { lm.getLastKnownLocation("network") }
            verify(exactly = 0) { lm.getLastKnownLocation(neq("network")) }

            job.cancelAndJoin()
        }

    @Test
    fun registersOnExactlyOneProvider() =
        runTest {
            val lm = manager(providers = listOf("fused", "network", "gps", "passive"))
            val job = launch { LocationFlow(lm, sdkInt = 37).get(60_000L, 500f).collect { } }

            runCurrent()

            verify(exactly = 1) {
                lm.requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
            }

            job.cancelAndJoin()
        }
}
