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
package com.vitorpamplona.amethyst.commons.nipACWebRtcCalls

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CallManager State Machine Tests
 *
 * Tests the NIP-AC call state machine implementation against all specified
 * transitions from the spec:
 *
 * ```
 *   Idle ──> Offering ──> Connecting ──> Connected ──> Ended ──> Idle
 *   Idle ──> IncomingCall ──> Connecting ──> Connected ──> Ended ──> Idle
 *   Idle ──> IncomingCall ──> Ended (reject)
 *   Idle ──> Offering ──> Ended (rejected / timeout)
 * ```
 *
 * These tests construct events directly (without signing) to verify
 * state machine logic independently of cryptographic operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerTest {
    // Real crypto identities — all tests use actual KeyPairs so that
    // signer.pubKey matches the pubkey used in constructed events.
    private val aliceSigner = NostrSignerInternal(KeyPair())
    private val bobSigner = NostrSignerInternal(KeyPair())
    private val carolSigner = NostrSignerInternal(KeyPair())

    private val alice = aliceSigner.pubKey
    private val bob = bobSigner.pubKey
    private val carol = carolSigner.pubKey

    private val callId = "550e8400-e29b-41d4-a716-446655440000"
    private val callId2 = "660e8400-e29b-41d4-a716-446655440001"
    private val sdpOffer = "v=0\r\no=- 4611731400430051336 2 IN IP4 127.0.0.1\r\n..."
    private val sdpAnswer = "v=0\r\no=- 4611731400430051337 2 IN IP4 127.0.0.1\r\n..."

    private val signers = mapOf(alice to aliceSigner, bob to bobSigner, carol to carolSigner)

    /**
     * Creates a CallManager backed by a real NostrSignerInternal. Tests the full
     * pipeline: CallManager → WebRtcCallFactory → sign → gift wrap → publish.
     */
    private fun TestScope.createManager(
        localPubKey: HexKey = bob,
        followedKeys: Set<HexKey> = setOf(alice, carol),
        isCallsEnabled: () -> Boolean = { true },
        signer: NostrSigner = signers[localPubKey] ?: error("Unknown test identity: $localPubKey"),
    ): Pair<CallManager, MutableList<EphemeralGiftWrapEvent>> {
        val published = mutableListOf<EphemeralGiftWrapEvent>()
        val manager =
            CallManager(
                signer = signer,
                scope = this,
                isFollowing = { it in followedKeys },
                publishEvent = { published.add(it) },
                isCallsEnabled = isCallsEnabled,
            )
        return manager to published
    }

    /**
     * Starts collecting [CallManager.sessionEvents] into a list.
     * Returns (events, job) — cancel the job when done.
     */
    private fun TestScope.collectSessionEvents(manager: CallManager): Pair<MutableList<CallSessionEvent>, Job> {
        val events = mutableListOf<CallSessionEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.sessionEvents.collect { events.add(it) } }
        return events to job
    }

    private fun TestScope.collectRenegotiationEvents(manager: CallManager): Pair<MutableList<CallRenegotiateEvent>, Job> {
        val events = mutableListOf<CallRenegotiateEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { manager.renegotiationEvents.collect { events.add(it) } }
        return events to job
    }

    // ---- Event construction helpers ----
    // These use the real event builders from quartz so that tag structures
    // stay in sync with the production code.  The builder returns an
    // EventTemplate (no pubKey/id/sig); we wrap the template fields into
    // a concrete event instance with a test pubKey and dummy id/sig.

    private var eventCounter = 0

    private fun makeOffer(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        callType: CallType = CallType.VOICE,
        sdp: String = sdpOffer,
        createdAt: Long = TimeUtils.now(),
    ): CallOfferEvent {
        val t = CallOfferEvent.build(sdp, to, callId, callType)
        return CallOfferEvent("offer${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    private fun makeGroupOffer(
        from: HexKey,
        members: Set<HexKey>,
        callId: String = this.callId,
        callType: CallType = CallType.VOICE,
        sdp: String = sdpOffer,
        createdAt: Long = TimeUtils.now(),
    ): CallOfferEvent {
        val t = CallOfferEvent.build(sdp, members, callId, callType)
        return CallOfferEvent("offer${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    private fun makeAnswer(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        sdp: String = sdpAnswer,
        createdAt: Long = TimeUtils.now(),
    ): CallAnswerEvent {
        val t = CallAnswerEvent.build(sdp, to, callId)
        return CallAnswerEvent("answer${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    private fun makeGroupAnswer(
        from: HexKey,
        members: Set<HexKey>,
        callId: String = this.callId,
        sdp: String = sdpAnswer,
        createdAt: Long = TimeUtils.now(),
    ): CallAnswerEvent {
        val t = CallAnswerEvent.build(sdp, members, callId)
        return CallAnswerEvent("answer${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    private fun makeHangup(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        reason: String = "",
        createdAt: Long = TimeUtils.now(),
    ): CallHangupEvent {
        val t = CallHangupEvent.build(to, callId, reason)
        return CallHangupEvent("hangup${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    private fun makeReject(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        reason: String = "",
        createdAt: Long = TimeUtils.now(),
    ): CallRejectEvent {
        val t = CallRejectEvent.build(to, callId, reason)
        return CallRejectEvent("reject${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    private fun makeIceCandidate(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        createdAt: Long = TimeUtils.now(),
    ): CallIceCandidateEvent {
        val json = CallIceCandidateEvent.serializeCandidate("candidate:1", "0", 0)
        val t = CallIceCandidateEvent.build(json, to, callId)
        return CallIceCandidateEvent("ice${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    private fun makeRenegotiate(
        from: HexKey,
        to: HexKey,
        callId: String = this.callId,
        sdp: String = sdpOffer,
        createdAt: Long = TimeUtils.now(),
    ): CallRenegotiateEvent {
        val t = CallRenegotiateEvent.build(sdp, to, callId)
        return CallRenegotiateEvent("renego${eventCounter++}", from, createdAt, t.tags, t.content, "sig")
    }

    // ========================================================================
    // 1. P2P Call: Idle → IncomingCall → Connecting → Connected → Ended
    // ========================================================================

    @Test
    fun incomingCallFromFollowedUserTransitionsToIncomingCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)

            val state = manager.state.value
            assertIs<CallState.IncomingCall>(state)
            assertEquals(callId, state.callId)
            assertEquals(alice, state.callerPubKey)
            assertEquals(CallType.VOICE, state.callType)
            assertEquals(sdpOffer, state.sdpOffer)
        }

    @Test
    fun incomingCallFromNonFollowedIsIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = emptySet())

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)

            assertIs<CallState.Idle>(manager.state.value)
        }

    @Test
    fun acceptingCallTransitionsToConnecting() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.acceptCall(sdpAnswer)

            val state = manager.state.value
            assertIs<CallState.Connecting>(state)
            assertEquals(callId, state.callId)
        }

    @Test
    fun peerConnectedTransitionsConnectingToConnected() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            manager.onPeerConnected()

            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertEquals(callId, state.callId)
        }

    @Test
    fun peerHangupEndsConnectedCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            val hangup = makeHangup(from = alice, to = bob)
            manager.onSignalingEvent(hangup)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.PEER_HANGUP, state.reason)
        }

    @Test
    fun endedStateAutoResetsToIdle() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            manager.onSignalingEvent(makeHangup(from = alice, to = bob))

            assertIs<CallState.Ended>(manager.state.value)

            // Advance past the ENDED_DISPLAY_MS (2 seconds)
            advanceUntilIdle()

            assertIs<CallState.Idle>(manager.state.value)
        }

    // ========================================================================
    // 2. Caller Side: Idle → Offering → Connecting → Connected
    // ========================================================================

    @Test
    fun initiateCallTransitionsToOffering() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice)

            manager.initiateCall(bob, CallType.VIDEO, callId, sdpOffer)

            val state = manager.state.value
            assertIs<CallState.Offering>(state)
            assertEquals(callId, state.callId)
            assertEquals(setOf(bob), state.peerPubKeys)
            assertEquals(CallType.VIDEO, state.callType)
        }

    @Test
    fun receivingAnswerTransitionsOfferingToConnecting() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            val (events, job) = collectSessionEvents(manager)

            val answer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(answer)

            val state = manager.state.value
            assertIs<CallState.Connecting>(state)
            assertTrue(events.any { it is CallSessionEvent.AnswerReceived }, "AnswerReceived event should be emitted")
            job.cancel()
        }

    // ========================================================================
    // 3. Call Rejection
    // ========================================================================

    @Test
    fun rejectingIncomingCallTransitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.rejectCall()

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.REJECTED, state.reason)
        }

    @Test
    fun receivingRejectEndsOfferingCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            val reject = makeReject(from = bob, to = alice)
            manager.onSignalingEvent(reject)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.PEER_REJECTED, state.reason)
        }

    // ========================================================================
    // 4. Busy Auto-Reject
    // ========================================================================

    @Test
    fun incomingCallWhileInActiveCallAutoRejectsBusy() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob)

            // First call: accepted and connected
            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            // Second call arrives while connected
            val secondOffer = makeOffer(from = carol, to = bob, callId = callId2)
            manager.onSignalingEvent(secondOffer)
            advanceUntilIdle()

            // Should still be in the original call
            assertIs<CallState.Connected>(manager.state.value)

            // Should have published a reject (busy)
            assertTrue(published.isNotEmpty(), "Should publish a busy reject")
        }

    // ========================================================================
    // 5. Staleness: Old Events Discarded
    // ========================================================================

    @Test
    fun staleEventsAreDiscarded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            // Event from 30 seconds ago (beyond 20s threshold)
            val staleOffer = makeOffer(from = alice, to = bob, createdAt = TimeUtils.now() - 30)
            manager.onSignalingEvent(staleOffer)

            assertIs<CallState.Idle>(manager.state.value, "Stale events (>20s old) MUST be discarded")
        }

    @Test
    fun freshEventsAreProcessed() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            // Event from 5 seconds ago (within 20s threshold)
            val freshOffer = makeOffer(from = alice, to = bob, createdAt = TimeUtils.now() - 5)
            manager.onSignalingEvent(freshOffer)

            assertIs<CallState.IncomingCall>(manager.state.value, "Fresh events (<20s old) should be processed")
        }

    // ========================================================================
    // 6. Deduplication
    // ========================================================================

    @Test
    fun duplicateEventsAreIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Re-deliver the same offer (same event ID)
            manager.onSignalingEvent(offer)

            // Should still be Connected, not re-processing the offer
            assertIs<CallState.Connected>(manager.state.value)
        }

    // ========================================================================
    // 7. Self-Event Filtering
    // ========================================================================

    @Test
    fun selfIceCandidatesAreAlwaysIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)

            val (events, job) = collectSessionEvents(manager)

            // ICE candidate from self (echoed back by relay)
            val selfIce = makeIceCandidate(from = bob, to = alice)
            manager.onSignalingEvent(selfIce)

            assertTrue(events.none { it is CallSessionEvent.IceCandidateReceived }, "Self ICE candidates MUST be ignored")
            job.cancel()
        }

    @Test
    fun selfHangupIsAlwaysIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Self hangup echo from relay
            val selfHangup = makeHangup(from = bob, to = alice)
            manager.onSignalingEvent(selfHangup)

            // Should still be Connected (self hangup is handled locally)
            assertIs<CallState.Connected>(manager.state.value)
        }

    @Test
    fun selfAnswerInIncomingCallMeansAnsweredElsewhere() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Self-answer from another device
            val selfAnswer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(selfAnswer)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.ANSWERED_ELSEWHERE, state.reason)
        }

    @Test
    fun selfAnswerInOfferingStateIsIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            // Self-answer echo — should be ignored in Offering state
            val selfAnswer = makeAnswer(from = alice, to = bob)
            manager.onSignalingEvent(selfAnswer)

            assertIs<CallState.Offering>(manager.state.value, "Self-answer in Offering should be ignored")
        }

    // ========================================================================
    // 8. ICE Candidate Forwarding
    // ========================================================================

    @Test
    fun iceCandidatesAreForwardedViaCallback() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)

            val (events, job) = collectSessionEvents(manager)

            val ice = makeIceCandidate(from = alice, to = bob)
            manager.onSignalingEvent(ice)

            val iceEvent = events.filterIsInstance<CallSessionEvent.IceCandidateReceived>().firstOrNull()
            assertNotNull(iceEvent, "ICE candidate should be emitted via sessionEvents")
            job.cancel()
        }

    // ========================================================================
    // 9. Mid-Call Renegotiation (Voice → Video Switch)
    // ========================================================================

    @Test
    fun renegotiationInConnectedStateIsForwarded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            val (renegoEvents, renegoJob) = collectRenegotiationEvents(manager)

            val renego = makeRenegotiate(from = alice, to = bob)
            manager.onSignalingEvent(renego)

            assertNotNull(renegoEvents.firstOrNull(), "Renegotiation should be forwarded in Connected state")
            renegoJob.cancel()
        }

    @Test
    fun renegotiationInConnectingStateIsForwarded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            val (renegoEvents, renegoJob) = collectRenegotiationEvents(manager)

            val renego = makeRenegotiate(from = alice, to = bob)
            manager.onSignalingEvent(renego)

            assertNotNull(renegoEvents.firstOrNull())
            renegoJob.cancel()
        }

    @Test
    fun renegotiationInIdleStateIsIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val (renegoEvents, renegoJob) = collectRenegotiationEvents(manager)

            val renego = makeRenegotiate(from = alice, to = bob)
            manager.onSignalingEvent(renego)

            assertIs<CallState.Idle>(manager.state.value)
            assertEquals(0, renegoEvents.size, "Renegotiation in Idle state should be ignored")
            renegoJob.cancel()
        }

    @Test
    fun renegotiationWrongCallIdIsIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()

            val (renegoEvents, renegoJob) = collectRenegotiationEvents(manager)

            val renego = makeRenegotiate(from = alice, to = bob, callId = "wrong-call-id")
            manager.onSignalingEvent(renego)

            assertEquals(0, renegoEvents.size, "Renegotiation for wrong call-id should be ignored")
            renegoJob.cancel()
        }

    // ========================================================================
    // 10. Hangup from Any Active State
    // ========================================================================

    @Test
    fun hangupFromOfferingTransitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            manager.hangup()

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.HANGUP, state.reason)
        }

    @Test
    fun hangupFromConnectingTransitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            manager.hangup()

            assertIs<CallState.Ended>(manager.state.value)
        }

    @Test
    fun hangupFromConnectedTransitionsToEnded() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()

            manager.hangup()

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.HANGUP, state.reason)
        }

    @Test
    fun hangupFromIdleIsNoop() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob)

            manager.hangup()

            assertIs<CallState.Idle>(manager.state.value)
            assertTrue(published.isEmpty())
        }

    // ========================================================================
    // 11. Group Call: Multiple Peers
    // ========================================================================

    @Test
    fun groupCallOfferDetectsMultipleMembers() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val offer = makeGroupOffer(from = alice, members = setOf(bob, carol))
            manager.onSignalingEvent(offer)

            val state = manager.state.value
            assertIs<CallState.IncomingCall>(state)
            assertTrue(state.groupMembers.containsAll(setOf(alice, bob, carol)))
        }

    @Test
    fun groupCallPeerRejectRemovesFromGroup() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)
            assertIs<CallState.Offering>(manager.state.value)

            // Bob rejects
            val reject = makeReject(from = bob, to = alice)
            manager.onSignalingEvent(reject)

            val state = manager.state.value
            assertIs<CallState.Offering>(state)
            assertTrue(bob !in state.peerPubKeys, "Rejected peer should be removed")
            assertTrue(carol in state.peerPubKeys, "Remaining peers should stay")
        }

    @Test
    fun groupCallAllPeersRejectEndsCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            manager.onSignalingEvent(makeReject(from = bob, to = alice))
            manager.onSignalingEvent(makeReject(from = carol, to = alice))

            assertIs<CallState.Ended>(manager.state.value)
        }

    @Test
    fun groupCallPartialDisconnectContinuesWithRemainingPeers() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            // Bob answers first
            val bobAnswer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(bobAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Carol answers
            val carolAnswer = makeAnswer(from = carol, to = alice)
            manager.onSignalingEvent(carolAnswer)

            // Bob hangs up
            manager.onSignalingEvent(makeHangup(from = bob, to = alice))

            // Call should continue with carol
            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertTrue(bob !in state.peerPubKeys, "Bob should be removed")
        }

    @Test
    fun groupCallLastPeerLeavesEndsCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            // Both answer
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            manager.onPeerConnected()

            val carolAnswer = makeAnswer(from = carol, to = alice)
            manager.onSignalingEvent(carolAnswer)

            // Both leave
            manager.onSignalingEvent(makeHangup(from = bob, to = alice))
            manager.onSignalingEvent(makeHangup(from = carol, to = alice))

            assertIs<CallState.Ended>(manager.state.value)
        }

    // ========================================================================
    // 12. Group Call: Callee-to-Callee Mesh Discovery
    // ========================================================================

    @Test
    fun groupCallDiscoversPeerWhileRinging() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            // Alice calls bob and carol
            val offer = makeGroupOffer(from = alice, members = setOf(bob, carol))
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Carol answers (bob sees this while ringing)
            val carolAnswer = makeGroupAnswer(from = carol, members = setOf(alice, bob, carol))
            manager.onSignalingEvent(carolAnswer)

            // Bob still ringing
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Track mesh setup events
            val (events, job) = collectSessionEvents(manager)

            // Bob accepts
            manager.acceptCall(sdpAnswer)

            // Should trigger callee-to-callee mesh setup with carol
            val newPeers = events.filterIsInstance<CallSessionEvent.NewPeerInGroupCall>().map { it.peerPubKey }
            assertTrue(carol in newPeers, "Should discover carol for mesh setup after accepting")
            job.cancel()
        }

    // ========================================================================
    // 13. Mid-Call Offer (Callee-to-Callee)
    // ========================================================================

    @Test
    fun midCallOfferSameCallIdIsForwardedAsMidCallOffer() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            val (events, job) = collectSessionEvents(manager)

            // Carol sends a mid-call offer (callee-to-callee mesh)
            val carolOffer = makeOffer(from = carol, to = bob, callId = callId, sdp = "carol-sdp")
            manager.onSignalingEvent(carolOffer)

            val midCallEvent = events.filterIsInstance<CallSessionEvent.MidCallOfferReceived>().firstOrNull()
            assertNotNull(midCallEvent)
            assertEquals(carol, midCallEvent.peerPubKey)
            assertEquals("carol-sdp", midCallEvent.sdpOffer)
            job.cancel()
        }

    // ========================================================================
    // 14. Call-ID Mismatch Ignored
    // ========================================================================

    @Test
    fun answerWrongCallIdIsIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            val wrongAnswer = makeAnswer(from = bob, to = alice, callId = "wrong-id")
            manager.onSignalingEvent(wrongAnswer)

            assertIs<CallState.Offering>(manager.state.value, "Answer with wrong call-id should be ignored")
        }

    @Test
    fun hangupWrongCallIdIsIgnored() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            val wrongHangup = makeHangup(from = alice, to = bob, callId = "wrong-id")
            manager.onSignalingEvent(wrongHangup)

            assertIs<CallState.Connected>(manager.state.value, "Hangup with wrong call-id should be ignored")
        }

    // ========================================================================
    // 15. Peer Left Callback
    // ========================================================================

    @Test
    fun peerLeftCallbackFiresOnHangup() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            manager.onPeerConnected()
            manager.onSignalingEvent(makeAnswer(from = carol, to = alice))

            val (events, job) = collectSessionEvents(manager)

            manager.onSignalingEvent(makeHangup(from = bob, to = alice))

            val leftPeers = events.filterIsInstance<CallSessionEvent.PeerLeft>().map { it.peerPubKey }
            assertTrue(bob in leftPeers, "PeerLeft should be emitted when a peer hangs up")
            job.cancel()
        }

    // ========================================================================
    // 16. Reset
    // ========================================================================

    @Test
    fun resetReturnsToIdle() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            manager.reset()

            assertIs<CallState.Idle>(manager.state.value)
        }

    // ========================================================================
    // 17. Video Call Type Preserved
    // ========================================================================

    @Test
    fun videoCallTypePreservedThroughStates() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            val videoOffer = makeOffer(from = alice, to = bob, callType = CallType.VIDEO)
            manager.onSignalingEvent(videoOffer)

            val incoming = manager.state.value
            assertIs<CallState.IncomingCall>(incoming)
            assertEquals(CallType.VIDEO, incoming.callType)

            manager.acceptCall(sdpAnswer)
            val connecting = manager.state.value
            assertIs<CallState.Connecting>(connecting)
            assertEquals(CallType.VIDEO, connecting.callType)

            manager.onPeerConnected()
            val connected = manager.state.value
            assertIs<CallState.Connected>(connected)
            assertEquals(CallType.VIDEO, connected.callType)
        }

    // ========================================================================
    // 18. Caller Cancels (Hangup While Ringing)
    // ========================================================================

    @Test
    fun callerHangupWhileRingingEndsIncomingCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Caller cancels
            manager.onSignalingEvent(makeHangup(from = alice, to = bob))

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.PEER_HANGUP, state.reason)
        }
    // ========================================================================
    // INTERFACE-LEVEL TESTS (Real Signers, Full Pipeline)
    // ========================================================================
    // These tests use real NostrSignerInternal with actual crypto keys to verify
    // the full pipeline: CallManager → WebRtcCallFactory → sign → gift wrap → publish

    @Test
    fun interfaceInitiateCallPublishesGiftWrappedOffer() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VIDEO, callId, sdpOffer)

            assertIs<CallState.Offering>(manager.state.value)
            assertEquals(1, published.size, "Should publish exactly one gift-wrapped offer")
            assertEquals(EphemeralGiftWrapEvent.KIND, published[0].kind, "Wrap must be kind 21059")
        }

    @Test
    fun interfaceAcceptCallPublishesGiftWrappedAnswer() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            // Simulate incoming offer from alice
            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)
            published.clear()

            manager.acceptCall(sdpAnswer)

            assertIs<CallState.Connecting>(manager.state.value)
            // Should publish answer wrapped for all recipients (alice + self for multi-device)
            assertTrue(published.isNotEmpty(), "Should publish gift-wrapped answer(s)")
            published.forEach { wrap ->
                assertEquals(EphemeralGiftWrapEvent.KIND, wrap.kind)
            }
        }

    @Test
    fun interfaceRejectCallPublishesGiftWrappedReject() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(manager.state.value)
            published.clear()

            manager.rejectCall()

            assertIs<CallState.Ended>(manager.state.value)
            assertTrue(published.isNotEmpty(), "Should publish gift-wrapped reject(s)")
        }

    @Test
    fun interfaceHangupPublishesGiftWrappedHangup() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            manager.hangup()

            assertIs<CallState.Ended>(manager.state.value)
            assertTrue(published.isNotEmpty(), "Should publish gift-wrapped hangup(s)")
        }

    @Test
    fun interfaceSendRenegotiationPublishesGiftWrappedRenegotiate() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            val newSdp = "v=0\r\nnew-sdp-for-video"
            manager.sendRenegotiation(newSdp, alice)

            assertEquals(1, published.size, "Should publish exactly one gift-wrapped renegotiate")
            assertEquals(EphemeralGiftWrapEvent.KIND, published[0].kind)
        }

    @Test
    fun interfaceSendRenegotiationAnswerPublishesGiftWrappedAnswer() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            published.clear()

            manager.sendRenegotiationAnswer("renegotiation-answer-sdp", alice)

            assertEquals(1, published.size)
            assertEquals(EphemeralGiftWrapEvent.KIND, published[0].kind)
        }

    @Test
    fun interfaceBusyAutoRejectPublishesRejectEvent() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            // Accept first call
            val offer = makeOffer(from = alice, to = bob)
            manager.onSignalingEvent(offer)
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            published.clear()

            // Second call from carol while in active call
            val secondOffer = makeOffer(from = carol, to = bob, callId = callId2)
            manager.onSignalingEvent(secondOffer)
            advanceUntilIdle()

            // Should remain in original call
            assertIs<CallState.Connected>(manager.state.value)
            // Should have published auto-reject
            assertTrue(published.isNotEmpty(), "Should publish busy auto-reject")
        }

    @Test
    fun interfaceGroupCallPublishesPerPeerOffers() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)

            // Publish per-peer offers
            manager.publishOfferToPeer(bob, setOf(bob, carol), CallType.VOICE, callId, sdpOffer)
            manager.publishOfferToPeer(carol, setOf(bob, carol), CallType.VOICE, callId, "carol-sdp")

            assertEquals(2, published.size, "Should publish one gift-wrapped offer per peer")
        }

    @Test
    fun interfaceInvitePeerPublishesOfferToNewPeer() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            val answer = makeAnswer(from = bob, to = alice)
            manager.onSignalingEvent(answer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            manager.invitePeer(carol, "invite-sdp")

            assertEquals(1, published.size, "Should publish one gift-wrapped invite offer")

            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertTrue(carol in state.pendingPeerPubKeys, "Invited peer should be in pending set")
        }

    // ========================================================================
    // Mid-Call Invite: existing callees observe the invitee's broadcast answer
    // ========================================================================

    /**
     * Bob is already in a Connected group call with Alice. Alice invites Carol.
     * Carol's broadcast CallAnswer reaches Bob. Bob's state must expand to
     * include Carol in [CallState.Connected.peerPubKeys] and the answer must
     * still be emitted via [CallManager.sessionEvents] so the caller-side
     * CallSession can unconditionally initiate a mesh offer to Carol.
     */
    @Test
    fun midCallInviteAnswerFromUnknownPeerInConnectedExpandsMembership() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            // Bob is in an established 1-1 call with Alice.
            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            val (events, job) = collectSessionEvents(manager)

            // Alice invited Carol mid-call; Carol broadcasts her acceptance.
            // Bob sees a CallAnswer from Carol (unknown peer, same call-id)
            // with p-tags covering the whole expanded group {alice, bob, carol}.
            val carolAnswer = makeGroupAnswer(from = carol, members = setOf(alice, bob, carol))
            manager.onSignalingEvent(carolAnswer)

            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertTrue(carol in state.peerPubKeys, "Mid-call joiner must be added to peerPubKeys")
            assertTrue(alice in state.peerPubKeys, "Existing peer must still be present")
            val forwardedPeer =
                events
                    .filterIsInstance<CallSessionEvent.AnswerReceived>()
                    .firstOrNull()
                    ?.event
                    ?.pubKey
            assertEquals(carol, forwardedPeer, "Answer must still be emitted to CallSession")
            job.cancel()
        }

    /**
     * Regression: in an initial group call, the callees observing each other's
     * answers MUST NOT trip the mid-call expansion branch. The answering peer
     * was already part of the group membership set by [acceptCall] (via the
     * IncomingCall.groupMembers → Connecting.peerPubKeys/pendingPeerPubKeys
     * transition), so the combined size must stay the same — the peer only
     * moves out of pending into connected.
     */
    @Test
    fun initialCallAnswerFromKnownPeerDoesNotExpandMembership() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            // Alice calls Bob and Carol as a group.
            manager.onSignalingEvent(makeGroupOffer(from = alice, members = setOf(bob, carol)))
            assertIs<CallState.IncomingCall>(manager.state.value)

            // Bob accepts. State becomes
            // Connecting(peerPubKeys={alice}, pendingPeerPubKeys={carol}).
            manager.acceptCall(sdpAnswer)
            val connecting = manager.state.value
            assertIs<CallState.Connecting>(connecting)
            assertTrue(alice in connecting.peerPubKeys)
            assertTrue(
                carol in connecting.pendingPeerPubKeys,
                "Carol should start in pending until Bob observes her joining",
            )

            val totalBefore = connecting.peerPubKeys.size + connecting.pendingPeerPubKeys.size

            // Carol — who is already in Bob's tracked membership — answers.
            // This is the normal initial-call mesh observation path.
            manager.onSignalingEvent(makeGroupAnswer(from = carol, members = setOf(alice, bob, carol)))

            val after = manager.state.value
            assertIs<CallState.Connecting>(after)
            val totalAfter = after.peerPubKeys.size + after.pendingPeerPubKeys.size
            assertEquals(totalBefore, totalAfter, "Known peer's answer must not grow the tracked membership")
            assertTrue(carol in after.peerPubKeys, "Carol should move into peerPubKeys on answer")
            assertTrue(carol !in after.pendingPeerPubKeys, "Carol should leave pendingPeerPubKeys on answer")
        }

    /**
     * Edge case: an existing callee is still in Connecting state (its own ICE
     * handshake with the caller hasn't completed yet) when the mid-call
     * invitee broadcasts its answer. Membership must still expand so the UI
     * shows the new peer.
     */
    @Test
    fun midCallInviteAnswerFromUnknownPeerInConnectingExpandsMembership() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            // Intentionally NOT calling onPeerConnected — we want to stay in
            // Connecting for this test.
            assertIs<CallState.Connecting>(manager.state.value)

            val carolAnswer = makeGroupAnswer(from = carol, members = setOf(alice, bob, carol))
            manager.onSignalingEvent(carolAnswer)

            val state = manager.state.value
            assertIs<CallState.Connecting>(state)
            assertTrue(carol in state.peerPubKeys, "Mid-call joiner must be added while in Connecting")
        }

    /**
     * Full end-to-end mid-call invite: Alice calls Bob, connects, then invites
     * Carol. Verifies the round-trip state on all three CallManagers:
     *
     * - Alice's pending→connected transition for Carol (caller side)
     * - Carol's IncomingCall → Connecting with {alice, bob} as group members
     * - Bob's Connected state expanding to include Carol via the broadcast
     *   answer path
     */
    @Test
    fun interfaceMidCallInviteFullFlow() =
        runTest {
            val (aliceManager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))
            val (bobManager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))
            val (carolManager, _) = createManager(localPubKey = carol, followedKeys = setOf(alice, bob))

            // Step 1: Alice calls Bob (1-1). Both reach Connected.
            aliceManager.initiateCall(bob, CallType.VIDEO, callId, sdpOffer)
            bobManager.onSignalingEvent(makeOffer(from = alice, to = bob, callType = CallType.VIDEO))
            bobManager.acceptCall(sdpAnswer)
            aliceManager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            aliceManager.onPeerConnected()
            bobManager.onPeerConnected()
            assertIs<CallState.Connected>(aliceManager.state.value)
            assertIs<CallState.Connected>(bobManager.state.value)

            // Step 2: Alice invites Carol mid-call.
            aliceManager.invitePeer(carol, "alice-to-carol-sdp")
            val aliceAfterInvite = aliceManager.state.value
            assertIs<CallState.Connected>(aliceAfterInvite)
            assertTrue(carol in aliceAfterInvite.pendingPeerPubKeys)

            // Step 3: Carol receives the invite offer. Its p-tags cover the
            // full expanded group {alice, bob, carol} so Carol sees Bob in her
            // group membership from the first event.
            val carolOffer = makeGroupOffer(from = alice, members = setOf(alice, bob, carol), callType = CallType.VIDEO)
            carolManager.onSignalingEvent(carolOffer)
            val carolIncoming = carolManager.state.value
            assertIs<CallState.IncomingCall>(carolIncoming)
            assertTrue(alice in carolIncoming.groupMembers)
            assertTrue(bob in carolIncoming.groupMembers)

            // Step 4: Carol accepts. Her Connecting state must include Bob
            // (so later mid-call offers from Bob are handled correctly).
            // Alice (the caller) is placed directly into peerPubKeys; Bob
            // is placed into pendingPeerPubKeys with a local watchdog
            // timer until we observe him in the call.
            carolManager.acceptCall("carol-answer-sdp")
            val carolConnecting = carolManager.state.value
            assertIs<CallState.Connecting>(carolConnecting)
            assertTrue(alice in carolConnecting.peerPubKeys, "Carol's Connecting state must include Alice as a peer")
            assertTrue(
                bob in carolConnecting.pendingPeerPubKeys,
                "Carol's Connecting state must track Bob as a pending peer until he joins",
            )

            // Step 5: Alice receives Carol's answer broadcast. Carol moves
            // out of pending into peerPubKeys.
            aliceManager.onSignalingEvent(
                makeGroupAnswer(from = carol, members = setOf(alice, bob, carol), sdp = "carol-answer-sdp"),
            )
            val aliceAfterCarolAnswer = aliceManager.state.value
            assertIs<CallState.Connected>(aliceAfterCarolAnswer)
            assertTrue(carol in aliceAfterCarolAnswer.peerPubKeys, "Alice should have Carol connected")
            assertTrue(
                carol !in aliceAfterCarolAnswer.pendingPeerPubKeys,
                "Alice should no longer have Carol pending",
            )

            // Step 6: Bob receives Carol's answer broadcast. Bob's state must
            // expand to include Carol (mid-call join), and the answer must be
            // emitted so Bob's CallSession can initiate a mesh offer.
            val (bobEvents, bobJob) = collectSessionEvents(bobManager)
            bobManager.onSignalingEvent(
                makeGroupAnswer(from = carol, members = setOf(alice, bob, carol), sdp = "carol-answer-sdp"),
            )
            val bobAfterCarolAnswer = bobManager.state.value
            assertIs<CallState.Connected>(bobAfterCarolAnswer)
            assertTrue(carol in bobAfterCarolAnswer.peerPubKeys, "Bob should add Carol to his membership")
            val bobForwardedAnswer =
                bobEvents
                    .filterIsInstance<CallSessionEvent.AnswerReceived>()
                    .firstOrNull()
                    ?.event
                    ?.pubKey
            assertEquals(carol, bobForwardedAnswer, "Bob must emit Carol's answer to his CallSession")
            bobJob.cancel()
        }

    // ========================================================================
    // Per-peer 30-second invite timeout
    // ========================================================================

    /**
     * P2P call: Alice calls Bob, Bob never answers. After 30 s Alice's
     * per-peer timer fires and the call ends with [EndReason.TIMEOUT]. The
     * timeout hangup is also published so Bob's device stops ringing.
     */
    @Test
    fun perPeerTimeoutEndsP2PCallWhenBobNeverAnswers() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)
            published.clear()

            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS + 100)

            val ended = manager.state.value
            assertIs<CallState.Ended>(ended)
            assertEquals(EndReason.TIMEOUT, ended.reason)
            assertEquals(
                1,
                published.size,
                "Timeout should publish exactly one hangup to the unresponsive peer",
            )
        }

    /**
     * Group call: Alice offers to Bob + Carol. Bob answers quickly, Carol
     * does not. After 30 s Alice's timer for Carol fires and Carol is
     * removed from pending. The call continues with Bob. A hangup is
     * published to Carol but the Bob leg is untouched.
     */
    @Test
    fun perPeerTimeoutDropsSlowCalleeFromGroupCall() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)
            assertIs<CallState.Offering>(manager.state.value)

            // Bob answers quickly (well before the 30 s timer).
            manager.onSignalingEvent(makeGroupAnswer(from = bob, members = setOf(alice, bob, carol)))
            val afterBob = manager.state.value
            assertIs<CallState.Connecting>(afterBob)
            assertTrue(bob in afterBob.peerPubKeys)
            assertTrue(carol in afterBob.pendingPeerPubKeys)
            published.clear()

            // Advance past the 30 s per-peer timeout.
            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS + 100)

            // Carol dropped; call continues with Bob.
            val state = manager.state.value
            assertIs<CallState.Connecting>(state)
            assertTrue(carol !in state.pendingPeerPubKeys, "Carol should be dropped from pending")
            assertTrue(bob in state.peerPubKeys, "Bob leg must be untouched")

            assertEquals(
                1,
                published.size,
                "Timeout must publish exactly one hangup (addressed to Carol)",
            )
        }

    /**
     * Bob answering inside the 30 s window cancels his per-peer timer.
     * Advancing 60 s afterwards must not fire any phantom timeout.
     */
    @Test
    fun perPeerTimeoutIsCancelledOnAnswer() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            assertIs<CallState.Offering>(manager.state.value)

            // Bob answers before the timeout fires.
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            assertIs<CallState.Connecting>(manager.state.value)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            // Advance way past the 30 s timeout — no timeout must fire.
            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS * 2)

            assertIs<CallState.Connected>(manager.state.value)
            assertTrue(published.isEmpty(), "No timeout hangup must be published after a successful answer")
        }

    /**
     * Mid-call invite: Alice is Connected with Bob, then invites Carol.
     * Carol never answers. After 30 s Carol is dropped from
     * [CallState.Connected.pendingPeerPubKeys] and the call continues with
     * Bob. A hangup is published to Carol.
     */
    @Test
    fun perPeerTimeoutDropsMidCallInviteeWhenNoAnswer() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            // Alice ↔ Bob Connected.
            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Alice invites Carol.
            manager.invitePeer(carol, "invite-sdp")
            val afterInvite = manager.state.value
            assertIs<CallState.Connected>(afterInvite)
            assertTrue(carol in afterInvite.pendingPeerPubKeys)
            published.clear()

            // Carol never answers — advance past the 30 s invite timeout.
            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS + 100)

            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertTrue(carol !in state.pendingPeerPubKeys, "Carol should be dropped from pending")
            assertTrue(bob in state.peerPubKeys, "Bob leg must be untouched")

            assertEquals(
                1,
                published.size,
                "Invite timeout must publish exactly one hangup addressed to Carol",
            )
        }

    /**
     * Bob rejecting a P2P offer cancels his per-peer timer so advancing time
     * past 30 s afterwards does not publish a duplicate hangup on top of the
     * Ended transition.
     */
    @Test
    fun perPeerTimeoutIsCancelledOnReject() =
        runTest {
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob))

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            manager.onSignalingEvent(makeReject(from = bob, to = alice))
            val ended = manager.state.value
            assertIs<CallState.Ended>(ended)
            assertEquals(EndReason.PEER_REJECTED, ended.reason)

            val publishedAfterReject = published.size
            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS * 2)
            assertEquals(
                publishedAfterReject,
                published.size,
                "No timeout hangup should be published after the peer rejected",
            )
        }

    /**
     * Callee-side group call: Bob accepts a group offer from Alice that
     * also included Carol. Carol never joins. After 30 s Bob's local
     * watchdog timer drops Carol from his Connecting state WITHOUT
     * publishing any signaling (the caller is responsible for terminating
     * Carol's ringing — not Bob).
     */
    @Test
    fun perPeerTimeoutDropsUnresponsiveGroupMemberOnCalleeSide() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            // Alice calls {bob, carol} as a group. Bob receives and accepts.
            manager.onSignalingEvent(makeGroupOffer(from = alice, members = setOf(bob, carol)))
            assertIs<CallState.IncomingCall>(manager.state.value)
            manager.acceptCall(sdpAnswer)

            // Bob's Connecting state: Alice (caller) is connected, Carol
            // is pending with a watchdog timer.
            val connecting = manager.state.value
            assertIs<CallState.Connecting>(connecting)
            assertTrue(alice in connecting.peerPubKeys, "Caller must be in peerPubKeys")
            assertTrue(carol in connecting.pendingPeerPubKeys, "Unseen peer must be pending")
            published.clear()

            // Advance past the 30 s per-peer timeout. Carol never sent an
            // answer or a mesh offer.
            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS + 100)

            val state = manager.state.value
            assertIs<CallState.Connecting>(state)
            assertTrue(carol !in state.pendingPeerPubKeys, "Carol must be dropped from pending")
            assertTrue(carol !in state.peerPubKeys, "Carol must not be silently promoted")
            assertTrue(alice in state.peerPubKeys, "Alice leg must be untouched")

            // The callee MUST NOT publish a hangup — Carol is still
            // connected to Alice from the caller's perspective, and
            // terminating her ringing is Alice's responsibility.
            assertTrue(
                published.isEmpty(),
                "Callee watchdog timeout must not publish any signaling",
            )
        }

    /**
     * Callee-side group call: Bob accepts a group offer that included
     * Carol. Carol publishes a mesh offer to Bob mid-call. Bob must move
     * Carol out of pending into peerPubKeys and cancel her watchdog timer
     * — the offer is proof she's in the call.
     */
    @Test
    fun midCallOfferFromPendingPeerMovesThemIntoConnected() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            manager.onSignalingEvent(makeGroupOffer(from = alice, members = setOf(bob, carol)))
            manager.acceptCall(sdpAnswer)
            val connecting = manager.state.value
            assertIs<CallState.Connecting>(connecting)
            assertTrue(carol in connecting.pendingPeerPubKeys)

            // Carol publishes a mesh offer to Bob (same call-id).
            val (events, job) = collectSessionEvents(manager)
            manager.onSignalingEvent(makeGroupOffer(from = carol, members = setOf(alice, bob, carol)))

            val after = manager.state.value
            assertIs<CallState.Connecting>(after)
            assertTrue(carol in after.peerPubKeys, "Carol should move into peerPubKeys")
            assertTrue(carol !in after.pendingPeerPubKeys, "Carol should leave pending")
            val midCallOfferPeer = events.filterIsInstance<CallSessionEvent.MidCallOfferReceived>().firstOrNull()?.peerPubKey
            assertEquals(carol, midCallOfferPeer, "CallSession must still receive the mid-call offer")
            job.cancel()

            // And her watchdog should be cancelled — advancing past the
            // 30 s budget must not further alter state.
            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS + 100)
            val later = manager.state.value
            assertIs<CallState.Connecting>(later)
            assertTrue(carol in later.peerPubKeys, "Carol must remain connected after timeout budget")
        }

    /**
     * Regression: the callee-side watchdog must be cancelled when a
     * pending peer's answer arrives, so advancing past 30 s afterwards
     * does not cause a phantom state change or publish.
     */
    @Test
    fun calleeWatchdogIsCancelledOnAnswerFromPendingPeer() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            manager.onSignalingEvent(makeGroupOffer(from = alice, members = setOf(bob, carol)))
            manager.acceptCall(sdpAnswer)
            assertTrue(carol in (manager.state.value as CallState.Connecting).pendingPeerPubKeys)

            // Carol's answer reaches Bob well before her watchdog fires.
            manager.onSignalingEvent(makeGroupAnswer(from = carol, members = setOf(alice, bob, carol)))
            val afterAnswer = manager.state.value
            assertIs<CallState.Connecting>(afterAnswer)
            assertTrue(carol in afterAnswer.peerPubKeys)
            assertTrue(carol !in afterAnswer.pendingPeerPubKeys)
            published.clear()

            // Advance past the 30 s watchdog — nothing should fire.
            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS * 2)
            assertIs<CallState.Connecting>(manager.state.value)
            assertTrue(
                published.isEmpty(),
                "Cancelled watchdog must not publish a hangup after the peer answered",
            )
        }

    /**
     * Regression: Alice calls {bob, carol} as a group, Bob accepts and
     * talks to Alice, Carol never joins. Alice hangs up before Carol's
     * watchdog timer fires. Bob must end the call immediately — NOT stay
     * in a Connected state alone waiting for Carol.
     */
    @Test
    fun callerHangupEndsCalleeCallEvenWhenPendingPeersRemain() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            // Alice calls {bob, carol}. Bob accepts and reaches Connected.
            manager.onSignalingEvent(makeGroupOffer(from = alice, members = setOf(bob, carol)))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            val connected = manager.state.value
            assertIs<CallState.Connected>(connected)
            assertTrue(alice in connected.peerPubKeys, "Alice must be the sole connected peer")
            assertTrue(carol in connected.pendingPeerPubKeys, "Carol must still be pending")
            published.clear()

            // Alice hangs up well before Carol's 30 s watchdog fires.
            manager.onSignalingEvent(makeHangup(from = alice, to = bob))

            // The call must terminate immediately — Bob has nobody left
            // to talk to, so staying in Connected alone is wrong.
            val after = manager.state.value
            assertIs<CallState.Ended>(after)
            assertEquals(EndReason.PEER_HANGUP, after.reason)

            // Bob must not publish any hangup: Carol's ringing is Alice's
            // responsibility (Alice already broadcast her hangup to Carol
            // as part of her own group hangup).
            assertTrue(
                published.isEmpty(),
                "Callee must not publish anything when ending due to caller hangup",
            )
        }

    /**
     * Regression: when Bob is alone in a Connecting state (haven't reached
     * Connected yet) and the caller hangs up, the call must end rather
     * than lingering in Connecting with an empty peerPubKeys until the
     * watchdog fires.
     */
    @Test
    fun callerHangupEndsCalleeCallInConnectingEvenWhenPendingPeersRemain() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            manager.onSignalingEvent(makeGroupOffer(from = alice, members = setOf(bob, carol)))
            manager.acceptCall(sdpAnswer)
            // Intentionally NOT calling onPeerConnected — we want to stay in Connecting.
            assertIs<CallState.Connecting>(manager.state.value)

            manager.onSignalingEvent(makeHangup(from = alice, to = bob))

            val after = manager.state.value
            assertIs<CallState.Ended>(after)
            assertEquals(EndReason.PEER_HANGUP, after.reason)
        }

    /**
     * Regression: if Bob is in a Connected group call with Alice and has
     * personally invited Dave mid-call, and then Alice hangs up, the call
     * ends AND Bob publishes a hangup to Dave (who is still ringing on
     * Bob's invitation) so Dave's device stops ringing.
     */
    @Test
    fun callerHangupEndsCallAndNotifiesInvitedPendingPeers() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice, carol))

            // Alice ↔ Bob Connected (1-1).
            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // Bob invites Carol mid-call — Bob becomes Carol's inviter.
            manager.invitePeer(carol, "bob-to-carol-sdp")
            val afterInvite = manager.state.value
            assertIs<CallState.Connected>(afterInvite)
            assertTrue(carol in afterInvite.pendingPeerPubKeys)
            published.clear()

            // Alice hangs up before Carol accepts Bob's invite.
            manager.onSignalingEvent(makeHangup(from = alice, to = bob))

            val after = manager.state.value
            assertIs<CallState.Ended>(after)
            assertEquals(EndReason.PEER_HANGUP, after.reason)

            // Bob must publish a hangup to Carol (his invitee) so her
            // device stops ringing.
            assertEquals(
                1,
                published.size,
                "Must publish exactly one hangup to the invited-but-pending peer",
            )
        }

    /**
     * When a group offer is made, each callee gets its own timer. If NEITHER
     * answers, both timers fire in turn and the whole call ends with
     * [EndReason.TIMEOUT] (the second timeout has no peers left, so it tips
     * the state over the edge).
     */
    @Test
    fun perPeerTimeoutEndsCallWhenAllCalleesIgnore() =
        runTest {
            val (manager, _) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol))

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)
            assertIs<CallState.Offering>(manager.state.value)

            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS + 100)

            val ended = manager.state.value
            assertIs<CallState.Ended>(ended)
            assertEquals(EndReason.TIMEOUT, ended.reason)
        }

    @Test
    fun interfaceFullP2PCallFlowWithRealSigners() =
        runTest {
            // Full end-to-end P2P call: Alice calls Bob
            val (aliceManager, alicePublished) = createManager(localPubKey = alice, followedKeys = setOf(bob))
            val (bobManager, bobPublished) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            // Step 1: Alice initiates call
            aliceManager.initiateCall(bob, CallType.VIDEO, callId, sdpOffer)
            assertIs<CallState.Offering>(aliceManager.state.value)
            assertEquals(1, alicePublished.size)

            // Step 2: Bob receives offer (simulated, since we can't decrypt the gift wrap)
            val bobOffer = makeOffer(from = alice, to = bob, callType = CallType.VIDEO)
            bobManager.onSignalingEvent(bobOffer)
            assertIs<CallState.IncomingCall>(bobManager.state.value)

            // Step 3: Bob accepts
            bobManager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(bobManager.state.value)
            assertTrue(bobPublished.isNotEmpty())

            // Step 4: Alice receives answer
            val aliceAnswer = makeAnswer(from = bob, to = alice)
            aliceManager.onSignalingEvent(aliceAnswer)
            assertIs<CallState.Connecting>(aliceManager.state.value)

            // Step 5: Both sides report peer connected
            aliceManager.onPeerConnected()
            bobManager.onPeerConnected()
            assertIs<CallState.Connected>(aliceManager.state.value)
            assertIs<CallState.Connected>(bobManager.state.value)

            // Step 6: Alice sends renegotiation (add video)
            alicePublished.clear()
            aliceManager.sendRenegotiation("new-video-sdp", bob)
            assertEquals(1, alicePublished.size)

            // Step 7: Bob receives renegotiate and responds
            val (bobRenegoEvents, bobRenegoJob) = collectRenegotiationEvents(bobManager)
            bobManager.onSignalingEvent(makeRenegotiate(from = alice, to = bob))
            assertTrue(bobRenegoEvents.isNotEmpty())
            bobRenegoJob.cancel()

            bobPublished.clear()
            bobManager.sendRenegotiationAnswer("renego-answer-sdp", alice)
            assertEquals(1, bobPublished.size)

            // Step 8: Alice hangs up
            alicePublished.clear()
            aliceManager.hangup()
            assertIs<CallState.Ended>(aliceManager.state.value)
            assertTrue(alicePublished.isNotEmpty())

            // Step 9: Bob receives hangup
            bobManager.onSignalingEvent(makeHangup(from = alice, to = bob))
            assertIs<CallState.Ended>(bobManager.state.value)

            // Step 10: Both auto-reset to Idle
            advanceUntilIdle()
            assertIs<CallState.Idle>(aliceManager.state.value)
            assertIs<CallState.Idle>(bobManager.state.value)
        }

    // ========================================================================
    // User has disabled calls in Settings
    // ========================================================================

    /**
     * When [CallManager.isCallsEnabled] returns false, an incoming
     * [CallOfferEvent] is silently dropped — no state change, no ringing,
     * no published reject.
     */
    @Test
    fun incomingOfferIgnoredWhenCallsDisabledInSettings() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, isCallsEnabled = { false })

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))

            assertIs<CallState.Idle>(manager.state.value)
            assertTrue(
                published.isEmpty(),
                "Disabled calls must not publish any signaling events in response to an incoming offer",
            )
        }

    /**
     * Toggling the flag to false after a call is already in progress does
     * not affect the in-flight call — CallManager only gates *new* incoming
     * offers. Signaling for the active call continues to flow so cleanup
     * (hangups, answers, ICE candidates) can complete.
     */
    @Test
    fun disablingCallsAfterStartDoesNotTearDownInProgressCall() =
        runTest {
            var enabled = true
            val (manager, _) = createManager(localPubKey = bob, isCallsEnabled = { enabled })

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // User flips the toggle off mid-call.
            enabled = false

            // The existing call is unaffected — Bob can still receive
            // hangup/answer/ICE traffic for the current call.
            assertIs<CallState.Connected>(manager.state.value)

            // But a *new* offer for a different call is silently ignored.
            val newCall = makeOffer(from = carol, to = bob, callId = callId2)
            manager.onSignalingEvent(newCall)
            assertIs<CallState.Connected>(manager.state.value)
        }

    /**
     * Regression: when calls are enabled (the default) the incoming-offer
     * path still works exactly as before.
     */
    @Test
    fun incomingOfferProcessedWhenCallsEnabled() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, isCallsEnabled = { true })

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))

            assertIs<CallState.IncomingCall>(manager.state.value)
        }

    @Test
    fun acceptCallPublishesAnswerWrappedForSelfSoSiblingDeviceCanStopRinging() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)
            published.clear()

            manager.acceptCall(sdpAnswer)

            val recipients = published.mapNotNull { it.recipientPubKey() }.toSet()
            assertTrue(
                alice in recipients,
                "Answer must be wrapped for the caller (alice), recipients=$recipients",
            )
            assertTrue(
                bob in recipients,
                "Answer must also be wrapped for the callee's own pubkey (bob) so " +
                    "sibling devices stop ringing, recipients=$recipients",
            )
        }

    @Test
    fun rejectCallPublishesRejectWrappedForSelfSoSiblingDeviceCanStopRinging() =
        runTest {
            val (manager, published) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)
            published.clear()

            manager.rejectCall()

            val recipients = published.mapNotNull { it.recipientPubKey() }.toSet()
            assertTrue(
                alice in recipients,
                "Reject must be wrapped for the caller (alice), recipients=$recipients",
            )
            assertTrue(
                bob in recipients,
                "Reject must also be wrapped for the callee's own pubkey (bob) so " +
                    "sibling devices stop ringing, recipients=$recipients",
            )
        }

    /**
     * Simulates the full multi-device scenario:
     *   1. Alice calls Bob.  Bob is logged in on two phones (two separate
     *      CallManagers backed by the same bobSigner).
     *   2. Both phones receive the offer and enter IncomingCall.
     *   3. Phone 1 accepts — this publishes an answer gift-wrapped for
     *      alice AND for bob.
     *   4. Phone 2 (subscribed to its own pubkey) receives the self-
     *      addressed echo and must transition to Ended(ANSWERED_ELSEWHERE).
     *   5. Phone 1 must remain in Connecting — the echo it also receives
     *      must not disturb its in-progress session.
     */
    @Test
    fun answeringOnDeviceOneStopsDeviceTwoRingingWithoutDisturbingDeviceOne() =
        runTest {
            val (phone1, published1) = createManager(localPubKey = bob, followedKeys = setOf(alice))
            val (phone2, _) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val offer = makeOffer(from = alice, to = bob)
            phone1.onSignalingEvent(offer)
            phone2.onSignalingEvent(offer)
            assertIs<CallState.IncomingCall>(phone1.state.value)
            assertIs<CallState.IncomingCall>(phone2.state.value)
            published1.clear()

            // Phone 1 picks up.  It will publish one wrap for alice and one
            // wrap for bob (the self-echo).
            phone1.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(phone1.state.value)

            // Deliver the self-echo to phone 2. The inner event is what
            // another CallManager would see after unwrapping the gift wrap,
            // so construct a parallel CallAnswerEvent directly (the wrap
            // payload is opaque in the test harness because published1
            // stores EphemeralGiftWrapEvents, not the unwrapped inner).
            val selfAnswer = makeAnswer(from = bob, to = alice)
            phone2.onSignalingEvent(selfAnswer)

            // Phone 2 stops ringing with ANSWERED_ELSEWHERE.
            val phone2State = phone2.state.value
            assertIs<CallState.Ended>(phone2State)
            assertEquals(EndReason.ANSWERED_ELSEWHERE, phone2State.reason)

            // Phone 1 ALSO receives the self-echo (it is addressed to bob).
            // It must stay in Connecting — the live call cannot be torn down
            // by its own answer bouncing off the relay.
            phone1.onSignalingEvent(selfAnswer)
            assertIs<CallState.Connecting>(phone1.state.value)
        }

    /**
     * Regression for the subtle bug where a self-reject echo would trigger
     * PeerLeft(signer.pubKey) on a device that had already accepted,
     * disposing its own PeerSession and killing the live call.
     */
    @Test
    fun selfRejectEchoDoesNotDisturbDeviceAlreadyInCall() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val (events, job) = collectSessionEvents(manager)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)

            // A sibling device rejects the call after we already answered.
            // Its reject event is wrapped for us too (multi-device signal).
            val siblingReject = makeReject(from = bob, to = alice)
            manager.onSignalingEvent(siblingReject)

            // Our live call must survive — the echo must not emit
            // PeerLeft (which would dispose our own PeerSession) and must
            // not change state.
            assertIs<CallState.Connected>(manager.state.value)
            assertTrue(
                events.none { it is CallSessionEvent.PeerLeft },
                "PeerLeft must not be emitted for a self-reject echo — the UI would " +
                    "tear down our own PeerSession, killing the live call audio.",
            )
            job.cancel()
        }

    /**
     * Same guard for the Connecting state: a self-reject arriving during
     * the connection handshake must not drop us from our own pending set
     * and must not emit PeerLeft(self).
     */
    @Test
    fun selfRejectEchoDuringConnectingDoesNotFireOnPeerLeft() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            val (events, job) = collectSessionEvents(manager)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            manager.acceptCall(sdpAnswer)
            assertIs<CallState.Connecting>(manager.state.value)

            val siblingReject = makeReject(from = bob, to = alice)
            manager.onSignalingEvent(siblingReject)

            assertIs<CallState.Connecting>(manager.state.value)
            assertTrue(events.none { it is CallSessionEvent.PeerLeft }, "PeerLeft must not be emitted for a self-reject echo during Connecting")
            job.cancel()
        }

    /**
     * If the relay replays events out of order — self-answer arriving
     * before the original offer — the second device must NOT start ringing
     * for a call-id it already knows was answered elsewhere.
     */
    @Test
    fun offerReplayedAfterSelfAnswerDoesNotStartRinging() =
        runTest {
            val (manager, _) = createManager(localPubKey = bob, followedKeys = setOf(alice))

            // Self-answer arrives first (while we're in Idle — nothing to
            // do with it except remember the call-id so a later offer for
            // it is treated as stale).
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            assertIs<CallState.Idle>(manager.state.value)

            // Now the offer finally arrives.  We must NOT start ringing.
            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.Idle>(manager.state.value)
        }

    // ========================================================================
    // Signer refusals: the user rejects a call signal in their signer app
    // ========================================================================

    /**
     * Signs like [inner] until [refuse] is set, then throws what an external signer (Amber)
     * throws when the user rejects the request, counting each refused prompt.
     */
    private class RefusingSigner(
        private val inner: NostrSigner,
    ) : NostrSigner(inner.pubKey) {
        var refuse = false
        var refusals = 0

        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T {
            if (refuse) {
                refusals++
                throw SignerExceptions.ManuallyUnauthorizedException("Could not sign: User has rejected the request.")
            }
            return inner.sign(createdAt, kind, tags, content)
        }

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip44Decrypt(ciphertext, fromPublicKey)

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = inner.hasForegroundSupport()
    }

    @Test
    fun refusedOfferEndsOneToOneCallWithoutPromptingAgain() =
        runTest {
            val signer = RefusingSigner(aliceSigner).apply { refuse = true }
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob), signer = signer)

            manager.beginOffering(callId, setOf(bob), CallType.VOICE)
            manager.publishOfferToPeer(bob, setOf(bob), CallType.VOICE, callId, sdpOffer)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.HANGUP, state.reason)

            // No offer went out, so the per-peer and ringing timeouts must not ask the signer
            // for a hangup to a peer who never rang.
            advanceTimeBy(CallManager.RINGING_WATCHDOG_MS + 1_000)
            assertTrue(published.isEmpty(), "Nothing should be published")
            assertEquals(1, signer.refusals, "The signer should be asked only once")
        }

    @Test
    fun refusedOfferDropsOnlyThatPeerFromGroupCall() =
        runTest {
            val signer = RefusingSigner(aliceSigner)
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob, carol), signer = signer)

            manager.beginOffering(callId, setOf(bob, carol), CallType.VOICE)
            manager.publishOfferToPeer(bob, setOf(bob, carol), CallType.VOICE, callId, sdpOffer)
            signer.refuse = true
            manager.publishOfferToPeer(carol, setOf(bob, carol), CallType.VOICE, callId, "carol-sdp")

            val state = manager.state.value
            assertIs<CallState.Offering>(state)
            assertEquals(setOf(bob), state.peerPubKeys)
            assertEquals(1, published.size, "Only bob's offer went out")
            assertEquals(1, signer.refusals)
        }

    @Test
    fun refusedAnswerEndsIncomingCallLocally() =
        runTest {
            val signer = RefusingSigner(bobSigner)
            val (manager, published) = createManager(localPubKey = bob, signer = signer)

            manager.onSignalingEvent(makeOffer(from = alice, to = bob))
            assertIs<CallState.IncomingCall>(manager.state.value)

            signer.refuse = true
            manager.acceptCall(sdpAnswer)

            val state = manager.state.value
            assertIs<CallState.Ended>(state)
            assertEquals(EndReason.REJECTED, state.reason)
            advanceTimeBy(CallManager.CONNECTING_TIMEOUT_MS + 1_000)
            assertTrue(published.isEmpty(), "Nothing should be published")
            assertEquals(1, signer.refusals)
        }

    @Test
    fun refusedInviteDropsTheInviteeWithoutHangup() =
        runTest {
            val signer = RefusingSigner(aliceSigner)
            val (manager, published) = createManager(localPubKey = alice, followedKeys = setOf(bob), signer = signer)

            manager.initiateCall(bob, CallType.VOICE, callId, sdpOffer)
            manager.onSignalingEvent(makeAnswer(from = bob, to = alice))
            manager.onPeerConnected()
            assertIs<CallState.Connected>(manager.state.value)
            published.clear()

            signer.refuse = true
            manager.invitePeer(carol, "invite-sdp")

            val state = manager.state.value
            assertIs<CallState.Connected>(state)
            assertTrue(carol !in state.pendingPeerPubKeys, "The refused invitee should be dropped")

            advanceTimeBy(CallManager.PEER_INVITE_TIMEOUT_MS + 1_000)
            assertTrue(published.isEmpty(), "No hangup for an invite that never went out")
            assertEquals(1, signer.refusals)
        }
}
