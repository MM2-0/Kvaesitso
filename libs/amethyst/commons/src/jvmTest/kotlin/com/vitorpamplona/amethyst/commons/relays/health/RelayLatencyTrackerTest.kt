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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayLatencyTrackerTest {
    private val relay = NormalizedRelayUrl("wss://relay.test/")
    private val relayB = NormalizedRelayUrl("wss://other.test/")

    private fun fakeEvent(idHex: String): Event =
        Event(
            id = idHex.padEnd(64, '0'),
            pubKey = "pub".padEnd(64, '0'),
            createdAt = 0L,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "sig".padEnd(128, '0'),
        )

    private fun req(subId: String) = ReqCmd(subId, listOf(Filter()))

    @Test
    fun okAckPairsByEventIdAndPushesDelta() {
        val tracker = RelayLatencyTracker()
        val event = fakeEvent("a")
        tracker.recordSent(relay, EventCmd(event), success = true, nowMs = 1_000)
        tracker.recordIncoming(relay, OkMessage(event.id, true, ""), nowMs = 1_250)

        val snap = tracker.snapshot()[relay]
        assertNotNull(snap)
        val ok = snap.samples[LatencyMetric.OK_ACK]
        assertNotNull(ok)
        assertEquals(250, ok.p50Ms)
        assertEquals(1, ok.count)
    }

    @Test
    fun eoseAndFirstResultPairBySubId() {
        val tracker = RelayLatencyTracker()
        tracker.recordSent(relay, req("s1"), success = true, nowMs = 1_000)
        tracker.recordIncoming(relay, EventMessage("s1", fakeEvent("e")), nowMs = 1_100)
        tracker.recordIncoming(relay, EventMessage("s1", fakeEvent("f")), nowMs = 1_400) // ignored
        tracker.recordIncoming(relay, EoseMessage("s1"), nowMs = 1_500)

        val snap = tracker.snapshot()[relay]!!
        assertEquals(100, snap.samples[LatencyMetric.FIRST_RESULT]!!.p50Ms)
        assertEquals(1, snap.samples[LatencyMetric.FIRST_RESULT]!!.count)
        assertEquals(500, snap.samples[LatencyMetric.EOSE]!!.p50Ms)
        assertEquals(1, snap.samples[LatencyMetric.EOSE]!!.count)
    }

    @Test
    fun unsuccessfulSendDoesNotInsertPending() {
        val tracker = RelayLatencyTracker()
        val event = fakeEvent("a")
        tracker.recordSent(relay, EventCmd(event), success = false, nowMs = 1_000)
        tracker.recordIncoming(relay, OkMessage(event.id, true, ""), nowMs = 1_250)
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun closeCmdDropsPendingReq() {
        val tracker = RelayLatencyTracker()
        tracker.recordSent(relay, req("s1"), success = true, nowMs = 1_000)
        tracker.recordSent(relay, CloseCmd("s1"), success = true, nowMs = 1_100)
        // Now a stale EOSE for s1 arrives — should be ignored.
        tracker.recordIncoming(relay, EoseMessage("s1"), nowMs = 1_500)
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun closedMessageDropsPendingReqAndDoesNotSample() {
        val tracker = RelayLatencyTracker()
        tracker.recordSent(relay, req("s1"), success = true, nowMs = 1_000)
        tracker.recordIncoming(relay, ClosedMessage("s1", "auth-required"), nowMs = 1_020)
        // Subsequent stale EOSE is a no-op.
        tracker.recordIncoming(relay, EoseMessage("s1"), nowMs = 1_500)
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun authRetryOverwritesPendingTimestamp() {
        val tracker = RelayLatencyTracker()
        val event = fakeEvent("a")
        tracker.recordSent(relay, EventCmd(event), success = true, nowMs = 1_000)
        // AUTH happens, then quartz re-sends the same EventCmd.
        tracker.recordSent(relay, EventCmd(event), success = true, nowMs = 6_000)
        tracker.recordIncoming(relay, OkMessage(event.id, true, ""), nowMs = 6_200)

        val snap = tracker.snapshot()[relay]!!
        val ok = snap.samples[LatencyMetric.OK_ACK]!!
        // Sample reflects the retry leg (200 ms), not the AUTH round-trip (5.2 s).
        assertEquals(200, ok.p50Ms)
    }

    @Test
    fun disconnectDropsAllPendingForRelay() {
        val tracker = RelayLatencyTracker()
        val event = fakeEvent("a")
        tracker.recordSent(relay, EventCmd(event), success = true, nowMs = 1_000)
        tracker.recordSent(relay, req("s1"), success = true, nowMs = 1_000)
        tracker.recordDisconnect(relay)
        // Late OK / EOSE no longer pair.
        tracker.recordIncoming(relay, OkMessage(event.id, true, ""), nowMs = 1_200)
        tracker.recordIncoming(relay, EoseMessage("s1"), nowMs = 1_300)
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun sweepRecordsTtlValueAsSample() {
        val tracker = RelayLatencyTracker(okTtlMs = 60_000L, reqTtlMs = 300_000L)
        val event = fakeEvent("a")
        tracker.recordSent(relay, EventCmd(event), success = true, nowMs = 0)
        tracker.recordSent(relay, req("s1"), success = true, nowMs = 0)

        tracker.sweep(nowMs = 60_001L)

        val snap = tracker.snapshot()[relay]!!
        // OK_ACK: TTL expired → recorded as 60_000 ms.
        assertEquals(60_000, snap.samples[LatencyMetric.OK_ACK]!!.p50Ms)
        // EOSE: REQ TTL is 5min, not yet expired → no sample.
        assertNull(snap.samples[LatencyMetric.EOSE])

        tracker.sweep(nowMs = 300_001L)
        val snap2 = tracker.snapshot()[relay]!!
        // EOSE + FIRST_RESULT TTL recorded.
        assertEquals(300_000, snap2.samples[LatencyMetric.EOSE]!!.p50Ms)
        assertEquals(300_000, snap2.samples[LatencyMetric.FIRST_RESULT]!!.p50Ms)
    }

    @Test
    fun firstResultTtlOnlyRecordedWhenNothingWasSeen() {
        val tracker = RelayLatencyTracker(reqTtlMs = 300_000L)
        tracker.recordSent(relay, req("s1"), success = true, nowMs = 0)
        tracker.recordIncoming(relay, EventMessage("s1", fakeEvent("e")), nowMs = 100)
        // FIRST_RESULT was already sampled. EOSE never arrived. Sweep should record only an
        // EOSE TTL sample, not a FIRST_RESULT TTL sample.
        tracker.sweep(nowMs = 300_001L)

        val snap = tracker.snapshot()[relay]!!
        assertEquals(100, snap.samples[LatencyMetric.FIRST_RESULT]!!.p50Ms)
        assertEquals(300_000, snap.samples[LatencyMetric.EOSE]!!.p50Ms)
    }

    @Test
    fun pingRecordsDirectly() {
        val tracker = RelayLatencyTracker()
        tracker.recordPing(relay, 42)
        tracker.recordPing(relay, 58)
        val snap = tracker.snapshot()[relay]!!
        assertEquals(42, snap.samples[LatencyMetric.PING]!!.p50Ms) // lower-middle of 2
        assertEquals(2, snap.samples[LatencyMetric.PING]!!.count)
    }

    @Test
    fun perRelayIsolation() {
        val tracker = RelayLatencyTracker()
        val event = fakeEvent("a")
        tracker.recordSent(relay, EventCmd(event), success = true, nowMs = 1_000)
        // Same event_id arriving on a *different* relay — should not pair.
        tracker.recordIncoming(relayB, OkMessage(event.id, true, ""), nowMs = 1_200)
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun pendingMapSizeCappedAtMaxPerRelay() {
        val tracker = RelayLatencyTracker(maxPendingPerRelay = 4)
        repeat(10) { i ->
            tracker.recordSent(relay, req("s$i"), success = true, nowMs = i.toLong())
        }
        // Sweep with low enough now — none of these expired. Yet the pending set is capped.
        // We can't directly observe internal state, but a stale-sub EOSE for the *first*
        // sub-id should now miss (oldest dropped).
        tracker.recordIncoming(relay, EoseMessage("s0"), nowMs = 100)
        assertTrue(tracker.snapshot().isEmpty())

        // A still-pending later sub-id pairs correctly.
        tracker.recordIncoming(relay, EoseMessage("s9"), nowMs = 200)
        val snap = tracker.snapshot()[relay]
        assertNotNull(snap)
    }

    @Test
    fun restoreSamplesRoundTripsThroughPersistenceShape() {
        val tracker = RelayLatencyTracker()
        tracker.restoreSamples(
            mapOf(
                relay to
                    mapOf(
                        LatencyMetric.OK_ACK to intArrayOf(100, 200, 300),
                        LatencyMetric.EOSE to intArrayOf(50, 60),
                    ),
            ),
        )
        val snap = tracker.snapshot()[relay]!!
        assertEquals(200, snap.samples[LatencyMetric.OK_ACK]!!.p50Ms)
        assertEquals(3, snap.samples[LatencyMetric.OK_ACK]!!.count)
        assertEquals(50, snap.samples[LatencyMetric.EOSE]!!.p50Ms)
        assertEquals(2, snap.samples[LatencyMetric.EOSE]!!.count)

        // Persistence shape symmetric.
        val back = tracker.samplesForPersistence()[relay]!!
        assertEquals(2, back.size)
    }
}
