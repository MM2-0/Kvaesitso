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

/**
 * Represents a single ICE candidate received from a peer.
 * Platform-neutral — does not depend on org.webrtc.
 */
data class IceCandidateData(
    val sdp: String,
    val sdpMid: String,
    val sdpMLineIndex: Int,
)

/**
 * Represents the signaling state of a peer connection.
 * Maps 1:1 with WebRTC's PeerConnection.SignalingState.
 */
enum class SignalingState {
    STABLE,
    HAVE_LOCAL_OFFER,
    HAVE_REMOTE_OFFER,
    HAVE_LOCAL_PRANSWER,
    HAVE_REMOTE_PRANSWER,
    CLOSED,
}

enum class SdpType {
    OFFER,
    ANSWER,
}

/**
 * Abstraction over a single peer connection's signaling operations.
 * Implemented by the platform-specific WebRTC wrapper (e.g. WebRtcPeerSessionAdapter).
 */
interface PeerSession {
    fun getSignalingState(): SignalingState?

    fun setRemoteDescription(
        type: SdpType,
        sdp: String,
    )

    fun addIceCandidate(candidate: IceCandidateData)

    fun createOffer(onSdpCreated: (String) -> Unit)

    fun createAnswer(onSdpCreated: (String) -> Unit)

    fun rollback(onDone: () -> Unit)

    fun dispose()
}
