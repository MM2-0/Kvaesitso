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
package com.vitorpamplona.amethyst.service.playback.composable

import android.content.Context
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import com.vitorpamplona.amethyst.commons.ui.playback.audioSquare
import com.vitorpamplona.amethyst.commons.ui.playback.controls.BottomGradientOverlay
import com.vitorpamplona.amethyst.commons.ui.playback.controls.TopGradientOverlay
import com.vitorpamplona.amethyst.commons.ui.playback.shouldSquareAudioPlayer
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.controls.FullscreenSwipeControlsState
import com.vitorpamplona.amethyst.service.playback.composable.controls.FullscreenSwipeLevelIndicator
import com.vitorpamplona.amethyst.service.playback.composable.controls.LogVideoQualitySelection
import com.vitorpamplona.amethyst.service.playback.composable.controls.METERED_MAX_SHORT_SIDE_PX
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderAnimatedBottomInfo
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderCaptions
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderCenterButtons
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderTopButtons
import com.vitorpamplona.amethyst.service.playback.composable.controls.applyViewportConstraint
import com.vitorpamplona.amethyst.service.playback.composable.controls.clampViewportShortSide
import com.vitorpamplona.amethyst.service.playback.composable.controls.fullscreenSwipeControls
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.AudioPlayingAnimation
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.rememberIsAudioTrack
import com.vitorpamplona.amethyst.ui.components.RequestHdrFor

internal const val SKIP_SECONDS = 10
internal const val SKIP_MILLIS = SKIP_SECONDS * 1000L

internal fun Player.seekBackward() {
    seekTo((currentPosition - SKIP_MILLIS).coerceAtLeast(0))
}

internal fun Player.skipForward() {
    val newPosition = currentPosition + SKIP_MILLIS
    seekTo(if (duration > 0) newPosition.coerceAtMost(duration) else newPosition)
}

private fun getVideoSizeDp(player: Player): Size? {
    var videoSize = Size(player.videoSize.width.toFloat(), player.videoSize.height.toFloat())

    if (videoSize.width == 0f || videoSize.height == 0f) return null

    val par = player.videoSize.pixelWidthHeightRatio
    if (par < 1.0) {
        videoSize = videoSize.copy(width = videoSize.width * par)
    } else if (par > 1.0) {
        videoSize = videoSize.copy(height = videoSize.height / par)
    }
    return videoSize
}

@Composable
@OptIn(UnstableApi::class)
fun RenderVideoPlayer(
    mediaItem: LoadedMediaItem,
    controllerState: MediaControllerState,
    thumbData: VideoThumb?,
    showControls: Boolean = true,
    contentScale: ContentScale,
    borderModifier: Modifier,
    videoModifier: Modifier,
    onDialog: (() -> Unit)? = null,
    controllerVisible: MutableState<Boolean> = remember { mutableStateOf(false) },
    hasBlurhash: Boolean = false,
    isFullscreen: Boolean = false,
    accountViewModel: AccountViewModel,
) {
    // Hold the container size in a non-state holder so layout passes don't trigger an
    // unnecessary recomposition of the whole player tree just to update a value that is only
    // ever read inside the onDoubleTap callback below.
    val containerWidth = remember { intArrayOf(0) }

    // Last measured player size, kept out of snapshot state for the same reason as containerWidth:
    // it exists only so a connectivity flip can re-push the viewport without a layout pass.
    val lastMeasured = remember { intArrayOf(0, 0) }
    val isMetered by accountViewModel.settings.isMobileOrMeteredConnection.collectAsStateWithLifecycle()
    // Fullscreen is exempt: the cap exists to hold back feeds that autoplay without being asked,
    // and someone who tapped into fullscreen on mobile data asked. Capping there would also put a
    // ceiling the quality menu's "Auto" could not exceed.
    val viewportCeiling = if (isMetered && !isFullscreen) METERED_MAX_SHORT_SIDE_PX else 0
    val isLive = remember(mediaItem.src.videoUri, mediaItem.src.mimeType) { isHlsMedia(mediaItem.src.videoUri, mediaItem.src.mimeType) }

    val swipeState = remember { FullscreenSwipeControlsState() }
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    // Brightness is applied to the fullscreen dialog window so it auto-reverts on dismiss.
    // Returns null for the inline feed player (not inside a dialog) — fine, gated by isFullscreen below.
    val dialogWindow = getDialogWindow()

    // The gesture detectors below run in pointerInput coroutines that outlive recompositions, so
    // they read these through rememberUpdatedState instead of capturing the parameters: the
    // caller can hand in a new controllerVisible, and GetVideoController swaps the controller
    // when late captions rebuild the media item. A captured value would keep the old one, which
    // nothing draws (taps stop showing the controls) or which is released (swipes and double
    // taps do nothing). Reading the current value also avoids restarting the detectors, which
    // would drop a gesture in progress.
    val currentControllerState by rememberUpdatedState(controllerState)
    val currentControllerVisible by rememberUpdatedState(controllerVisible)

    // Sync the per-video mute (Media3 player volume) with the volume swipe. Mirrors the mute
    // button's handler exactly, so its listener-driven icon flips when the swipe mutes/unmutes,
    // and the global default carries to the next video.
    val isVideoMuted = { currentControllerState.controller.volume < 0.001f }
    val setVideoMuted = { mute: Boolean ->
        DEFAULT_MUTED_SETTING.value = mute
        currentControllerState.controller.volume = if (mute) 0f else 1f
    }

    // Belt-and-suspenders: clear any brightness override when this player leaves composition, so
    // exiting fullscreen never leaves the screen dimmed. releaseBrightness no-ops when dialogWindow
    // is null (the inline feed path) or when no override was applied, so this is safe unconditionally.
    DisposableEffect(Unit) {
        onDispose { swipeState.releaseBrightness(dialogWindow) }
    }

    WatchPlaybackErrors(controllerState)
    LogVideoQualitySelection(controllerState.controller)

    // Moving on or off mobile data does not relayout, so the new ceiling has to be pushed by hand.
    // Before the first measurement lastMeasured is still zero and applyViewportConstraint no-ops.
    LaunchedEffect(viewportCeiling, controllerState.controller) {
        val (w, h) = clampViewportShortSide(lastMeasured[0], lastMeasured[1], viewportCeiling)
        applyViewportConstraint(controllerState.controller, w, h)
    }

    // Audio files have no video dimensions, so without this the player collapses to a thin strip and
    // the controls get crammed. Size it square (capped) so the visualizer and controls get room.
    // Voice notes keep their seek-bar strip; the full-screen dialog fills the screen.
    val tracksAreAudio by rememberIsAudioTrack(controllerState.controller)
    val squarePlayer =
        shouldSquareAudioPlayer(
            isFullscreen = isFullscreen,
            isVoiceNote = mediaItem.src.waveformData != null,
            isAudioMime = mediaItem.src.mimeType?.startsWith("audio/") == true,
            tracksAreAudio = tracksAreAudio,
        )
    val playerModifier = if (squarePlayer) borderModifier.audioSquare() else borderModifier

    Box(
        modifier =
            playerModifier
                .onSizeChanged {
                    containerWidth[0] = it.width
                    lastMeasured[0] = it.width
                    lastMeasured[1] = it.height
                    val (w, h) = clampViewportShortSide(it.width, it.height, viewportCeiling)
                    applyViewportConstraint(controllerState.controller, w, h)
                }.pointerInput(isLive) {
                    detectTapGestures(
                        onTap = { currentControllerVisible.value = !currentControllerVisible.value },
                        onDoubleTap = { offset ->
                            if (!isLive) {
                                val isLeftSide = offset.x < containerWidth[0] / 2
                                if (isLeftSide) {
                                    currentControllerState.controller.seekBackward()
                                } else {
                                    currentControllerState.controller.skipForward()
                                }
                            }
                        },
                    )
                }.then(
                    if (isFullscreen) {
                        Modifier.fullscreenSwipeControls(
                            state = swipeState,
                            audioManager = audioManager,
                            window = dialogWindow,
                            resolver = context.contentResolver,
                            isMuted = isVideoMuted,
                            setMuted = setVideoMuted,
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        // Fullscreen plays into a SurfaceView so the decoder hands HDR frames straight to the
        // compositor. The feed keeps its TextureView, which clips, scales and sits in a lazy
        // list the way a SurfaceView can't; HDR is only requested where the surface can show it.
        val surfaceType = if (isFullscreen) SURFACE_TYPE_SURFACE_VIEW else SURFACE_TYPE_TEXTURE_VIEW
        if (surfaceType == SURFACE_TYPE_SURFACE_VIEW) RequestHdrFor(controllerState.controller)

        ContentFrame(
            player = controllerState.controller,
            modifier = videoModifier,
            surfaceType = surfaceType,
            contentScale = contentScale,
            // Transparent shutter — media3's default is an opaque black Box that flashes
            // until EVENT_RENDERED_FIRST_FRAME fires. Letting the layer beneath show
            // through (blurhash backdrop in the with-blurhash branch, or the post
            // background otherwise) avoids the black blink when re-entering the feed.
            shutter = {},
        )

        // Above the picture but below the controls, so the transport bar and the bottom gradient
        // draw over the cues rather than the other way around.
        if (mediaItem.src.captions.isNotEmpty()) {
            RenderCaptions(
                controllerState = controllerState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        RenderPlaybackError(
            controllerState = controllerState,
            videoUri = mediaItem.src.videoUri,
            modifier = Modifier.align(Alignment.Center),
        )

        val visualizerStyle by accountViewModel.audioVisualizerFlow().collectAsStateWithLifecycle()
        AudioPlayingAnimation(
            controllerState = controllerState,
            waveform = mediaItem.src.waveformData,
            mediaId = mediaItem.src.videoUri,
            style = visualizerStyle,
            isAudio = tracksAreAudio,
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
            hasBlurhash = hasBlurhash,
        )

        if (showControls && controllerState.playbackError.value == null) {
            TopGradientOverlay(
                controllerVisible = controllerVisible,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            BottomGradientOverlay(
                controllerVisible = controllerVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            RenderTopButtons(
                mediaData = mediaItem.src,
                controllerState = controllerState,
                controllerVisible = controllerVisible,
                onZoomClick = onDialog,
                modifier = Modifier.align(Alignment.TopEnd),
                accountViewModel = accountViewModel,
            )

            RenderAnimatedBottomInfo(controllerState, controllerVisible, Modifier.align(Alignment.BottomCenter))

            RenderCenterButtons(
                controllerState = controllerState,
                controllerVisible = controllerVisible,
                videoUri = mediaItem.src.videoUri,
                modifier = Modifier.align(Alignment.Center),
                isLiveStream = isLive,
            )
        }

        if (isFullscreen) {
            FullscreenSwipeLevelIndicator(swipeState, isMuted = isVideoMuted)
        }
    }
}
