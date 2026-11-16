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
package com.vitorpamplona.amethyst.ui.call.session

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.AnswerRouteAction
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.IceCandidateData
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.PeerSession
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.PeerSessionManager
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.SdpType
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.SignalingState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_screen_sharing
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.amethyst.service.call.AudioRoute
import com.vitorpamplona.amethyst.service.call.CallAudioManager
import com.vitorpamplona.amethyst.service.call.CallForegroundService
import com.vitorpamplona.amethyst.service.call.CallMediaManager
import com.vitorpamplona.amethyst.service.call.IceServerConfig
import com.vitorpamplona.amethyst.service.call.RemoteVideoMonitor
import com.vitorpamplona.amethyst.service.call.WebRtcCallSession
import com.vitorpamplona.amethyst.service.call.WebRtcPeerSessionAdapter
import com.vitorpamplona.amethyst.service.call.notification.CallNotifier
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.WebRtcCallFactory
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.RtpSender
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "CallSession"
private const val VIDEO_MAX_BITRATE_BPS_DEFAULT = 1_500_000

/**
 * One-per-Activity call session. Created in [CallActivity.onCreate],
 * destroyed in [CallActivity.onDestroy] via [close]. This guarantees
 * that every WebRTC/audio/notification resource is released when the
 * Activity dies — no guard flags, no race-sensitive state observing.
 */
@Stable
class CallSession(
    private val context: Context,
    val callManager: CallManager,
    private val scope: CoroutineScope,
    private val publishWrap: suspend (EphemeralGiftWrapEvent) -> Unit,
    private val signerProvider: suspend () -> com.vitorpamplona.quartz.nip01Core.signers.NostrSigner,
    localPubKey: HexKey,
    private val settingsProvider: () -> com.vitorpamplona.amethyst.commons.model.AccountSettings,
) : AutoCloseable {
    private var peerSessionMgr = PeerSessionManager(localPubKey)

    private fun webRtcSession(peerPubKey: HexKey): WebRtcCallSession? = (peerSessionMgr.getSession(peerPubKey)?.session as? WebRtcPeerSessionAdapter)?.webRtcSession

    val mediaManager = CallMediaManager(context)
    val audioManager = CallAudioManager(context)
    val videoMonitor = RemoteVideoMonitor(scope)
    private val callFactory = WebRtcCallFactory()

    // ---- UI-exposed state ----

    val localVideoTrack: StateFlow<VideoTrack?> = mediaManager.localVideoTrackFlow
    val remoteVideoTrack: StateFlow<VideoTrack?> = videoMonitor.remoteVideoTrack
    val remoteVideoTracks: StateFlow<Map<HexKey, VideoTrack>> = videoMonitor.remoteVideoTracks

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isRemoteVideoActive: StateFlow<Boolean> = videoMonitor.isRemoteVideoActive
    val remoteVideoAspectRatio: StateFlow<Float?> = videoMonitor.remoteVideoAspectRatio
    val activePeerVideos: StateFlow<Set<HexKey>> = videoMonitor.activePeerVideos

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()
    val isVideoEnabled: StateFlow<Boolean> = mediaManager.isVideoEnabled
    val isScreenSharing: StateFlow<Boolean> = mediaManager.isScreenSharing
    val isFrontCamera: StateFlow<Boolean> = mediaManager.isFrontCamera
    val audioRoute: StateFlow<AudioRoute> = audioManager.audioRoute
    val isBluetoothAvailable: StateFlow<Boolean> = audioManager.isBluetoothAvailable

    @Volatile private var videoPausedByProximity = false

    @Volatile private var closed = false

    @Volatile private var foregroundServiceStarted = false
    private val videoSenders = ConcurrentHashMap<HexKey, RtpSender>()

    private val pendingRenegotiation = ConcurrentHashMap<HexKey, Boolean>()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallbackRegistered = false
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG) { "Network available — triggering ICE restart on all peers" }
                restartIceOnAllPeers()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                Log.d(TAG) { "Network capabilities changed — triggering ICE restart on all peers" }
                restartIceOnAllPeers()
            }
        }

    // ---- Initialization ----

    init {
        mediaManager.onScreenShareEnded = {
            if (!closed) {
                scope.launch { stopScreenShareNow() }
            }
        }

        // Collect all session-level events (answers, ICE candidates,
        // peer joins/leaves, mid-call offers) from the single SharedFlow.
        scope.launch {
            callManager.sessionEvents.collect { event ->
                if (closed) return@collect
                when (event) {
                    is com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallSessionEvent.AnswerReceived -> {
                        onCallAnswerReceived(event.event.pubKey, event.event.sdpAnswer())
                    }

                    is com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallSessionEvent.IceCandidateReceived -> {
                        onIceCandidateReceived(event.event)
                    }

                    is com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallSessionEvent.NewPeerInGroupCall -> {
                        onNewPeerInGroupCall(event.peerPubKey)
                    }

                    is com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallSessionEvent.MidCallOfferReceived -> {
                        onMidCallOfferReceived(event.peerPubKey, event.sdpOffer)
                    }

                    is com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallSessionEvent.PeerLeft -> {
                        disposePeerSession(event.peerPubKey)
                    }
                }
            }
        }

        // Renegotiation events have their own flow because they go
        // through dedicated glare-resolution logic.
        scope.launch {
            callManager.renegotiationEvents.collect { event ->
                if (!closed) onRenegotiationOfferReceived(event)
            }
        }

        scope.launch {
            callManager.state.collect { state ->
                Log.d(TAG) { "state collector received: ${state::class.simpleName} closed=$closed thread=${Thread.currentThread().name}" }
                if (closed) return@collect
                when (state) {
                    is CallState.IncomingCall -> {
                        ensureForegroundService()
                        withContext(Dispatchers.IO) { audioManager.startRinging() }
                        scope.launch {
                            CallNotifier.showIncomingCall(state.callerPubKey, context)
                        }
                    }

                    is CallState.Offering -> {
                        audioManager.startRingbackTone()
                        ensureForegroundService()
                    }

                    is CallState.Connecting, is CallState.Connected -> {
                        // Same setup on both entry paths: Offering -> Connecting -> Connected,
                        // and direct -> Connected (e.g. answered-elsewhere). All ops below are
                        // idempotent so re-running on Connecting -> Connected is safe.
                        audioManager.stopRinging()
                        audioManager.stopRingbackTone()
                        withContext(Dispatchers.IO) { audioManager.switchToCallAudioMode() }
                        audioManager.acquireProximityWakeLock()
                        CallNotifier.cancelIncomingCall(context)
                        updateForegroundServiceNotification()
                        registerNetworkCallback()
                    }

                    is CallState.Ended -> {
                        // Stop ringing/ringback — close() handles the
                        // full teardown when the Activity is destroyed.
                        audioManager.stopRinging()
                        audioManager.stopRingbackTone()
                        CallNotifier.cancelIncomingCall(context)
                    }

                    is CallState.Idle -> {
                        // No action — Ended already stopped audio/notification.
                    }
                }
            }
        }

        scope.launch {
            audioManager.isNearEar.collect { nearEar ->
                if (mediaManager.isScreenSharing.value) return@collect
                val videoTrack = mediaManager.localVideoTrack ?: return@collect
                if (nearEar && mediaManager.isVideoEnabled.value && !videoPausedByProximity) {
                    videoPausedByProximity = true
                    videoTrack.setEnabled(false)
                    mediaManager.stopCamera()
                } else if (!nearEar && videoPausedByProximity) {
                    videoPausedByProximity = false
                    videoTrack.setEnabled(true)
                    mediaManager.startCamera()
                }
            }
        }
    }

    private fun applyCallSettings() {
        val settings = settingsProvider()
        val resolution = settings.callVideoResolution
        mediaManager.setCaptureResolution(resolution.width, resolution.height, resolution.fps)
    }

    // ---- Call initiation (caller side) ----

    fun initiate(
        peerPubKeys: Set<String>,
        callType: CallType,
    ) {
        Log.d(TAG) { "initiate: peers=${peerPubKeys.map { it.take(8) }}, callType=$callType" }
        scope.launch {
            val callId = UUID.randomUUID().toString()
            _errorMessage.value = null
            applyCallSettings()
            try {
                withContext(Dispatchers.IO) { mediaManager.initialize(callType) }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Failed to initialize WebRTC", e)
                _errorMessage.value = "Failed to start call: ${e.message}"
                callManager.hangup()
                return@launch
            }

            callManager.beginOffering(callId, peerPubKeys, callType)

            var successCount = 0
            for (peerPubKey in peerPubKeys) {
                try {
                    val webRtcSession = withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                    val adapter = WebRtcPeerSessionAdapter(webRtcSession)
                    peerSessionMgr.registerSession(peerPubKey, adapter)
                    webRtcSession.createOffer { sdp ->
                        scope.launch {
                            callManager.publishOfferToPeer(peerPubKey, peerPubKeys, callType, callId, sdp.description)
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "Failed to create PeerConnection for ${peerPubKey.take(8)}", e)
                }
            }
            if (successCount == 0) {
                Log.e(TAG, "All PeerConnection creations failed, hanging up")
                _errorMessage.value = "Failed to start call: could not create any connections"
                callManager.hangup()
            }
        }
    }

    // ---- Accept incoming call (callee side) ----

    fun accept(sdpOffer: String) {
        val state = callManager.state.value
        if (state !is CallState.IncomingCall) return

        val callerPubKey = state.callerPubKey
        scope.launch {
            _errorMessage.value = null
            applyCallSettings()
            try {
                withContext(Dispatchers.IO) { mediaManager.initialize(state.callType) }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Failed to initialize WebRTC", e)
                _errorMessage.value = "Failed to accept call: ${e.message}"
                callManager.rejectCall()
                return@launch
            }

            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(callerPubKey) }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "Failed to create PeerConnection", e)
                    _errorMessage.value = "Failed to accept call: ${e.message}"
                    callManager.rejectCall()
                    return@launch
                }

            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            peerSessionMgr.registerSession(callerPubKey, adapter)
            adapter.setRemoteDescription(SdpType.OFFER, sdpOffer)
            peerSessionMgr.flushPendingIceCandidates(callerPubKey)

            webRtcSession.createAnswer { sdp ->
                scope.launch {
                    callManager.acceptCall(sdp.description)
                }
            }
        }
    }

    // ---- Answer routing ----

    fun onCallAnswerReceived(
        peerPubKey: HexKey,
        sdpAnswer: String,
    ) {
        val action = peerSessionMgr.routeAnswer(peerPubKey, sdpAnswer)
        when (action) {
            AnswerRouteAction.APPLIED -> {
                Log.d(TAG) { "Answer applied for ${peerPubKey.take(8)}" }
            }

            AnswerRouteAction.NO_SESSION -> {
                // Unknown peer answering the current call. Two cases:
                //
                // 1. We are in Connected state — another participant invited a
                //    new peer mid-call and the invitee is broadcasting their
                //    acceptance to us. The invitee stays passive, so we MUST
                //    unconditionally initiate a mesh offer to them. The
                //    lower-pubkey tiebreaker does NOT apply here because only
                //    one side (the existing Connected callee) reacts to the
                //    broadcast answer.
                //
                // 2. We are still in Connecting state — both callees are
                //    handshaking in parallel during an initial group call and
                //    are observing each other's answers. Use the lower-pubkey
                //    tiebreaker via onNewPeerInGroupCall() to avoid glare,
                //    since the symmetric peer will apply the same rule.
                if (callManager.state.value is CallState.Connected) {
                    Log.d(TAG) { "Mid-call invite: ${peerPubKey.take(8)} joined — initiating mesh offer" }
                    scope.launch { createAndOfferToPeer(peerPubKey) }
                } else {
                    Log.d(TAG) { "Answer from unknown peer ${peerPubKey.take(8)} — triggering callee-to-callee" }
                    onNewPeerInGroupCall(peerPubKey)
                }
            }

            AnswerRouteAction.IGNORED_WRONG_STATE -> {
                Log.d(TAG) { "Ignoring answer from ${peerPubKey.take(8)} (no pending local offer)" }
            }
        }
    }

    // ---- ICE candidate routing ----

    fun onIceCandidateReceived(event: CallIceCandidateEvent) {
        try {
            val candidate = IceCandidateData(event.candidateSdp(), event.sdpMid(), event.sdpMLineIndex())
            peerSessionMgr.routeIceCandidate(event.pubKey, candidate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ICE candidate", e)
        }
    }

    private fun onLocalIceCandidate(
        peerPubKey: HexKey,
        candidate: IceCandidate,
    ) {
        val callId = callManager.currentCallId() ?: return
        val candidateJson = CallIceCandidateEvent.serializeCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        scope.launch {
            // A refused candidate only loses that route; the remaining candidates still connect.
            logSignerErrors(TAG) {
                val signer = signerProvider()
                val result = callFactory.createIceCandidate(candidateJson, peerPubKey, callId, signer)
                publishWrap(result.wrap)
            }
        }
    }

    // ---- Callee-to-callee mesh connections ----

    fun onNewPeerInGroupCall(peerPubKey: HexKey) {
        if (peerSessionMgr.hasSession(peerPubKey)) return
        scope.launch {
            if (peerSessionMgr.shouldInitiateOffer(peerPubKey)) {
                createAndOfferToPeer(peerPubKey)
            }
        }
    }

    private suspend fun createAndOfferToPeer(peerPubKey: HexKey) {
        if (mediaManager.peerConnectionFactory == null) return
        val webRtcSession =
            try {
                withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to create PeerConnection for ${peerPubKey.take(8)}", e)
                return
            }
        val adapter = WebRtcPeerSessionAdapter(webRtcSession)
        peerSessionMgr.registerSession(peerPubKey, adapter)
        webRtcSession.createOffer { sdp ->
            scope.launch { callManager.publishOfferToPeer(peerPubKey, sdp.description) }
        }
    }

    fun onMidCallOfferReceived(
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        if (peerSessionMgr.hasSession(peerPubKey)) return
        scope.launch {
            if (mediaManager.peerConnectionFactory == null) return@launch
            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "Failed to create PeerConnection for mid-call offer from ${peerPubKey.take(8)}", e)
                    return@launch
                }
            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            peerSessionMgr.registerSession(peerPubKey, adapter)
            adapter.setRemoteDescription(SdpType.OFFER, sdpOffer)
            peerSessionMgr.flushPendingIceCandidates(peerPubKey)
            webRtcSession.createAnswer { sdp ->
                scope.launch { callManager.publishAnswerToPeer(peerPubKey, sdp.description) }
            }
        }
    }

    // ---- Renegotiation ----

    private fun onRenegotiationOfferReceived(event: CallRenegotiateEvent) {
        val peerPubKey = event.pubKey
        val sdpOffer = event.sdpOffer()
        scope.launch {
            peerSessionMgr.resolveRenegotiationGlare(peerPubKey, sdpOffer) { entry ->
                applyRenegotiationOffer(entry.session, peerPubKey, sdpOffer)
            }
        }
    }

    private fun applyRenegotiationOffer(
        session: PeerSession,
        peerPubKey: HexKey,
        sdpOffer: String,
    ) {
        session.setRemoteDescription(SdpType.OFFER, sdpOffer)
        session.createAnswer { sdpAnswer ->
            scope.launch { callManager.sendRenegotiationAnswer(sdpAnswer, peerPubKey) }
        }
    }

    private fun performRenegotiation(peerPubKey: HexKey) {
        if (pendingRenegotiation.putIfAbsent(peerPubKey, true) != null) return
        val webRtcSession =
            webRtcSession(peerPubKey) ?: run {
                pendingRenegotiation.remove(peerPubKey)
                return
            }
        val state = callManager.state.value
        if (state !is CallState.Connected && state !is CallState.Connecting) {
            pendingRenegotiation.remove(peerPubKey)
            return
        }
        webRtcSession.createOffer { sdp ->
            pendingRenegotiation.remove(peerPubKey)
            scope.launch { callManager.sendRenegotiation(sdp.description, peerPubKey) }
        }
    }

    // ---- Network change handling ----

    private fun restartIceOnAllPeers() {
        val state = callManager.state.value
        if (state !is CallState.Connected && state !is CallState.Connecting) return
        peerSessionMgr.allSessionKeys().forEach { key ->
            webRtcSession(key)?.triggerIceRestart()
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        networkCallbackRegistered = false
    }

    // ---- UI toggle controls ----

    fun startScreenShare(permissionData: Intent) {
        val state = callManager.state.value
        if (closed || (state !is CallState.Connecting && state !is CallState.Connected)) return

        scope.launch {
            if (mediaManager.isScreenSharing.value) return@launch
            try {
                ensureForegroundService(isScreenSharing = true)
                val screenTrack = withContext(Dispatchers.IO) { mediaManager.startScreenShare(permissionData) }
                if (closed) {
                    stopScreenShareNow()
                    return@launch
                }
                replaceVideoTrack(screenTrack)
                updateForegroundServiceNotification(isScreenSharing = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start screen share", e)
                if (mediaManager.isScreenSharing.value) {
                    try {
                        stopScreenShareNow()
                    } catch (cleanupError: Exception) {
                        Log.e(TAG, "Failed to clean up screen share", cleanupError)
                    }
                }
                currentCoroutineContext().ensureActive()
                _errorMessage.value = "Failed to share screen: ${e.message}"
                updateForegroundServiceNotification(isScreenSharing = false)
            }
        }
    }

    fun stopScreenShare() {
        scope.launch { stopScreenShareNow() }
    }

    private suspend fun stopScreenShareNow() {
        val resources = mediaManager.stopScreenShare() ?: return
        try {
            val restoredTrack =
                mediaManager.localVideoTrack.takeIf { mediaManager.isVideoEnabled.value }
            replaceVideoTrack(restoredTrack)
            if (videoPausedByProximity) {
                mediaManager.localVideoTrack?.setEnabled(false)
                mediaManager.stopCamera()
            }
        } finally {
            mediaManager.disposeScreenShareResources(resources)
            updateForegroundServiceNotification(isScreenSharing = false)
        }
    }

    private fun replaceVideoTrack(track: VideoTrack?) {
        peerSessionMgr.allSessionKeys().forEach { key ->
            val session = webRtcSession(key) ?: return@forEach
            if (track != null) {
                val sender = videoSenders[key]
                if (sender != null) {
                    if (!sender.setTrack(track, false)) {
                        Log.e(TAG) { "Failed to replace video track for ${key.take(8)}" }
                    }
                } else {
                    session.addTrack(track, settingsProvider().callMaxBitrateBps)?.let { newSender ->
                        videoSenders[key] = newSender
                    }
                }
            } else {
                videoSenders.remove(key)?.let { sender ->
                    sender.setTrack(null, false)
                    session.removeTrack(sender)
                }
            }
        }
    }

    fun toggleMute() {
        val muted = !_isAudioMuted.value
        _isAudioMuted.value = muted
        mediaManager.setAudioMuted(muted)
    }

    fun toggleVideo() {
        if (mediaManager.isScreenSharing.value) return
        val enabling = !mediaManager.isVideoEnabled.value
        if (enabling) {
            if (mediaManager.localVideoTrack == null) {
                mediaManager.createVideoResources()
            } else {
                mediaManager.enableVideo()
            }
            // Add video track to all peer connections
            peerSessionMgr.allSessionKeys().forEach { key ->
                if (videoSenders[key] == null) {
                    webRtcSession(key)?.let { session ->
                        mediaManager.localVideoTrack?.let { track ->
                            val sender = session.addTrack(track, settingsProvider().callMaxBitrateBps)
                            if (sender != null) {
                                videoSenders[key] = sender
                            }
                        }
                    }
                }
            }
        } else {
            // Remove video track senders so remote peers see track removal
            peerSessionMgr.allSessionKeys().forEach { key ->
                videoSenders.remove(key)?.let { sender ->
                    webRtcSession(key)?.removeTrack(sender)
                }
            }
            mediaManager.disableVideo()
        }
    }

    fun switchCamera() {
        if (mediaManager.isScreenSharing.value) return
        mediaManager.switchCamera()
    }

    fun cycleAudioRoute() {
        audioManager.cycleAudioRoute()
    }

    fun getEglBase(): EglBase? = mediaManager.sharedEglBase

    fun invitePeer(peerPubKey: String) {
        scope.launch {
            if (mediaManager.peerConnectionFactory == null) return@launch
            val webRtcSession =
                try {
                    withContext(Dispatchers.IO) { createWebRtcSession(peerPubKey) }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "Failed to create PeerConnection for invite ${peerPubKey.take(8)}", e)
                    return@launch
                }
            val adapter = WebRtcPeerSessionAdapter(webRtcSession)
            peerSessionMgr.registerSession(peerPubKey, adapter)
            webRtcSession.createOffer { sdp ->
                scope.launch { callManager.invitePeer(peerPubKey, sdp.description) }
            }
        }
    }

    fun hangup() {
        scope.launch { callManager.hangup() }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ---- Per-peer PeerConnection creation ----

    private fun createWebRtcSession(peerPubKey: HexKey): WebRtcCallSession {
        val factory = mediaManager.peerConnectionFactory ?: throw IllegalStateException("PeerConnectionFactory not initialized")
        val session =
            WebRtcCallSession(
                peerConnectionFactory = factory,
                iceServers = IceServerConfig.buildIceServers(settingsProvider().callTurnServers),
                onIceCandidate = { candidate ->
                    if (!closed) onLocalIceCandidate(peerPubKey, candidate)
                },
                onPeerConnected = {
                    if (closed) return@WebRtcCallSession
                    Log.d(TAG) { "Peer ${peerPubKey.take(8)} connected!" }
                    scope.launch {
                        if (closed) return@launch
                        callManager.onPeerConnected()
                        if (callManager.state.value is CallState.Connected) {
                            ensureForegroundService()
                        }
                    }
                },
                onRemoteVideoTrack = { track ->
                    if (!closed) videoMonitor.onRemoteVideoTrack(peerPubKey, track)
                },
                onDisconnected = {
                    if (!closed) scope.launch { onPeerDisconnected(peerPubKey) }
                },
                onError = { error ->
                    if (!closed) _errorMessage.value = error
                },
                onRenegotiationNeeded = {
                    if (!closed) performRenegotiation(peerPubKey)
                },
                onIceRestartOffer = { sdp ->
                    if (!closed) {
                        scope.launch { callManager.sendRenegotiation(sdp.description, peerPubKey) }
                    }
                },
            )
        try {
            session.createPeerConnection()
        } catch (e: Exception) {
            session.dispose()
            throw e
        }
        mediaManager.localAudioTrack?.let { session.addTrack(it) }
        (mediaManager.screenVideoTrack ?: mediaManager.localVideoTrack)?.let { track ->
            val sender = session.addTrack(track, settingsProvider().callMaxBitrateBps)
            if (sender != null) {
                videoSenders[peerPubKey] = sender
            }
        }
        return session
    }

    private fun onPeerDisconnected(peerPubKey: HexKey) {
        Log.d(TAG) { "Peer ${peerPubKey.take(8)} disconnected (ICE FAILED)" }
        val allDisconnected =
            peerSessionMgr.allSessionKeys().all { key ->
                key == peerPubKey ||
                    peerSessionMgr.getSession(key)?.session?.getSignalingState() == SignalingState.CLOSED ||
                    peerSessionMgr.getSession(key)?.remoteDescriptionSet != true
            }
        if (allDisconnected) {
            scope.launch { callManager.hangup() }
        }
    }

    // ---- Per-peer cleanup ----

    fun disposePeerSession(peerPubKey: HexKey) {
        videoSenders.remove(peerPubKey)
        val entry = peerSessionMgr.removeSession(peerPubKey)
        if (entry != null) {
            try {
                entry.session.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "disposePeerSession: dispose() failed for ${peerPubKey.take(8)}", e)
            }
            videoMonitor.onPeerRemoved(peerPubKey)
        }
    }

    // ---- Close (AutoCloseable) ----

    /**
     * Single-shot teardown tied to Activity destruction. Unconditionally
     * releases every resource this session owns: audio, ringtone, ringback,
     * notification, WebRTC peer connections, network callback, foreground
     * service, and proximity wake lock. No guard flags — each Activity
     * instance creates exactly one [CallSession] and calls [close] exactly
     * once from [onDestroy].
     */
    override fun close() {
        Log.d(TAG) { "close() called on ${Thread.currentThread().name} state=${callManager.state.value::class.simpleName}" }
        // Signal the init collectors to stop touching resources.
        closed = true
        mediaManager.onScreenShareEnded = null

        try {
            audioManager.stopRinging()
        } catch (e: Exception) {
            Log.e(TAG, "close: audioManager.stopRinging() failed", e)
        }
        try {
            audioManager.stopRingbackTone()
        } catch (e: Exception) {
            Log.e(TAG, "close: audioManager.stopRingbackTone() failed", e)
        }
        try {
            CallNotifier.cancelIncomingCall(context)
        } catch (e: Exception) {
            Log.e(TAG, "close: cancelIncomingCall() failed", e)
        }

        unregisterNetworkCallback()
        try {
            audioManager.release()
        } catch (e: Exception) {
            Log.e(TAG, "close: audioManager.release() failed", e)
        }
        try {
            stopForegroundService()
        } catch (e: Exception) {
            Log.e(TAG, "close: stopForegroundService() failed", e)
        }
        foregroundServiceStarted = false

        videoMonitor.dispose()

        // Synchronized to prevent a concurrent collector from reading
        // the old manager after disposeAll() but before reassignment.
        synchronized(this) {
            try {
                peerSessionMgr.disposeAll()
            } catch (e: Exception) {
                Log.e(TAG, "close: sessionManager.disposeAll() failed", e)
            }
            peerSessionMgr = PeerSessionManager(peerSessionMgr.localPubKey)
        }

        mediaManager.dispose()

        _isAudioMuted.value = false
        videoPausedByProximity = false
        videoSenders.clear()
        pendingRenegotiation.clear()
    }

    // ---- Foreground service ----

    private suspend fun ensureForegroundService(isScreenSharing: Boolean = mediaManager.isScreenSharing.value) {
        if (foregroundServiceStarted) {
            updateForegroundServiceNotification(isScreenSharing)
            return
        }
        foregroundServiceStarted = true
        startForegroundService(isScreenSharing)
    }

    private suspend fun startForegroundService(isScreenSharing: Boolean = mediaManager.isScreenSharing.value) {
        try {
            val peerName = callManager.currentPeerPubKey() ?: ""
            val isVideo = mediaManager.isVideoEnabled.value
            val isRinging = callManager.state.value is CallState.IncomingCall || callManager.state.value is CallState.Offering
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_START
                    putExtra(CallForegroundService.EXTRA_PEER_NAME, peerName)
                    putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
                    putExtra(CallForegroundService.EXTRA_IS_SCREEN_SHARING, isScreenSharing)
                    putExtra(CallForegroundService.EXTRA_IS_RINGING, isRinging)
                    if (isScreenSharing) {
                        putExtra(CallForegroundService.EXTRA_STATUS_TEXT, loadStringRes(Res.string.call_screen_sharing))
                    }
                }
            context.startForegroundService(intent)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private suspend fun updateForegroundServiceNotification(isScreenSharing: Boolean = mediaManager.isScreenSharing.value) {
        if (!foregroundServiceStarted) return
        try {
            val peerName = callManager.currentPeerPubKey() ?: ""
            val isVideo = mediaManager.isVideoEnabled.value
            val isRinging = callManager.state.value is CallState.IncomingCall || callManager.state.value is CallState.Offering
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_UPDATE
                    putExtra(CallForegroundService.EXTRA_PEER_NAME, peerName)
                    putExtra(CallForegroundService.EXTRA_IS_VIDEO, isVideo)
                    putExtra(CallForegroundService.EXTRA_IS_SCREEN_SHARING, isScreenSharing)
                    putExtra(CallForegroundService.EXTRA_IS_RINGING, isRinging)
                    if (isScreenSharing) {
                        putExtra(CallForegroundService.EXTRA_STATUS_TEXT, loadStringRes(Res.string.call_screen_sharing))
                    }
                }
            context.startService(intent)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "Failed to update foreground service notification", e)
        }
    }

    private fun stopForegroundService() {
        try {
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    action = CallForegroundService.ACTION_STOP
                }
            context.startService(intent)
        } catch (_: Exception) {
        }
    }
}
