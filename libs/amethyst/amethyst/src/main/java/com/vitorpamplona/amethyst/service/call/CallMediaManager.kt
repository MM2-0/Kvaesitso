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

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

private const val TAG = "CallMediaManager"

/**
 * Owns the shared WebRTC infrastructure: [PeerConnectionFactory], [EglBase],
 * local audio/video sources and tracks, and the camera capturer.
 *
 * [CallController] delegates resource creation and teardown here so that
 * it can focus on call orchestration.
 */
class CallMediaManager(
    private val context: Context,
) {
    var peerConnectionFactory: PeerConnectionFactory? = null
        private set
    var sharedEglBase: EglBase? = null
        private set

    var localAudioSource: AudioSource? = null
        private set
    var localVideoSource: VideoSource? = null
        private set
    var localAudioTrack: AudioTrack? = null
        private set
    var localVideoTrack: VideoTrack? = null
        private set

    var screenVideoSource: VideoSource? = null
        private set
    var screenVideoTrack: VideoTrack? = null
        private set

    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenCapturer: ScreenShareCapturer? = null
    private var screenSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var usingFrontCamera: Boolean = true
    private var cameraWasEnabledBeforeScreenShare = false
    private var stoppingScreenShare = false

    var onScreenShareEnded: (() -> Unit)? = null

    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(false)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    fun initialize(callType: CallType) {
        if (peerConnectionFactory != null) return

        var egl: EglBase? = null
        try {
            egl = EglBase.create()

            PeerConnectionFactory.initialize(
                PeerConnectionFactory
                    .InitializationOptions
                    .builder(context)
                    .createInitializationOptions(),
            )

            peerConnectionFactory =
                PeerConnectionFactory
                    .builder()
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
                    .createPeerConnectionFactory()

            sharedEglBase = egl
        } catch (e: Exception) {
            // Clean up partially-created resources to avoid leaking EglBase
            egl?.release()
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            sharedEglBase = null
            throw e
        }

        localAudioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio0", localAudioSource)

        if (callType == CallType.VIDEO) {
            createVideoResources()
        }
    }

    @Synchronized
    fun createVideoResources() {
        if (localVideoSource != null) return
        val factory = peerConnectionFactory ?: return

        localVideoSource = factory.createVideoSource(false)
        localVideoTrack = factory.createVideoTrack("video0", localVideoSource)
        _localVideoTrackFlow.value = localVideoTrack
        _isVideoEnabled.value = true
        startCamera()
    }

    var captureWidth: Int = 1280
        private set
    var captureHeight: Int = 720
        private set
    var captureFps: Int = 30
        private set

    fun setCaptureResolution(
        width: Int,
        height: Int,
        fps: Int,
    ) {
        captureWidth = width
        captureHeight = height
        captureFps = fps
    }

    /**
     * Starts Android's system screen capture using the one-shot permission result supplied by
     * [android.media.projection.MediaProjectionManager]. The returned track is owned by this
     * manager and must be attached to the peer senders before [stopScreenShare] releases it.
     */
    @Synchronized
    fun startScreenShare(permissionData: Intent): VideoTrack {
        screenVideoTrack?.let { return it }

        val factory = peerConnectionFactory ?: throw IllegalStateException("PeerConnectionFactory not initialized")
        val egl = sharedEglBase ?: throw IllegalStateException("EGL context not initialized")
        val displayMetrics = context.resources.displayMetrics
        val captureSize = screenShareCaptureSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
        cameraWasEnabledBeforeScreenShare = _isVideoEnabled.value
        var source: VideoSource? = null
        var track: VideoTrack? = null
        var helper: SurfaceTextureHelper? = null
        var capturer: ScreenShareCapturer? = null

        try {
            val createdSource = factory.createVideoSource(true)
            source = createdSource
            val createdTrack = factory.createVideoTrack("screen0", createdSource)
            track = createdTrack
            val createdHelper = SurfaceTextureHelper.create("ScreenCaptureThread", egl.eglBaseContext)
            helper = createdHelper
            val createdCapturer =
                ScreenShareCapturer(
                    permissionData,
                    object : MediaProjection.Callback() {
                        override fun onStop() {
                            if (!stoppingScreenShare) {
                                onScreenShareEnded?.invoke()
                            }
                        }
                    },
                )
            capturer = createdCapturer

            screenVideoSource = createdSource
            screenVideoTrack = createdTrack
            screenSurfaceTextureHelper = createdHelper
            screenCapturer = createdCapturer
            if (cameraWasEnabledBeforeScreenShare) {
                stopCamera()
            }
            createdCapturer.initialize(createdHelper, context, createdSource.capturerObserver)
            createdCapturer.startCapture(captureSize.width, captureSize.height, captureFps)
            _isScreenSharing.value = true
            _isVideoEnabled.value = true
            _localVideoTrackFlow.value = createdTrack
            return createdTrack
        } catch (e: Exception) {
            screenCapturer = null
            screenSurfaceTextureHelper = null
            screenVideoTrack = null
            screenVideoSource = null
            runCatching { capturer?.dispose() }
            runCatching { helper?.dispose() }
            runCatching { track?.dispose() }
            runCatching { source?.dispose() }
            if (cameraWasEnabledBeforeScreenShare) {
                _isVideoEnabled.value = true
                _localVideoTrackFlow.value = localVideoTrack
                startCamera()
            }
            cameraWasEnabledBeforeScreenShare = false
            throw e
        }
    }

    /**
     * Stops screen capture and restores the camera state from before sharing started. The
     * returned resources remain alive until the caller has replaced every [RtpSender] track.
     *
     * Pass `restoreCamera = false` when the whole session is going away — see [dispose]. Restoring
     * a camera that is about to be torn down opens the device for a few hundred milliseconds
     * (lighting the privacy indicator) and makes the following [stopCamera] block waiting for the
     * capture session it just started.
     */
    @Synchronized
    fun stopScreenShare(restoreCamera: Boolean = true): ScreenShareResources? {
        val track = screenVideoTrack ?: return null
        val source = requireNotNull(screenVideoSource)
        stoppingScreenShare = true
        try {
            screenCapturer?.let { capturer ->
                runCatching { capturer.stopCapture() }
                runCatching { capturer.dispose() }
            }
            screenSurfaceTextureHelper?.let { runCatching { it.dispose() } }
        } finally {
            screenCapturer = null
            screenSurfaceTextureHelper = null
            screenVideoTrack = null
            screenVideoSource = null
            _isScreenSharing.value = false

            if (restoreCamera && cameraWasEnabledBeforeScreenShare) {
                recreateCameraResources()
                _isVideoEnabled.value = true
                _localVideoTrackFlow.value = localVideoTrack
                startCamera()
            } else {
                _isVideoEnabled.value = false
                _localVideoTrackFlow.value = localVideoTrack
            }
            cameraWasEnabledBeforeScreenShare = false
            stoppingScreenShare = false
        }
        return ScreenShareResources(track, source)
    }

    fun disposeScreenShareResources(resources: ScreenShareResources?) {
        resources ?: return
        runCatching { resources.track.dispose() }
        runCatching { resources.source.dispose() }
    }

    private fun recreateCameraResources() {
        val factory = peerConnectionFactory ?: return
        // RtpSender.setTrack may release the previous native track wrapper, so restore a fresh
        // camera track before binding it back to the senders.
        runCatching { localVideoTrack?.dispose() }
        runCatching { localVideoSource?.dispose() }
        localVideoSource = factory.createVideoSource(false)
        localVideoTrack = factory.createVideoTrack("video0", localVideoSource)
    }

    @Synchronized
    fun startCamera() {
        if (cameraCapturer != null) return
        val source = localVideoSource ?: return
        val egl = sharedEglBase ?: return

        val enumerator = Camera2Enumerator(context)
        val preferred =
            if (usingFrontCamera) {
                enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            } else {
                enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            }
        val camera = preferred ?: enumerator.deviceNames.firstOrNull() ?: return

        val helper = SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext)
        surfaceTextureHelper = helper
        cameraCapturer =
            enumerator.createCapturer(camera, null)?.also {
                it.initialize(helper, context, source.capturerObserver)
                it.startCapture(captureWidth, captureHeight, captureFps)
            }
    }

    fun switchCamera() {
        val capturer = cameraCapturer ?: return
        capturer.switchCamera(
            object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFront: Boolean) {
                    usingFrontCamera = isFront
                    _isFrontCamera.value = isFront
                    Log.d(TAG) { "Camera switched: front=$isFront" }
                }

                override fun onCameraSwitchError(error: String?) {
                    Log.e(TAG) { "Camera switch failed: $error" }
                }
            },
        )
    }

    @Synchronized
    fun stopCamera() {
        try {
            cameraCapturer?.stopCapture()
        } catch (_: InterruptedException) {
        }
        cameraCapturer?.dispose()
        cameraCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
    }

    fun enableVideo() {
        localVideoTrack?.setEnabled(true)
        _isVideoEnabled.value = true
        _localVideoTrackFlow.value = localVideoTrack
        startCamera()
    }

    fun disableVideo() {
        localVideoTrack?.setEnabled(false)
        stopCamera()
        _isVideoEnabled.value = false
    }

    fun setAudioMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun dispose() {
        // Everything below tears the camera down, so don't let the screen-share stop bring it back
        // up first — that opened the device mid-hangup and made stopCamera() wait on it.
        val screenResources = stopScreenShare(restoreCamera = false)
        disposeScreenShareResources(screenResources)
        try {
            stopCamera()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: stopCamera() failed", e)
        }
        try {
            localAudioTrack?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: localAudioTrack.dispose() failed", e)
        }
        try {
            localVideoTrack?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: localVideoTrack.dispose() failed", e)
        }
        try {
            localAudioSource?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: localAudioSource.dispose() failed", e)
        }
        try {
            localVideoSource?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: localVideoSource.dispose() failed", e)
        }
        try {
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: peerConnectionFactory.dispose() failed", e)
        }
        try {
            sharedEglBase?.release()
        } catch (e: Exception) {
            Log.e(TAG, "dispose: sharedEglBase.release() failed", e)
        }

        localAudioTrack = null
        localVideoTrack = null
        localAudioSource = null
        localVideoSource = null
        peerConnectionFactory = null
        sharedEglBase = null
        _localVideoTrackFlow.value = null
        _isVideoEnabled.value = false
    }
}
