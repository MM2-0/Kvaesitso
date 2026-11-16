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

import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager.Companion.CALL_TIMEOUT_MS
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager.Companion.CONNECTING_TIMEOUT_MS
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.WebRtcCallFactory
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "CallManager"

class CallManager(
    private val signer: NostrSigner,
    private val scope: CoroutineScope,
    private val isFollowing: (HexKey) -> Boolean,
    private val publishEvent: (EphemeralGiftWrapEvent) -> Unit,
    /**
     * Whether the user has enabled calls in Settings. When false, all
     * incoming [CallOfferEvent]s are silently ignored so the device never
     * rings and no `IncomingCall` state is entered. Signaling for calls that
     * are already in progress is still processed so cleanup can complete.
     * Defaults to `true` (enabled) so existing callers and tests keep their
     * current behavior.
     */
    private val isCallsEnabled: () -> Boolean = { true },
) {
    private val factory = WebRtcCallFactory()

    /** Serializes all state-mutating operations.  Signaling events can arrive
     *  from multiple relay coroutines concurrently; without this lock a hangup
     *  and an answer could race, causing the answer to overwrite an Ended
     *  transition and leaking WebRTC resources. */
    private val stateMutex = Mutex()

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    /**
     * All events that the Activity-scoped `CallSession` must handle are
     * emitted through this single flow. Using a SharedFlow instead of
     * mutable `var` callbacks means:
     *  - No stale references to a dead CallSession between Activity
     *    destroy and recreate.
     *  - The collector can (re)subscribe via `repeatOnLifecycle` without
     *    missing buffered events.
     *  - No null-check on every emit site.
     *
     * Buffer size 256 handles worst-case bursts (20+ ICE candidates per
     * peer × multiple peers + answers + peer-left events). tryEmit is
     * used instead of emit to avoid suspending while holding stateMutex
     * (which would block all signaling). Drops are logged but should
     * never happen in practice with this buffer size.
     */
    private val _sessionEvents = MutableSharedFlow<CallSessionEvent>(extraBufferCapacity = 256)
    val sessionEvents: SharedFlow<CallSessionEvent> = _sessionEvents.asSharedFlow()

    /*
     * Renegotiation offers from remote peers. Separate from [sessionEvents]
     * because renegotiation has its own glare-resolution logic in
     * CallSession and benefits from a dedicated collector.
     */

    /** Emits a session event, logging a warning if the buffer overflows. */
    private fun emitSessionEvent(event: CallSessionEvent) {
        if (!_sessionEvents.tryEmit(event)) {
            Log.e("CallManager") { "sessionEvents buffer overflow — dropped: $event" }
        }
    }

    private val _renegotiationEvents = MutableSharedFlow<CallRenegotiateEvent>(extraBufferCapacity = 32)
    val renegotiationEvents: SharedFlow<CallRenegotiateEvent> = _renegotiationEvents.asSharedFlow()

    private var timeoutJob: Job? = null
    private var resetJob: Job? = null
    private val processedEventIds = LinkedHashSet<String>()

    /**
     * Detached scope for the ringing watchdog so the timer survives the
     * `scope` being cancelled by the owning ViewModel. The watchdog is a
     * fail-safe that unconditionally forces an `Ended(TIMEOUT)` transition
     * if the state stays in `IncomingCall`/`Offering` past
     * `RINGING_WATCHDOG_MS`, regardless of whether any collector observes
     * the state. Cancelled explicitly via [dispose].
     */
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ringingWatchdogJob: Job? = null
    private var connectingWatchdogJob: Job? = null

    /** Per-peer invite timeout jobs.  A separate 30-second timer is scheduled
     *  for each peer we are waiting on (initial group-call offerees and
     *  mid-call invitees) so that slow/unavailable peers can be dropped
     *  individually without affecting the rest of the call. The timer is
     *  cancelled when the peer answers, rejects, or hangs up — or when the
     *  whole call ends. */
    private val perPeerTimeoutJobs = mutableMapOf<HexKey, Job>()

    /** Peers we have published a CallOffer to (either as the call initiator
     *  or via a mid-call invitePeer). When their per-peer timer fires we
     *  publish a CallHangup so their device stops ringing.
     *
     *  Peers we are merely *waiting* on (i.e. members of a group call we
     *  accepted but who haven't yet joined via mesh) are tracked only with
     *  their timer job — NOT in this set. When their timer fires we drop
     *  them silently from local state without sending any further
     *  signaling, because their ringing is the caller's responsibility to
     *  terminate. */
    private val peersInvitedByUs = mutableSetOf<HexKey>()

    /** Call IDs for which we have seen a hangup, reject, or answer-elsewhere
     *  signal.  Checked before transitioning to [CallState.IncomingCall] so
     *  that stale offer events replayed by relays after an app restart do not
     *  trigger ringing for calls that already ended. */
    private val completedCallIds = LinkedHashSet<String>()

    /** Timestamp (epoch seconds) when this CallManager was created.  Events
     *  created before this are from a previous app session and should not
     *  trigger ringing. */
    private val initTimestamp = TimeUtils.now()

    /** Peers whose answers we saw while still ringing (IncomingCall).
     *  After we accept, we trigger callee-to-callee mesh setup with them. */
    private val discoveredCalleePeers = mutableSetOf<HexKey>()

    companion object {
        const val CALL_TIMEOUT_MS = 60_000L // 60 seconds ringing timeout (callee side)
        const val PEER_INVITE_TIMEOUT_MS = 30_000L // 30 seconds per-peer invite timeout (caller side)
        const val CONNECTING_TIMEOUT_MS = 30_000L // 30 seconds to establish ICE connection
        const val ENDED_DISPLAY_MS = 2_000L // show "call ended" briefly before resetting
        const val MAX_EVENT_AGE_SECONDS = 20L // discard signaling events older than this
        const val MAX_PROCESSED_EVENT_IDS = 2_000 // cap dedup set to prevent unbounded growth
        const val MAX_COMPLETED_CALL_IDS = 200 // cap completed-call set

        /**
         * Hard ceiling on how long a call may remain in a ringing state
         * (`IncomingCall` or `Offering`). Deliberately longer than
         * [CALL_TIMEOUT_MS] (60s) so the normal timeout path gets a chance
         * to fire first; this is purely the fail-safe for when that path
         * never runs (e.g. a stuck coroutine, a canceled `scope`, or a
         * dropped state transition).
         */
        const val RINGING_WATCHDOG_MS = 65_000L
    }

    /** Adds [value] to a [LinkedHashSet], evicting the oldest entries when
     *  the set exceeds [maxSize]. Insertion-order iteration of LinkedHashSet
     *  ensures oldest entries are removed first. */
    private fun <T> cappedAdd(
        set: LinkedHashSet<T>,
        value: T,
        maxSize: Int,
    ) {
        set.add(value)
        while (set.size > maxSize) {
            val oldest = set.iterator().next()
            set.remove(oldest)
        }
    }

    private fun isEventTooOld(event: Event): Boolean {
        val age = TimeUtils.now() - event.createdAt
        if (age > MAX_EVENT_AGE_SECONDS) return true
        // Reject events created before this CallManager was initialized.
        // After an app restart the relay replays old events; even if the
        // wall-clock age is within the window, events from a previous
        // session should never start ringing.
        if (event.createdAt < initTimestamp - MAX_EVENT_AGE_SECONDS) return true
        return false
    }

    // ---- Call initiation (state + publish) ----

    /**
     * Sets state to Offering. Called by CallController before creating
     * per-peer offers in group calls.
     */
    suspend fun beginOffering(
        callId: String,
        calleePubKeys: Set<HexKey>,
        callType: CallType,
    ) = stateMutex.withLock {
        _state.value = CallState.Offering(callId, calleePubKeys, callType)
        cancelAllPeerTimeouts()
        peersInvitedByUs.addAll(calleePubKeys)
        calleePubKeys.forEach { schedulePeerTimeout(it, callId) }
        armRingingWatchdog(callId)
    }

    /**
     * Publishes a per-peer call offer (group context: p-tags for all members).
     * Called once per callee so each gets their own SDP.
     */
    suspend fun publishOfferToPeer(
        calleePubKey: HexKey,
        allCalleePubKeys: Set<HexKey>,
        callType: CallType,
        callId: String,
        sdpOffer: String,
    ) {
        Log.d("CallManager") { "publishOfferToPeer: to=${calleePubKey.take(8)}, sdpLength=${sdpOffer.length}" }
        logSignerErrors(TAG) {
            val result = factory.createCallOffer(sdpOffer, calleePubKey, allCalleePubKeys, callId, callType, signer)
            publishEvent(result.wrap)
        } ?: dropPeerAfterSignerRefusal(calleePubKey, callId)
    }

    /**
     * Publishes a callee-to-callee offer within an existing call.
     * Uses the current call context for call-id, call-type, and group members.
     */
    suspend fun publishOfferToPeer(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        val callId = currentCallId() ?: return
        val callType = currentCallType() ?: return
        val allMembers = (currentPeerPubKeys() ?: emptySet()) + signer.pubKey
        Log.d("CallManager") { "publishOfferToPeer (mid-call): to=${peerPubKey.take(8)}, sdpLength=${sdpOffer.length}" }
        logSignerErrors(TAG) {
            val result = factory.createCallOffer(sdpOffer, peerPubKey, allMembers, callId, callType, signer)
            publishEvent(result.wrap)
        }
    }

    /**
     * Publishes a callee-to-callee answer within an existing call.
     */
    suspend fun publishAnswerToPeer(
        peerPubKey: HexKey,
        sdpAnswer: String,
    ) {
        val callId = currentCallId() ?: return
        val allMembers = (currentPeerPubKeys() ?: emptySet()) + signer.pubKey
        Log.d("CallManager") { "publishAnswerToPeer: to=${peerPubKey.take(8)}, sdpLength=${sdpAnswer.length}" }
        logSignerErrors(TAG) {
            val result = factory.createCallAnswer(sdpAnswer, peerPubKey, allMembers, callId, signer)
            publishEvent(result.wrap)
        }
    }

    // ---- P2P call initiation (convenience) ----

    suspend fun initiateCall(
        calleePubKey: HexKey,
        callType: CallType,
        callId: String,
        sdpOffer: String,
    ) {
        Log.d("CallManager") { "initiateCall: callId=$callId, callee=${calleePubKey.take(8)}, type=$callType, sdpLength=${sdpOffer.length}" }
        val result = factory.createCallOffer(sdpOffer, calleePubKey, callId, callType, signer)
        stateMutex.withLock {
            _state.value = CallState.Offering(callId, setOf(calleePubKey), callType)
            cancelAllPeerTimeouts()
            peersInvitedByUs.add(calleePubKey)
            schedulePeerTimeout(calleePubKey, callId)
            armRingingWatchdog(callId)
        }
        publishEvent(result.wrap)
        Log.d("CallManager") { "initiateCall: offer published, per-peer timeout started" }
    }

    // ---- Incoming call handling ----

    private fun onIncomingCallEvent(event: CallOfferEvent) {
        val callerPubKey = event.pubKey
        val callId = event.callId() ?: return
        val callType = event.callType() ?: CallType.VOICE

        Log.d("CallManager") { "onIncomingCallEvent: from=${callerPubKey.take(8)}, callId=$callId, type=$callType, sdpOfferLength=${event.sdpOffer().length}" }

        // User disabled calls in Settings — silently ignore new incoming
        // offers so the device does not ring. Mid-call signaling for calls
        // that are already in progress is still processed by the other
        // branches in onSignalingEvent so cleanup can complete normally.
        if (!isCallsEnabled()) {
            Log.d("CallManager") { "onIncomingCallEvent: calls disabled in settings — ignoring" }
            return
        }

        if (!isFollowing(callerPubKey)) {
            Log.d("CallManager") { "onIncomingCallEvent: caller not followed — ignoring" }
            return
        }

        if (callId in completedCallIds) {
            Log.d("CallManager") { "onIncomingCallEvent: callId=$callId already completed — ignoring stale offer" }
            return
        }

        val currentState = _state.value

        // Mid-call offer: same call-id, we're already in the call
        if (currentState is CallState.Connecting || currentState is CallState.Connected) {
            val currentCallId =
                when (currentState) {
                    is CallState.Connecting -> currentState.callId
                    is CallState.Connected -> currentState.callId
                }
            if (callId == currentCallId) {
                Log.d("CallManager") { "Mid-call offer from ${callerPubKey.take(8)} for current call — callee-to-callee" }
                // If this peer was in our pending set (we were waiting for
                // them to join), move them into the connected set and
                // cancel their watchdog timer — the offer is proof they're
                // in the call.
                when (currentState) {
                    is CallState.Connecting -> {
                        if (callerPubKey in currentState.pendingPeerPubKeys) {
                            _state.value =
                                currentState.copy(
                                    peerPubKeys = currentState.peerPubKeys + callerPubKey,
                                    pendingPeerPubKeys = currentState.pendingPeerPubKeys - callerPubKey,
                                )
                            cancelPeerTimeout(callerPubKey)
                        }
                    }

                    is CallState.Connected -> {
                        if (callerPubKey in currentState.pendingPeerPubKeys) {
                            _state.value =
                                currentState.copy(
                                    peerPubKeys = currentState.peerPubKeys + callerPubKey,
                                    pendingPeerPubKeys = currentState.pendingPeerPubKeys - callerPubKey,
                                )
                            cancelPeerTimeout(callerPubKey)
                        }
                    }
                }
                emitSessionEvent(CallSessionEvent.MidCallOfferReceived(callerPubKey, event.sdpOffer()))
                return
            }
        }

        if (currentState !is CallState.Idle) {
            scope.launch {
                logSignerErrors(TAG) {
                    val result = factory.createReject(callerPubKey, callId, "busy", signer = signer)
                    publishEvent(result.wrap)
                }
            }
            return
        }

        val groupMembers = event.groupMembers()

        _state.value =
            CallState.IncomingCall(
                callId = callId,
                callerPubKey = callerPubKey,
                groupMembers = groupMembers,
                callType = callType,
                sdpOffer = event.sdpOffer(),
            )
        armRingingWatchdog(callId)
        startTimeout(callId)
    }

    suspend fun acceptCall(sdpAnswer: String) {
        val current: CallState.IncomingCall
        val discovered: Set<HexKey>
        stateMutex.withLock {
            val s = _state.value
            if (s !is CallState.IncomingCall) {
                Log.d("CallManager") { "acceptCall: state is ${s::class.simpleName}, not IncomingCall — ignoring" }
                return
            }
            current = s

            discovered = discoveredCalleePeers.toSet()
            discoveredCalleePeers.clear()

            // Split group members into "known to be in the call" vs "still
            // waiting on". The caller is definitely in the call (they sent
            // us the offer). Peers whose answer we observed while ringing
            // are also confirmed. Everyone else is placed in
            // pendingPeerPubKeys with a per-peer watchdog timer so that
            // unresponsive group members are dropped from our local UI
            // after the same 30-second budget the caller uses — otherwise
            // we would wait for them forever (the caller's timeout hangup
            // is addressed only to the unresponsive peer and never reaches
            // the rest of us).
            val knownPeers = (discovered + current.callerPubKey) - signer.pubKey
            val pendingOnJoin = (current.peerPubKeys() - signer.pubKey) - knownPeers

            Log.d("CallManager") {
                "acceptCall: callId=${current.callId}, transitioning to Connecting, " +
                    "known=${knownPeers.size}, pending=${pendingOnJoin.size}, " +
                    "sdpAnswerLength=${sdpAnswer.length}"
            }
            _state.value =
                CallState.Connecting(
                    callId = current.callId,
                    peerPubKeys = knownPeers,
                    callType = current.callType,
                    pendingPeerPubKeys = pendingOnJoin,
                )
            cancelTimeout()
            disarmRingingWatchdog()
            armConnectingWatchdog(current.callId)
            // Local watchdog timers only — we are not the inviter for these
            // peers, so [handlePeerTimeout] will silently drop them without
            // publishing any hangup (see `peersInvitedByUs`).
            pendingOnJoin.forEach { schedulePeerTimeout(it, current.callId) }
        }

        val allMembers = current.groupMembers + signer.pubKey
        val otherMembers = allMembers - signer.pubKey
        Log.d("CallManager") { "acceptCall: publishing answer to ${otherMembers.size} recipients" }
        logSignerErrors(TAG) {
            val result = factory.createGroupCallAnswer(sdpAnswer, otherMembers, current.callId, signer)
            result.wraps.forEach { publishEvent(it) }

            // Notify our own other devices so they stop ringing. We wrap the
            // *same* signed answer event for our own pubkey and publish it as
            // an extra gift wrap. A sibling device in IncomingCall will observe
            // the self-answer and transition to Ended(ANSWERED_ELSEWHERE)
            // locally (see onCallAnswered). This call is the only place the
            // "answered elsewhere" signal is generated — without it, a second
            // logged-in device keeps ringing until its 60-second local timeout.
            //
            // The echo that arrives back at *this* device is ignored in
            // Connecting/Connected state by the self-answer guard at the top
            // of onCallAnswered, so it cannot disturb the live call.
            val selfWrap = EphemeralGiftWrapEvent.create(event = result.msg, recipientPubKey = signer.pubKey)
            publishEvent(selfWrap)
            Log.d("CallManager") { "acceptCall: answer published, now in Connecting state" }
        } ?: return endAfterSignerRefusal(current.callId, EndReason.REJECTED)

        // Trigger callee-to-callee mesh connections with peers we discovered
        // while still ringing (their answers arrived before we accepted).
        if (discovered.isNotEmpty()) {
            Log.d("CallManager") { "acceptCall: triggering mesh setup with ${discovered.size} discovered peers: ${discovered.map { it.take(8) }}" }
            for (peer in discovered) {
                emitSessionEvent(CallSessionEvent.NewPeerInGroupCall(peer))
            }
        }
    }

    suspend fun rejectCall() {
        Log.d("CallManager") { "rejectCall: enter state=${_state.value::class.simpleName}" }
        val current: CallState.IncomingCall
        stateMutex.withLock {
            val s = _state.value
            if (s !is CallState.IncomingCall) {
                Log.d("CallManager") { "rejectCall: state is ${s::class.simpleName}, not IncomingCall — ignoring" }
                return
            }
            current = s
            transitionToEnded(current.callId, current.peerPubKeys(), EndReason.REJECTED)
            Log.d("CallManager") { "rejectCall: transitioned to Ended, publishing reject events" }
        }

        val otherMembers = current.groupMembers - signer.pubKey
        logSignerErrors(TAG) {
            val result = factory.createGroupReject(otherMembers, current.callId, signer = signer)
            result.wraps.forEach { publishEvent(it) }

            // Notify our own other devices so they stop ringing. See the
            // matching comment in acceptCall — sibling devices in IncomingCall
            // pick up this echo and transition to Ended(REJECTED) locally.
            val selfWrap = EphemeralGiftWrapEvent.create(event = result.msg, recipientPubKey = signer.pubKey)
            publishEvent(selfWrap)
        }
    }

    private fun onCallAnswered(event: CallAnswerEvent) {
        val current = _state.value
        val callId = event.callId()
        val answeringPeer = event.pubKey

        Log.d("CallManager") {
            "onCallAnswered: from=${answeringPeer.take(8)}, callId=$callId, " +
                "currentState=${current::class.simpleName}, sdpAnswerLength=${event.sdpAnswer().length}"
        }

        // Self-answer: only meaningful as "answered elsewhere" in IncomingCall.
        // In all other states it's our own echo from the relay — ignore it.
        if (answeringPeer == signer.pubKey) {
            if (current is CallState.IncomingCall && callId == current.callId) {
                Log.d("CallManager") { "onCallAnswered: self-answer detected in IncomingCall — answered elsewhere" }
                transitionToEnded(current.callId, current.peerPubKeys(), EndReason.ANSWERED_ELSEWHERE)
            } else {
                Log.d("CallManager") { "onCallAnswered: ignoring self-answer echo in ${current::class.simpleName}" }
            }
            return
        }

        when (current) {
            is CallState.Offering -> {
                if (callId != current.callId) {
                    Log.d("CallManager") { "onCallAnswered: callId mismatch (got=$callId, expected=${current.callId})" }
                    return
                }
                val pending = current.peerPubKeys - answeringPeer
                _state.value =
                    CallState.Connecting(
                        current.callId,
                        setOf(answeringPeer),
                        current.callType,
                        pendingPeerPubKeys = pending,
                    )
                // The answered peer no longer needs its invite timer. Peers
                // still in `pending` keep theirs (scheduled in beginOffering).
                cancelPeerTimeout(answeringPeer)
                disarmRingingWatchdog()
                armConnectingWatchdog(current.callId)
                Log.d("CallManager") { "onCallAnswered: Offering -> Connecting, forwarding answer to CallSession" }
                emitSessionEvent(CallSessionEvent.AnswerReceived(event))
            }

            is CallState.Connecting -> {
                if (callId != current.callId) return
                when {
                    answeringPeer in current.pendingPeerPubKeys -> {
                        _state.value =
                            current.copy(
                                peerPubKeys = current.peerPubKeys + answeringPeer,
                                pendingPeerPubKeys = current.pendingPeerPubKeys - answeringPeer,
                            )
                        cancelPeerTimeout(answeringPeer)
                    }

                    answeringPeer !in current.peerPubKeys -> {
                        // Mid-call join while we're still handshaking: another
                        // participant invited a new peer and that peer
                        // broadcast their acceptance to us. Expand our group
                        // membership so the UI reflects the new peer.
                        _state.value =
                            current.copy(
                                peerPubKeys = current.peerPubKeys + answeringPeer,
                            )
                    }
                }
                // Forward to CallController — it routes to the correct PeerSession
                // and internally triggers callee-to-callee mesh setup if needed.
                Log.d("CallManager") { "onCallAnswered: additional peer ${answeringPeer.take(8)} in Connecting, forwarding to CallController" }
                emitSessionEvent(CallSessionEvent.AnswerReceived(event))
            }

            is CallState.IncomingCall -> {
                if (callId != current.callId) return
                // Another group member answered — remember them for mesh setup
                // after we accept the call ourselves.
                Log.d("CallManager") { "onCallAnswered: peer ${answeringPeer.take(8)} answered while we're still ringing, storing for later mesh" }
                discoveredCalleePeers.add(answeringPeer)
            }

            is CallState.Connected -> {
                if (callId != current.callId) return
                when {
                    answeringPeer in current.pendingPeerPubKeys -> {
                        _state.value =
                            current.copy(
                                peerPubKeys = current.peerPubKeys + answeringPeer,
                                pendingPeerPubKeys = current.pendingPeerPubKeys - answeringPeer,
                            )
                        cancelPeerTimeout(answeringPeer)
                    }

                    answeringPeer !in current.peerPubKeys -> {
                        // Mid-call join: another participant invited a new
                        // peer and that peer broadcast their acceptance to us.
                        // Expand our group membership so the UI reflects the
                        // new peer. CallController will unconditionally
                        // initiate a mesh offer to them (the invitee stays
                        // passive).
                        _state.value =
                            current.copy(
                                peerPubKeys = current.peerPubKeys + answeringPeer,
                            )
                    }
                }
                // Forward to CallController — it routes to the correct PeerSession
                // and internally triggers callee-to-callee mesh setup if needed.
                Log.d("CallManager") { "onCallAnswered: peer ${answeringPeer.take(8)} answer in Connected, forwarding to CallController" }
                emitSessionEvent(CallSessionEvent.AnswerReceived(event))
            }

            else -> {
                return
            }
        }
    }

    private fun onCallRejected(event: CallRejectEvent) {
        val current = _state.value
        val callId = event.callId()
        val rejectingPeer = event.pubKey

        // Self-reject: only meaningful as "rejected elsewhere" while we are
        // still ringing (IncomingCall).  In every other state it is our own
        // echo from a relay after a sibling device rejected a call that we
        // have already accepted and are now actively in — treating it as a
        // peer rejection would drop ourselves from the call (PeerLeft
        // would dispose our own PeerConnection, killing the live audio on
        // the device that picked up).  Just drop the echo.
        if (rejectingPeer == signer.pubKey) {
            if (current is CallState.IncomingCall && callId == current.callId) {
                Log.d("CallManager") { "onCallRejected: self-reject detected in IncomingCall — rejected elsewhere" }
                transitionToEnded(current.callId, current.peerPubKeys(), EndReason.REJECTED)
            } else {
                Log.d("CallManager") { "onCallRejected: ignoring self-reject echo in ${current::class.simpleName}" }
            }
            return
        }

        when (current) {
            is CallState.Offering -> {
                if (callId != current.callId) return
                cancelPeerTimeout(rejectingPeer)
                val remaining = current.peerPubKeys - rejectingPeer
                if (remaining.isEmpty()) {
                    transitionToEnded(current.callId, current.peerPubKeys, EndReason.PEER_REJECTED)
                } else {
                    _state.value = current.copy(peerPubKeys = remaining)
                    emitSessionEvent(CallSessionEvent.PeerLeft(rejectingPeer))
                }
            }

            is CallState.Connecting -> {
                if (callId != current.callId) return
                cancelPeerTimeout(rejectingPeer)
                _state.value =
                    current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys - rejectingPeer)
                emitSessionEvent(CallSessionEvent.PeerLeft(rejectingPeer))
            }

            is CallState.Connected -> {
                if (callId != current.callId) return
                cancelPeerTimeout(rejectingPeer)
                _state.value =
                    current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys - rejectingPeer)
                emitSessionEvent(CallSessionEvent.PeerLeft(rejectingPeer))
            }

            is CallState.IncomingCall -> {
                if (callId != current.callId) return
                if (rejectingPeer == current.callerPubKey) {
                    // Caller rejected/cancelled the call
                    transitionToEnded(current.callId, current.groupMembers, EndReason.PEER_REJECTED)
                } else {
                    // Another group member rejected — remove them from the group
                    val remaining = current.groupMembers - rejectingPeer
                    if (remaining.size <= 1) {
                        // Only us left, no one to call with
                        transitionToEnded(current.callId, current.groupMembers, EndReason.PEER_REJECTED)
                    } else {
                        _state.value = current.copy(groupMembers = remaining)
                    }
                }
            }

            else -> {
                return
            }
        }
    }

    private fun onIceCandidate(event: CallIceCandidateEvent) {
        emitSessionEvent(CallSessionEvent.IceCandidateReceived(event))
    }

    private fun onRenegotiate(event: CallRenegotiateEvent) {
        val current = _state.value
        val callId = event.callId()
        val currentCallId =
            when (current) {
                is CallState.Connecting -> current.callId
                is CallState.Connected -> current.callId
                else -> return
            }
        if (callId != currentCallId) return
        if (!_renegotiationEvents.tryEmit(event)) {
            Log.e("CallManager") { "renegotiationEvents buffer overflow — dropped renegotiation from ${event.pubKey.take(8)}" }
        }
    }

    suspend fun sendRenegotiation(
        sdpOffer: String,
        peerPubKey: HexKey,
    ) {
        val callId = currentCallId() ?: return
        val peerPubKeys = currentPeerPubKeys() ?: return
        logSignerErrors(TAG) {
            val result = factory.createRenegotiate(sdpOffer, peerPubKey, peerPubKeys, callId, signer)
            publishEvent(result.wrap)
        }
    }

    suspend fun sendRenegotiationAnswer(
        sdpAnswer: String,
        peerPubKey: HexKey,
    ) {
        val callId = currentCallId() ?: return
        val peerPubKeys = currentPeerPubKeys() ?: return
        logSignerErrors(TAG) {
            val result = factory.createCallAnswer(sdpAnswer, peerPubKey, peerPubKeys, callId, signer)
            publishEvent(result.wrap)
        }
    }

    suspend fun onPeerConnected() {
        stateMutex.withLock {
            val current = _state.value
            if (current !is CallState.Connecting) {
                Log.d("CallManager") { "onPeerConnected: state is ${current::class.simpleName}, not Connecting — ignoring" }
                return
            }

            Log.d("CallManager") { "onPeerConnected: Connecting -> Connected! callId=${current.callId}" }
            disarmConnectingWatchdog()
            _state.value =
                CallState.Connected(
                    callId = current.callId,
                    peerPubKeys = current.peerPubKeys,
                    callType = current.callType,
                    startedAtEpoch = TimeUtils.now(),
                    pendingPeerPubKeys = current.pendingPeerPubKeys,
                )
        }
    }

    suspend fun invitePeer(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        val current = _state.value
        val callId: String
        val callType: CallType
        val existingMembers: Set<HexKey>
        when (current) {
            is CallState.Connecting -> {
                callId = current.callId
                callType = current.callType
                existingMembers = current.peerPubKeys + current.pendingPeerPubKeys
                _state.value = current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys + peerPubKey)
            }

            is CallState.Connected -> {
                callId = current.callId
                callType = current.callType
                existingMembers = current.allPeerPubKeys
                _state.value = current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys + peerPubKey)
            }

            else -> {
                return
            }
        }

        // Start the per-peer invite timer. If the invitee does not answer
        // within PEER_INVITE_TIMEOUT_MS, they are dropped from the call.
        // We sent the offer, so a timeout must also publish a hangup.
        peersInvitedByUs.add(peerPubKey)
        schedulePeerTimeout(peerPubKey, callId)

        val allMembers = existingMembers + peerPubKey + signer.pubKey
        logSignerErrors(TAG) {
            val result = factory.createCallOffer(sdpOffer, peerPubKey, allMembers, callId, callType, signer)
            publishEvent(result.wrap)
        } ?: dropPeerAfterSignerRefusal(peerPubKey, callId)
    }

    suspend fun hangup() {
        val peerPubKeys: Set<HexKey>
        val callId: String
        stateMutex.withLock {
            when (val current = _state.value) {
                is CallState.Offering -> {
                    peerPubKeys = current.peerPubKeys
                    callId = current.callId
                }

                is CallState.Connecting -> {
                    peerPubKeys = current.peerPubKeys + current.pendingPeerPubKeys
                    callId = current.callId
                }

                is CallState.Connected -> {
                    peerPubKeys = current.allPeerPubKeys
                    callId = current.callId
                }

                else -> {
                    return
                }
            }

            // Transition immediately so the UI stops ringing/ringback before
            // the (potentially slow) signing + relay publish completes.
            transitionToEnded(callId, peerPubKeys, EndReason.HANGUP)
        }

        logSignerErrors(TAG) {
            val result = factory.createGroupHangup(peerPubKeys, callId, signer = signer)
            result.wraps.forEach { publishEvent(it) }
        }
    }

    private suspend fun onPeerHangup(event: CallHangupEvent) {
        val current = _state.value
        val callId = event.callId() ?: return
        val leavingPeer = event.pubKey

        Log.d("CallManager") { "onPeerHangup: from=${leavingPeer.take(8)}, callId=$callId, state=${current::class.simpleName}" }

        when (current) {
            is CallState.Connected -> {
                if (callId != current.callId) return
                cancelPeerTimeout(leavingPeer)
                val connectedRemaining = current.peerPubKeys - leavingPeer
                val pendingRemaining = current.pendingPeerPubKeys - leavingPeer
                if (connectedRemaining.isEmpty()) {
                    // No connected peers left. Pending members we never
                    // heard from can't sustain the call on their own, so
                    // terminate — otherwise the caller (the only other
                    // participant we were actually talking to) has hung up
                    // and we'd be left staring at a black screen waiting
                    // for someone who may never join.
                    Log.d("CallManager") {
                        "onPeerHangup: last connected peer left, ending call (pendingRemaining=${pendingRemaining.size})"
                    }
                    publishHangupToInvitedPendingPeers(callId, pendingRemaining)
                    transitionToEnded(callId, current.allPeerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    Log.d("CallManager") { "onPeerHangup: ${leavingPeer.take(8)} left, remaining=${connectedRemaining.map { it.take(8) }}, pending=${pendingRemaining.map { it.take(8) }}" }
                    _state.value =
                        current.copy(
                            peerPubKeys = connectedRemaining,
                            pendingPeerPubKeys = pendingRemaining,
                        )
                    emitSessionEvent(CallSessionEvent.PeerLeft(leavingPeer))
                }
            }

            is CallState.Connecting -> {
                if (callId != current.callId) return
                cancelPeerTimeout(leavingPeer)
                val connectedRemaining = current.peerPubKeys - leavingPeer
                val pendingRemaining = current.pendingPeerPubKeys - leavingPeer
                if (connectedRemaining.isEmpty()) {
                    // Same rule as Connected: without a single connected
                    // peer the call can't keep running. Pending members
                    // are dropped regardless of how many are left.
                    Log.d("CallManager") {
                        "onPeerHangup: last connected peer left during connecting, ending call (pendingRemaining=${pendingRemaining.size})"
                    }
                    publishHangupToInvitedPendingPeers(callId, pendingRemaining)
                    transitionToEnded(callId, current.peerPubKeys + current.pendingPeerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    Log.d("CallManager") { "onPeerHangup: ${leavingPeer.take(8)} left during connecting, remaining=${connectedRemaining.map { it.take(8) }}" }
                    _state.value =
                        current.copy(
                            peerPubKeys = connectedRemaining,
                            pendingPeerPubKeys = pendingRemaining,
                        )
                    emitSessionEvent(CallSessionEvent.PeerLeft(leavingPeer))
                }
            }

            is CallState.Offering -> {
                if (callId != current.callId) return
                cancelPeerTimeout(leavingPeer)
                val remaining = current.peerPubKeys - leavingPeer
                if (remaining.isEmpty()) {
                    transitionToEnded(callId, current.peerPubKeys, EndReason.PEER_HANGUP)
                } else {
                    _state.value = current.copy(peerPubKeys = remaining)
                    emitSessionEvent(CallSessionEvent.PeerLeft(leavingPeer))
                }
            }

            is CallState.IncomingCall -> {
                if (callId != current.callId) return
                if (leavingPeer == current.callerPubKey) {
                    transitionToEnded(callId, current.groupMembers, EndReason.PEER_HANGUP)
                } else {
                    val remaining = current.groupMembers - leavingPeer
                    if (remaining.size <= 1) {
                        transitionToEnded(callId, current.groupMembers, EndReason.PEER_HANGUP)
                    } else {
                        _state.value = current.copy(groupMembers = remaining)
                    }
                }
            }

            else -> {
                return
            }
        }
    }

    /**
     * When a call is ending because its last connected peer left, publish
     * a CallHangup to every *pending* peer we had personally invited so
     * their devices stop ringing. Peers we did NOT invite (e.g. callee-side
     * group members) are the caller's responsibility and are left alone.
     *
     * Must be called while still inside the CallManager state mutex and
     * BEFORE [transitionToEnded], which clears [peersInvitedByUs].
     */
    private suspend fun publishHangupToInvitedPendingPeers(
        callId: String,
        pendingPeers: Set<HexKey>,
    ) {
        val invitedPending = peersInvitedByUs.intersect(pendingPeers)
        if (invitedPending.isEmpty()) return
        Log.d("CallManager") {
            "publishHangupToInvitedPendingPeers: notifying ${invitedPending.size} invited pending peers to stop ringing"
        }
        for (peer in invitedPending) {
            logSignerErrors(TAG) {
                val result = factory.createHangup(peer, callId, signer = signer)
                publishEvent(result.wrap)
            }
        }
    }

    suspend fun onSignalingEvent(event: Event) {
        if (isEventTooOld(event)) {
            Log.d("CallManager") { "Discarding old event kind=${event.kind} age=${TimeUtils.now() - event.createdAt}s" }
            return
        }

        stateMutex.withLock {
            if (event.id in processedEventIds) return
            cappedAdd(processedEventIds, event.id, MAX_PROCESSED_EVENT_IDS)

            // Filter out our own ICE candidates and hangups echoed back from relays.
            // These are never useful: ICE candidates are for the remote peer, and
            // hangups are already handled locally by hangup() → transitionToEnded.
            // Self-answers and self-rejects are NOT filtered here because they serve
            // as "answered/rejected elsewhere" signals when in IncomingCall state.
            if (event.pubKey == signer.pubKey && (event is CallIceCandidateEvent || event is CallHangupEvent)) {
                Log.d("CallManager") { "Ignoring self-event kind=${event.kind} id=${event.id.take(8)}" }
                return
            }

            Log.d("CallManager") { "Processing signaling event kind=${event.kind} id=${event.id.take(8)} state=${_state.value::class.simpleName}" }

            // Record call-ids from termination signals so that a later offer
            // for the same call is recognised as stale (common after app restart
            // when relay replays events out of order).  For answers, only
            // *self*-answers count as a termination signal: they mean another
            // of our devices picked up, so ringing on this device should never
            // start for the same call-id.  Peer answers to our own offer are
            // not terminal — they are what moves us into Connecting.
            val terminatedCallId =
                when (event) {
                    is CallHangupEvent -> event.callId()
                    is CallRejectEvent -> event.callId()
                    is CallAnswerEvent -> if (event.pubKey == signer.pubKey) event.callId() else null
                    else -> null
                }
            if (terminatedCallId != null) {
                cappedAdd(completedCallIds, terminatedCallId, MAX_COMPLETED_CALL_IDS)
            }

            when (event) {
                is CallOfferEvent -> onIncomingCallEvent(event)
                is CallAnswerEvent -> onCallAnswered(event)
                is CallRejectEvent -> onCallRejected(event)
                is CallHangupEvent -> onPeerHangup(event)
                is CallIceCandidateEvent -> onIceCandidate(event)
                is CallRenegotiateEvent -> onRenegotiate(event)
            }
        }
    }

    fun currentCallId(): String? =
        when (val s = _state.value) {
            is CallState.Offering -> s.callId
            is CallState.IncomingCall -> s.callId
            is CallState.Connecting -> s.callId
            is CallState.Connected -> s.callId
            else -> null
        }

    fun currentPeerPubKey(): HexKey? = currentPeerPubKeys()?.firstOrNull()

    fun currentPeerPubKeys(): Set<HexKey>? =
        when (val s = _state.value) {
            is CallState.Offering -> s.peerPubKeys
            is CallState.IncomingCall -> s.peerPubKeys()
            is CallState.Connecting -> s.peerPubKeys + s.pendingPeerPubKeys
            is CallState.Connected -> s.allPeerPubKeys
            else -> null
        }

    fun currentCallType(): CallType? =
        when (val s = _state.value) {
            is CallState.Offering -> s.callType
            is CallState.IncomingCall -> s.callType
            is CallState.Connecting -> s.callType
            is CallState.Connected -> s.callType
            else -> null
        }

    fun isGroupCall(): Boolean = (currentPeerPubKeys()?.size ?: 0) > 1

    fun reset() {
        _state.value = CallState.Idle
        cancelTimeout()
        cancelAllPeerTimeouts()
        disarmRingingWatchdog()
        disarmConnectingWatchdog()
        resetJob?.cancel()
        resetJob = null
        processedEventIds.clear()
        discoveredCalleePeers.clear()
        // Note: completedCallIds is intentionally NOT cleared here so that
        // stale offers for previously-ended calls remain blocked.
    }

    private fun transitionToEnded(
        callId: String,
        peerPubKeys: Set<HexKey>,
        reason: EndReason,
    ) {
        Log.d("CallManager") { "transitionToEnded: callId=$callId reason=$reason peers=${peerPubKeys.size}" }
        cappedAdd(completedCallIds, callId, MAX_COMPLETED_CALL_IDS)
        discoveredCalleePeers.clear()
        _state.value = CallState.Ended(callId, peerPubKeys, reason)
        cancelTimeout()
        cancelAllPeerTimeouts()
        disarmRingingWatchdog()
        disarmConnectingWatchdog()
        resetJob?.cancel()
        resetJob =
            scope.launch {
                delay(ENDED_DISPLAY_MS)
                if (_state.value is CallState.Ended) {
                    _state.value = CallState.Idle
                    processedEventIds.clear()
                }
            }
    }

    // ---- Ringing watchdog ----

    /**
     * Arms the watchdog for [callId]. If the state is still
     * `IncomingCall(callId)` or `Offering(callId)` when the watchdog
     * fires, forces `Ended(TIMEOUT)` so nothing can keep ringing
     * indefinitely. Calling this replaces any previously armed watchdog.
     */
    private fun armRingingWatchdog(callId: String) {
        ringingWatchdogJob?.cancel()
        ringingWatchdogJob =
            watchdogScope.launch {
                delay(RINGING_WATCHDOG_MS)
                stateMutex.withLock {
                    val cur = _state.value
                    val hit =
                        (cur is CallState.IncomingCall && cur.callId == callId) ||
                            (cur is CallState.Offering && cur.callId == callId)
                    if (hit) {
                        Log.d("CallManager") { "Ringing watchdog fired for $callId — forcing Ended(TIMEOUT)" }
                        val peers =
                            when (cur) {
                                is CallState.IncomingCall -> cur.peerPubKeys()
                                is CallState.Offering -> cur.peerPubKeys
                            }
                        transitionToEnded(callId, peers, EndReason.TIMEOUT)
                    }
                }
            }
    }

    private fun disarmRingingWatchdog() {
        ringingWatchdogJob?.cancel()
        ringingWatchdogJob = null
    }

    // ---- Connecting watchdog ----

    /**
     * Arms a timer for [callId] in `Connecting` state. If ICE negotiation
     * does not promote the state to `Connected` within
     * [CONNECTING_TIMEOUT_MS], the call ends with [EndReason.TIMEOUT].
     * Disarmed when entering `Connected`, `Ended`, or `Idle`.
     */
    private fun armConnectingWatchdog(callId: String) {
        connectingWatchdogJob?.cancel()
        connectingWatchdogJob =
            watchdogScope.launch {
                delay(CONNECTING_TIMEOUT_MS)
                stateMutex.withLock {
                    val cur = _state.value
                    if (cur is CallState.Connecting && cur.callId == callId) {
                        Log.d("CallManager") { "Connecting watchdog fired for $callId — forcing Ended(TIMEOUT)" }
                        transitionToEnded(callId, cur.peerPubKeys + cur.pendingPeerPubKeys, EndReason.TIMEOUT)
                    }
                }
            }
    }

    private fun disarmConnectingWatchdog() {
        connectingWatchdogJob?.cancel()
        connectingWatchdogJob = null
    }

    /**
     * Cancels all long-lived coroutines owned by this manager. Called when
     * the owning account scope is torn down (logout / process shutdown).
     */
    fun dispose() {
        disarmRingingWatchdog()
        disarmConnectingWatchdog()
        watchdogScope.cancel()
    }

    // ---- Per-peer invite timeout ----

    /**
     * Starts a 30-second timer for [peerPubKey]. If the peer has not answered
     * by the time it fires, [handlePeerTimeout] drops them from the current
     * group call and publishes a CallHangup to them so their device stops
     * ringing.
     *
     * Safe to call multiple times for the same peer — any previous timer is
     * cancelled first.
     */
    private fun schedulePeerTimeout(
        peerPubKey: HexKey,
        callId: String,
    ) {
        perPeerTimeoutJobs.remove(peerPubKey)?.cancel()
        perPeerTimeoutJobs[peerPubKey] =
            scope.launch {
                delay(PEER_INVITE_TIMEOUT_MS)
                handlePeerTimeout(peerPubKey, callId)
            }
    }

    /** Cancels the per-peer timer for [peerPubKey], if any. */
    private fun cancelPeerTimeout(peerPubKey: HexKey) {
        perPeerTimeoutJobs.remove(peerPubKey)?.cancel()
        peersInvitedByUs.remove(peerPubKey)
    }

    /** Cancels every per-peer timer. Called on terminal state transitions. */
    private fun cancelAllPeerTimeouts() {
        perPeerTimeoutJobs.values.forEach { it.cancel() }
        perPeerTimeoutJobs.clear()
        peersInvitedByUs.clear()
    }

    /**
     * Handles a per-peer timeout firing. Drops the peer from the current
     * group call state and publishes a CallHangup to them.
     *
     * - In [CallState.Offering] the peer is removed from `peerPubKeys`; if no
     *   peers remain, the whole call ends with [EndReason.TIMEOUT].
     * - In [CallState.Connecting] the peer is removed from `pendingPeerPubKeys`;
     *   if that leaves nobody connected AND no more pending, the call ends
     *   with [EndReason.TIMEOUT]. Otherwise the call continues with the
     *   already-connected peers.
     * - In [CallState.Connected] the peer is removed from `pendingPeerPubKeys`;
     *   at least one other peer is connected by definition, so the call
     *   always continues.
     *
     * Emits [CallSessionEvent.PeerLeft] so the CallSession disposes the per-peer
     * PeerConnection (and any pending ICE buffers) for the dropped peer.
     */
    private suspend fun handlePeerTimeout(
        peerPubKey: HexKey,
        callId: String,
    ) {
        if (dropPendingPeer(peerPubKey, callId, EndReason.TIMEOUT)) {
            logSignerErrors(TAG) {
                val result = factory.createHangup(peerPubKey, callId, signer = signer)
                publishEvent(result.wrap)
            }
        }
    }

    /**
     * The signer refused the offer to [peerPubKey] (the user rejected it in their signer app, it
     * timed out, ...), so that peer never got one. Drops them as if they had timed out, but without
     * the hangup: there is nothing to hang up, and signing one would only prompt the user again.
     * The last peer dropped ends the call.
     */
    private suspend fun dropPeerAfterSignerRefusal(
        peerPubKey: HexKey,
        callId: String,
    ) {
        dropPendingPeer(peerPubKey, callId, EndReason.HANGUP, cancelTimer = true)
    }

    /**
     * The signer refused our answer, so the call never started on our side. Ends it locally without
     * signaling anyone: signing a reject now would only prompt the user again.
     */
    private suspend fun endAfterSignerRefusal(
        callId: String,
        reason: EndReason,
    ) = stateMutex.withLock {
        if (currentCallId() == callId) {
            transitionToEnded(callId, currentPeerPubKeys() ?: emptySet(), reason)
        }
    }

    /**
     * Drops [peerPubKey], who has not joined yet, from the current call; when nobody is left the
     * call ends with [endReason]. Returns whether we invited that peer, i.e. whether their device may
     * still be ringing and should get a hangup. [cancelTimer] is false when the peer's own timeout
     * calls this: cancelling that job would cancel the caller.
     */
    private suspend fun dropPendingPeer(
        peerPubKey: HexKey,
        callId: String,
        endReason: EndReason,
        cancelTimer: Boolean = false,
    ): Boolean {
        var shouldPublishHangup = false
        stateMutex.withLock {
            val timer = perPeerTimeoutJobs.remove(peerPubKey)
            if (cancelTimer) timer?.cancel()
            // Only publish a hangup if we were the peer's inviter. Pure
            // "watchdog" timers started by a callee for group members who
            // never joined must NOT publish anything — terminating the
            // invitee's ringing is the caller's responsibility.
            val wasInvitedByUs = peersInvitedByUs.remove(peerPubKey)
            when (val current = _state.value) {
                is CallState.Offering -> {
                    if (callId != current.callId) return@withLock
                    if (peerPubKey !in current.peerPubKeys) return@withLock
                    Log.d("CallManager") { "Per-peer timeout: dropping ${peerPubKey.take(8)} from Offering" }
                    shouldPublishHangup = wasInvitedByUs
                    val remaining = current.peerPubKeys - peerPubKey
                    if (remaining.isEmpty()) {
                        transitionToEnded(current.callId, current.peerPubKeys, endReason)
                    } else {
                        _state.value = current.copy(peerPubKeys = remaining)
                        emitSessionEvent(CallSessionEvent.PeerLeft(peerPubKey))
                    }
                }

                is CallState.Connecting -> {
                    if (callId != current.callId) return@withLock
                    if (peerPubKey !in current.pendingPeerPubKeys) return@withLock
                    Log.d("CallManager") { "Per-peer timeout: dropping ${peerPubKey.take(8)} from Connecting (invitedByUs=$wasInvitedByUs)" }
                    shouldPublishHangup = wasInvitedByUs
                    val newPending = current.pendingPeerPubKeys - peerPubKey
                    if (current.peerPubKeys.isEmpty() && newPending.isEmpty()) {
                        transitionToEnded(
                            current.callId,
                            current.peerPubKeys + current.pendingPeerPubKeys,
                            endReason,
                        )
                    } else {
                        _state.value = current.copy(pendingPeerPubKeys = newPending)
                        emitSessionEvent(CallSessionEvent.PeerLeft(peerPubKey))
                    }
                }

                is CallState.Connected -> {
                    if (callId != current.callId) return@withLock
                    if (peerPubKey !in current.pendingPeerPubKeys) return@withLock
                    Log.d("CallManager") { "Per-peer timeout: dropping ${peerPubKey.take(8)} from Connected (invitedByUs=$wasInvitedByUs)" }
                    shouldPublishHangup = wasInvitedByUs
                    _state.value = current.copy(pendingPeerPubKeys = current.pendingPeerPubKeys - peerPubKey)
                    emitSessionEvent(CallSessionEvent.PeerLeft(peerPubKey))
                }

                else -> {
                    return@withLock
                }
            }
        }

        return shouldPublishHangup
    }

    private fun startTimeout(callId: String) {
        cancelTimeout()
        timeoutJob =
            scope.launch {
                delay(CALL_TIMEOUT_MS)
                val peerPubKeys: Set<HexKey>
                val wasOffering: Boolean
                stateMutex.withLock {
                    val current = _state.value
                    val currentCallId =
                        when (current) {
                            is CallState.Offering -> current.callId
                            is CallState.IncomingCall -> current.callId
                            else -> null
                        }
                    if (currentCallId != callId) return@launch
                    peerPubKeys =
                        when (current) {
                            is CallState.Offering -> current.peerPubKeys
                            is CallState.IncomingCall -> current.peerPubKeys()
                            else -> return@launch
                        }
                    wasOffering = current is CallState.Offering
                    transitionToEnded(callId, peerPubKeys, EndReason.TIMEOUT)
                }
                // Notify peers so their phones stop ringing immediately
                // instead of waiting for their own 60-second timeout.
                if (wasOffering && peerPubKeys.isNotEmpty()) {
                    logSignerErrors(TAG) {
                        val result = factory.createGroupHangup(peerPubKeys, callId, signer = signer)
                        result.wraps.forEach { publishEvent(it) }
                    }
                }
            }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
}

private fun CallState.IncomingCall.peerPubKeys(): Set<HexKey> = groupMembers
