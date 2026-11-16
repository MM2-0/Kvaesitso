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
package com.vitorpamplona.amethyst.commons.service.broadcast

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BroadcastEventTest {
    private val outboxA = NormalizedRelayUrl("wss://outbox-a.test/")
    private val outboxB = NormalizedRelayUrl("wss://outbox-b.test/")
    private val inbox = NormalizedRelayUrl("wss://inbox.test/")

    private val event =
        Event(
            id = "a".padEnd(64, '0'),
            pubKey = "pub".padEnd(64, '0'),
            createdAt = 0L,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "sig".padEnd(128, '0'),
        )

    private fun broadcast(
        id: String = "b1",
        outbox: Set<NormalizedRelayUrl> = setOf(outboxA, outboxB),
    ) = BroadcastEvent(
        id = id,
        event = event,
        targetRelays = listOf(outboxA, outboxB, inbox),
        outboxRelays = outbox,
    )

    @Test
    fun isOutOnceEveryOutboxRelayAccepted() {
        val b =
            broadcast()
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Success)

        assertTrue(b.isOut, "the non-outbox relay is still pending, but the post is out")
    }

    @Test
    fun isNotOutWhileAnOutboxRelayIsPending() {
        val b =
            broadcast()
                .withResult(outboxA, RelayResult.Success)
                .withResult(inbox, RelayResult.Success)

        assertFalse(b.isOut)
    }

    @Test
    fun isNotOutWhenAnOutboxRelayFailed() {
        val rejected =
            broadcast()
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Error("blocked"))
                .withResult(inbox, RelayResult.Success)
        val timedOut =
            broadcast()
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Timeout)
                .withResult(inbox, RelayResult.Success)

        assertFalse(rejected.isOut)
        assertFalse(timedOut.isOut)
    }

    @Test
    fun isNotOutWhileAnOutboxRelayIsRetrying() {
        val b =
            broadcast()
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Retrying)
                .withResult(inbox, RelayResult.Success)

        assertFalse(b.isOut)
    }

    @Test
    fun aFailedNonOutboxRelayDoesNotHoldThePostBack() {
        val b =
            broadcast()
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Success)
                .withResult(inbox, RelayResult.Timeout)

        assertTrue(b.isOut)
    }

    @Test
    fun withoutOutboxTargetsAnyAcceptanceIsOut() {
        val pending = broadcast(outbox = emptySet())
        val accepted = pending.withResult(inbox, RelayResult.Success)
        val allFailed =
            pending
                .withResult(outboxA, RelayResult.Error("no"))
                .withResult(outboxB, RelayResult.Timeout)
                .withResult(inbox, RelayResult.Timeout)

        assertFalse(pending.isOut)
        assertTrue(accepted.isOut)
        assertFalse(allFailed.isOut)
    }

    @Test
    fun statusSummarisesOnceEveryRelayAnswered() {
        val pending = broadcast()
        val success = pending.withResult(outboxA, RelayResult.Success).withResult(outboxB, RelayResult.Success).withResult(inbox, RelayResult.Success)
        val partial = success.withResult(inbox, RelayResult.Timeout)
        val failed =
            pending
                .withResult(outboxA, RelayResult.Error("no"))
                .withResult(outboxB, RelayResult.Timeout)
                .withResult(inbox, RelayResult.Timeout)

        assertEquals(BroadcastStatus.IN_PROGRESS, pending.status)
        assertEquals(BroadcastStatus.SUCCESS, success.status)
        assertEquals(BroadcastStatus.PARTIAL, partial.status)
        assertEquals(BroadcastStatus.FAILED, failed.status)
    }

    @Test
    fun statusIsInProgressWhileARelayIsRetrying() {
        val b =
            broadcast()
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Success)
                .withResult(inbox, RelayResult.Retrying)

        assertEquals(BroadcastStatus.IN_PROGRESS, b.status, "a retry answer is not an answer")
    }

    @Test
    fun needsAttentionAsSoonAsAnOutboxRelayFails() {
        val rejected = broadcast().withResult(outboxB, RelayResult.Error("blocked"))
        val timedOut = broadcast().withResult(outboxB, RelayResult.Timeout)

        assertTrue(rejected.needsAttention, "the other relays are still pending, but the failure is known")
        assertEquals(BroadcastStatus.IN_PROGRESS, rejected.status)
        assertTrue(timedOut.needsAttention)
    }

    @Test
    fun noAttentionWhileTheOutboxIsFineOrRetrying() {
        val sending = broadcast().withResult(outboxA, RelayResult.Success)
        val inboxFailed =
            sending
                .withResult(outboxB, RelayResult.Success)
                .withResult(inbox, RelayResult.Error("no"))
        val retrying =
            sending
                .withResult(outboxB, RelayResult.Error("blocked"))
                .withResult(outboxB, RelayResult.Retrying)

        assertFalse(sending.needsAttention)
        assertFalse(inboxFailed.needsAttention, "only outbox failures matter")
        assertFalse(retrying.needsAttention)
    }

    @Test
    fun withoutOutboxTargetsAttentionMeansNothingAccepted() {
        val pending = broadcast(outbox = emptySet()).withResult(inbox, RelayResult.Error("no"))
        val allFailed = pending.withResult(outboxA, RelayResult.Timeout).withResult(outboxB, RelayResult.Timeout)
        val oneAccepted = pending.withResult(outboxA, RelayResult.Timeout).withResult(outboxB, RelayResult.Success)

        assertFalse(pending.needsAttention, "other relays may still accept it")
        assertTrue(allFailed.needsAttention)
        assertFalse(oneAccepted.needsAttention)
    }

    @Test
    fun hidingRemovesWhatHasAResultAndHidesWhatIsStillSending() {
        val sending = broadcast(id = "sending").withResult(outboxA, RelayResult.Success)
        val out = broadcast(id = "out").withResult(outboxA, RelayResult.Success).withResult(outboxB, RelayResult.Success)
        val failed = broadcast(id = "failed").withResult(outboxB, RelayResult.Error("blocked"))
        val untouched = broadcast(id = "untouched")

        val after = listOf(sending, out, failed, untouched).hiding(setOf("sending", "out", "failed"))

        assertEquals(listOf("sending", "untouched"), after.map { it.id })
        assertTrue(after.first { it.id == "sending" }.hidden)
        assertFalse(after.first { it.id == "untouched" }.hidden)
    }

    @Test
    fun hidingWithoutOutboxTargetsDropsResultsAndHidesWhatIsPending() {
        val pending = broadcast(id = "pending", outbox = emptySet())
        val accepted = broadcast(id = "accepted", outbox = emptySet()).withResult(inbox, RelayResult.Success)
        val allFailed =
            broadcast(id = "allFailed", outbox = emptySet())
                .withResult(outboxA, RelayResult.Timeout)
                .withResult(outboxB, RelayResult.Timeout)
                .withResult(inbox, RelayResult.Error("no"))

        val after = listOf(pending, accepted, allFailed).hiding(setOf("pending", "accepted", "allFailed"))

        assertEquals(listOf("pending"), after.map { it.id })
        assertTrue(after.single().hidden)
    }

    @Test
    fun aHiddenBroadcastStaysHiddenWhenTheTrackerWritesItsCopyBack() {
        // the tracker keeps its own copy, which never saw the hide.
        val trackersCopy = broadcast()
        val list = listOf(trackersCopy).hiding(setOf("b1"))

        val after = list.replacing(trackersCopy.withResult(inbox, RelayResult.Success))

        assertTrue(after.single().hidden)
    }

    @Test
    fun aHiddenBroadcastComesBackWhenItNeedsAttention() {
        val trackersCopy = broadcast()
        val list = listOf(trackersCopy).hiding(setOf("b1"))

        val after = list.replacing(trackersCopy.withResult(outboxB, RelayResult.Timeout))

        assertFalse(after.single().hidden)
        assertTrue(after.single().needsAttention)
    }

    @Test
    fun aHiddenBroadcastIsDroppedOnceOut() {
        val trackersCopy = broadcast()
        val list = listOf(trackersCopy).hiding(setOf("b1"))

        val after =
            list.replacing(
                trackersCopy.withResult(outboxA, RelayResult.Success).withResult(outboxB, RelayResult.Success),
            )

        assertTrue(after.isEmpty(), "slower relays no longer matter")
    }

    @Test
    fun replacingKeepsVisibleBroadcastsAndIgnoresUnknownOnes() {
        val visible = broadcast(id = "visible")
        val out = visible.withResult(outboxA, RelayResult.Success).withResult(outboxB, RelayResult.Success)

        assertEquals(listOf(out), listOf(visible).replacing(out), "a visible broadcast that is out stays for the banner")
        assertEquals(listOf(visible), listOf(visible).replacing(broadcast(id = "gone")))
    }

    @Test
    fun autoDismissesOnlyWhenEveryBroadcastIsOut() {
        val out =
            broadcast(id = "out")
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Success)
        val sending = broadcast(id = "sending").withResult(outboxA, RelayResult.Success)
        val failed =
            broadcast(id = "failed")
                .withResult(outboxA, RelayResult.Success)
                .withResult(outboxB, RelayResult.Error("blocked"))
                .withResult(inbox, RelayResult.Success)

        assertTrue(listOf(out).canAutoDismiss())
        assertFalse(listOf(out, sending).canAutoDismiss())
        assertFalse(listOf(out, failed).canAutoDismiss(), "a failed outbox relay keeps the banner up")
        assertFalse(emptyList<BroadcastEvent>().canAutoDismiss())
    }
}
