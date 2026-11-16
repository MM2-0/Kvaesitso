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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent

/**
 * Events emitted by [CallManager] that the Activity-scoped `CallSession`
 * must handle. Delivered via `SharedFlow` so the collector can
 * (re)subscribe across Activity restarts without missing buffered events.
 */
sealed interface CallSessionEvent {
    /** A peer answered our offer (or we received an answer in a group call). */
    data class AnswerReceived(
        val event: CallAnswerEvent,
    ) : CallSessionEvent

    /** An ICE candidate arrived from a peer. */
    data class IceCandidateReceived(
        val event: CallIceCandidateEvent,
    ) : CallSessionEvent

    /** A new peer joined the group call and needs callee-to-callee mesh setup. */
    data class NewPeerInGroupCall(
        val peerPubKey: HexKey,
    ) : CallSessionEvent

    /** A mid-call offer arrived from another callee in a group call. */
    data class MidCallOfferReceived(
        val peerPubKey: HexKey,
        val sdpOffer: String,
    ) : CallSessionEvent

    /** A peer left the call (hangup/reject/timeout) but the call continues. */
    data class PeerLeft(
        val peerPubKey: HexKey,
    ) : CallSessionEvent
}
