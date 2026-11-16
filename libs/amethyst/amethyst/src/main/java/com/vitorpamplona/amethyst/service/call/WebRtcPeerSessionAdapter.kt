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
package com.vitorpamplona.amethyst.service.call

import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.IceCandidateData
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.PeerSession
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.SdpType
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.SignalingState
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * Adapts a [WebRtcCallSession] to the platform-independent [PeerSession] interface,
 * allowing [com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.PeerSessionManager] to manage
 * ICE buffering, answer routing, and renegotiation glare without depending on org.webrtc.
 */
class WebRtcPeerSessionAdapter(
    val webRtcSession: WebRtcCallSession,
) : PeerSession {
    override fun getSignalingState(): SignalingState? =
        when (webRtcSession.getSignalingState()) {
            PeerConnection.SignalingState.STABLE -> SignalingState.STABLE
            PeerConnection.SignalingState.HAVE_LOCAL_OFFER -> SignalingState.HAVE_LOCAL_OFFER
            PeerConnection.SignalingState.HAVE_REMOTE_OFFER -> SignalingState.HAVE_REMOTE_OFFER
            PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER -> SignalingState.HAVE_LOCAL_PRANSWER
            PeerConnection.SignalingState.HAVE_REMOTE_PRANSWER -> SignalingState.HAVE_REMOTE_PRANSWER
            PeerConnection.SignalingState.CLOSED -> SignalingState.CLOSED
            null -> null
        }

    override fun setRemoteDescription(
        type: SdpType,
        sdp: String,
    ) {
        val sdType =
            when (type) {
                SdpType.OFFER -> SessionDescription.Type.OFFER
                SdpType.ANSWER -> SessionDescription.Type.ANSWER
            }
        webRtcSession.setRemoteDescription(SessionDescription(sdType, sdp))
    }

    override fun addIceCandidate(candidate: IceCandidateData) {
        webRtcSession.addIceCandidate(
            IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp),
        )
    }

    override fun createOffer(onSdpCreated: (String) -> Unit) {
        webRtcSession.createOffer { sdp -> onSdpCreated(sdp.description) }
    }

    override fun createAnswer(onSdpCreated: (String) -> Unit) {
        webRtcSession.createAnswer { sdp -> onSdpCreated(sdp.description) }
    }

    override fun rollback(onDone: () -> Unit) {
        webRtcSession.rollback(onDone)
    }

    override fun dispose() {
        webRtcSession.dispose()
    }
}
