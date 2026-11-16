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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.relayClient.paging.WindowLoadTracker
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Real-time tests for the **idle** backstop: when a relay streams stored events but never sends EOSE,
 * the window can't complete on "every relay settled", so the idle timer finishes it once the stream
 * goes quiet — but only after every still-pending relay has been *heard from*, so a slow connect (a
 * relay that hasn't answered yet) is never mistaken for a stream that has gone quiet.
 */
class WindowLoadTrackerIdleTest {
    private val good = NormalizedRelayUrl("wss://vitor.nostr1.com/")
    private val streamer = NormalizedRelayUrl("wss://relay.damus.io/")

    @Test
    fun idleBackstopCompletesARelayThatStreamsButNeverEoses() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val tracker = WindowLoadTracker(name = "test", idleTimeout = 50.milliseconds)

            tracker.startLoading(scope)
            tracker.setExpectedRelays(setOf(good, streamer))
            tracker.onRelaySettled(good) // `good` finishes with an EOSE
            // `streamer` keeps delivering stored events but never sends EOSE — so "every relay settled"
            // can never complete this window. Its events keep it "heard from" (idle gate satisfied).
            tracker.onRelayEvent(streamer)
            tracker.onRelayEvent(streamer)

            // The only way out is the idle backstop, once the stream stays quiet for idleTimeout.
            withTimeout(3000) { tracker.loading.first { !it } }
            scope.cancel()
        }

    @Test
    fun idleBackstopWaitsUntilEveryPendingRelayHasBeenHeardFrom() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val tracker = WindowLoadTracker(name = "test", idleTimeout = 50.milliseconds)

            tracker.startLoading(scope)
            tracker.setExpectedRelays(setOf(good, streamer))
            tracker.onRelaySettled(good)
            // `streamer` has NOT been heard from yet (still connecting). The idle gate must hold the window
            // open — a connection gap is not a quiet stream. Wait well past idleTimeout AND a watchdog tick.
            Thread.sleep(800)
            assertTrue("idle must not fire while a pending relay has never been heard from", tracker.loading.value)

            // Once it delivers something (now heard-from) and the stream goes quiet, idle completes it.
            tracker.onRelayEvent(streamer)
            withTimeout(3000) { tracker.loading.first { !it } }
            scope.cancel()
        }
}
