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
package com.vitorpamplona.amethyst.commons.service.nwc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [NwcPaymentTracker.cleanup] is what decides who gets to tell the user how a NIP-47
 * request ended. The give-up path and the response path race for the same entry, and
 * exactly one of them must speak — otherwise the user either hears nothing (the bug
 * that motivated this) or hears "timed out" over a refusal the wallet did send.
 */
class NwcPaymentTrackerTest {
    private val requestId = "a".repeat(64)
    private val walletPubkey = "b".repeat(64)
    private val attackerPubkey = "c".repeat(64)

    private fun trackerWithPendingRequest(): NwcPaymentTracker =
        NwcPaymentTracker().apply {
            registerRequest(requestId, walletPubkey, null) { }
        }

    @Test
    fun cleanupReportsWhoRemovedTheEntry() {
        val tracker = trackerWithPendingRequest()

        assertTrue(tracker.cleanup(requestId), "the first give-up owns the entry and must report")
        assertFalse(tracker.cleanup(requestId), "a second give-up must stay quiet")
        assertEquals(0, tracker.pendingCount())
    }

    @Test
    fun cleanupStaysQuietAfterAResponseConsumedTheRequest() {
        val tracker = trackerWithPendingRequest()

        assertIs<NwcPaymentTracker.MatchResult.Matched>(tracker.onResponseReceived(requestId, walletPubkey))
        assertFalse(
            tracker.cleanup(requestId),
            "the response already reported; a timeout firing afterwards must not overwrite it",
        )
    }

    @Test
    fun cleanupStillReportsWhenOnlySpoofedRepliesArrived() {
        val tracker = trackerWithPendingRequest()

        // A wrong-author reply is dropped and deliberately leaves the request pending,
        // so the give-up path is the only thing that can tell the user anything.
        assertIs<NwcPaymentTracker.MatchResult.WrongAuthor>(tracker.onResponseReceived(requestId, attackerPubkey))
        assertEquals(1, tracker.spoofAttemptsFor(requestId))
        assertTrue(tracker.cleanup(requestId))
    }

    @Test
    fun cleanupOfAnUnknownRequestReportsNothing() {
        assertFalse(NwcPaymentTracker().cleanup(requestId))
    }
}
