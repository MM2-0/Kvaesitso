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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType

@Stable
@Immutable
sealed interface CallState {
    @Stable
    data object Idle : CallState

    @Stable
    data class Offering(
        val callId: String,
        val peerPubKeys: Set<HexKey>,
        val callType: CallType,
    ) : CallState

    @Stable
    data class IncomingCall(
        val callId: String,
        val callerPubKey: HexKey,
        val groupMembers: Set<HexKey>,
        val callType: CallType,
        val sdpOffer: String,
    ) : CallState

    @Stable
    data class Connecting(
        val callId: String,
        val peerPubKeys: Set<HexKey>,
        val callType: CallType,
        val pendingPeerPubKeys: Set<HexKey> = emptySet(),
    ) : CallState

    @Stable
    data class Connected(
        val callId: String,
        val peerPubKeys: Set<HexKey>,
        val callType: CallType,
        val startedAtEpoch: Long,
        val pendingPeerPubKeys: Set<HexKey> = emptySet(),
    ) : CallState {
        /** All peers: connected + still pending. */
        val allPeerPubKeys: Set<HexKey> get() = peerPubKeys + pendingPeerPubKeys
    }

    @Stable
    data class Ended(
        val callId: String,
        val peerPubKeys: Set<HexKey>,
        val reason: EndReason,
    ) : CallState
}

enum class EndReason {
    HANGUP,
    REJECTED,
    TIMEOUT,
    ERROR,
    PEER_HANGUP,
    PEER_REJECTED,
    ANSWERED_ELSEWHERE,
}
