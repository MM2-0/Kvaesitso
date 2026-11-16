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
 * Fake PeerSession that records all operations for test assertions.
 * Simulates WebRTC PeerConnection signaling state transitions
 * without native libraries.
 */
class FakePeerSession(
    private var signalingState: SignalingState = SignalingState.STABLE,
) : PeerSession {
    val addedCandidates = mutableListOf<IceCandidateData>()
    var lastRemoteDescription: Pair<SdpType, String>? = null
    var rolledBack = false
    var disposed = false
    var lastCreatedOffer: String? = null
    var lastCreatedAnswer: String? = null

    override fun getSignalingState(): SignalingState = signalingState

    override fun setRemoteDescription(
        type: SdpType,
        sdp: String,
    ) {
        lastRemoteDescription = type to sdp
        signalingState =
            when (type) {
                SdpType.ANSWER -> SignalingState.STABLE
                SdpType.OFFER -> SignalingState.HAVE_REMOTE_OFFER
            }
    }

    override fun addIceCandidate(candidate: IceCandidateData) {
        addedCandidates.add(candidate)
    }

    override fun createOffer(onSdpCreated: (String) -> Unit) {
        signalingState = SignalingState.HAVE_LOCAL_OFFER
        val sdp = "fake-offer-sdp"
        lastCreatedOffer = sdp
        onSdpCreated(sdp)
    }

    override fun createAnswer(onSdpCreated: (String) -> Unit) {
        val sdp = "fake-answer-sdp"
        lastCreatedAnswer = sdp
        signalingState = SignalingState.STABLE
        onSdpCreated(sdp)
    }

    override fun rollback(onDone: () -> Unit) {
        rolledBack = true
        signalingState = SignalingState.STABLE
        onDone()
    }

    override fun dispose() {
        disposed = true
        signalingState = SignalingState.CLOSED
    }
}
