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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.ThreadUtils
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/**
 * Captures the screen through [MediaProjection] and feeds it to WebRTC.
 *
 * Behaviourally a drop-in for `org.webrtc.ScreenCapturerAndroid` (a derivative of it — original
 * © 2016 The WebRTC project authors, BSD-style license), with one defect fixed: the upstream
 * class builds the capture `Surface` inline,
 *
 * ```java
 * virtualDisplay = mediaProjection.createVirtualDisplay(..., new Surface(helper.getSurfaceTexture()), ...);
 * ```
 *
 * and keeps no reference to it, so `Surface.release()` is never called. `VirtualDisplay.release()`
 * does not cover it — the Surface belongs to the caller — so every capture session leaked one,
 * reclaimed only whenever the finalizer next ran. It showed up as a StrictMode
 * `LeakedClosableViolation` pointing at `ScreenCapturerAndroid.createVirtualDisplay`, and
 * `changeCaptureFormat` leaked another one per call. This version owns the Surface and releases
 * it with the virtual display.
 */
class ScreenShareCapturer(
    private val mediaProjectionPermissionResultData: Intent,
    private val mediaProjectionCallback: MediaProjection.Callback,
) : VideoCapturer,
    VideoSink {
    private var width: Int = 0
    private var height: Int = 0

    private var virtualDisplay: VirtualDisplay? = null

    /** The reference the upstream capturer drops on the floor. Released in [releaseDisplay]. */
    private var surface: Surface? = null

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    private var numCapturedFrames: Long = 0
    private var isDisposed = false

    private fun checkNotDisposed() {
        check(!isDisposed) { "capturer is disposed." }
    }

    @Synchronized
    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper,
        applicationContext: Context,
        capturerObserver: CapturerObserver,
    ) {
        checkNotDisposed()
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
        this.mediaProjectionManager = applicationContext.getSystemService(MediaProjectionManager::class.java)
    }

    @Synchronized
    override fun startCapture(
        width: Int,
        height: Int,
        ignoredFramerate: Int,
    ) {
        checkNotDisposed()
        this.width = width
        this.height = height

        val helper = surfaceTextureHelper ?: throw IllegalStateException("surfaceTextureHelper not set.")
        val manager = mediaProjectionManager ?: throw IllegalStateException("capturer not initialized.")

        val projection =
            manager.getMediaProjection(Activity.RESULT_OK, mediaProjectionPermissionResultData)
                ?: throw IllegalStateException("MediaProjection permission data was rejected.")
        mediaProjection = projection

        // Let the MediaProjection callback use the SurfaceTextureHelper thread.
        projection.registerCallback(mediaProjectionCallback, helper.handler)

        createVirtualDisplay()
        capturerObserver?.onCapturerStarted(true)
        helper.startListening(this)
    }

    @Synchronized
    override fun stopCapture() {
        checkNotDisposed()
        val helper = surfaceTextureHelper ?: return
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.stopListening()
            capturerObserver?.onCapturerStopped()

            releaseDisplay()

            mediaProjection?.let { projection ->
                // Unregister the callback before stopping, otherwise the callback recursively
                // calls this method.
                projection.unregisterCallback(mediaProjectionCallback)
                projection.stop()
            }
            mediaProjection = null
        }
    }

    @Synchronized
    override fun dispose() {
        isDisposed = true
        // Best-effort. On the failure path startCapture() can have created the display and Surface
        // without a matching stopCapture(), and nothing else would hand them back.
        releaseDisplay()
    }

    /**
     * Changes the output video size, e.g. when the captured screen rotates.
     */
    @Synchronized
    override fun changeCaptureFormat(
        width: Int,
        height: Int,
        ignoredFramerate: Int,
    ) {
        checkNotDisposed()
        this.width = width
        this.height = height

        // Capturer is stopped; the virtual display will be created by startCapture().
        if (virtualDisplay == null) return

        val helper = surfaceTextureHelper ?: return

        // Recreate on the SurfaceTextureHelper thread to avoid interfering with frame processing,
        // which runs on that same thread.
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            releaseDisplay()
            createVirtualDisplay()
        }
    }

    private fun createVirtualDisplay() {
        val helper = surfaceTextureHelper ?: return
        val projection = mediaProjection ?: return

        helper.setTextureSize(width, height)

        val newSurface = Surface(helper.surfaceTexture)
        surface = newSurface
        virtualDisplay =
            projection.createVirtualDisplay(
                "WebRTC_ScreenCapture",
                width,
                height,
                VIRTUAL_DISPLAY_DPI,
                DISPLAY_FLAGS,
                newSurface,
                null,
                null,
            )
    }

    /** Releases the virtual display first, so nothing is still drawing into the Surface. */
    private fun releaseDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        surface?.release()
        surface = null
    }

    /** Called on the internal looper thread of [SurfaceTextureHelper]. */
    override fun onFrame(frame: VideoFrame) {
        numCapturedFrames++
        capturerObserver?.onFrameCaptured(frame)
    }

    override fun isScreencast(): Boolean = true

    fun getNumCapturedFrames(): Long = numCapturedFrames

    companion object {
        private const val DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        /** DPI for the VirtualDisplay; does not appear to matter here. */
        private const val VIRTUAL_DISPLAY_DPI = 400
    }
}
