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
package com.vitorpamplona.amethyst.service.playback.pip

import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.ui.theme.VoiceHeightModifier
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.controls.PIP_PRESHRINK_MAX_SHORT_SIDE_PX
import com.vitorpamplona.amethyst.service.playback.composable.controls.constrainVideoQualityToViewport
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.ui.components.getActivity

@OptIn(UnstableApi::class)
@Composable
fun RenderPipVideo(
    controller: MediaControllerState,
    waveformData: WaveformData?,
) {
    KeepScreenOnWhilePlaying(controller)

    val modifier =
        remember {
            val ratio =
                controller.currentMedia()?.let {
                    MediaAspectRatioCache.get(it)
                }

            if (ratio != null) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }
        }

    // processIntentForPiP calls enterPictureInPictureMode from composition (PiPFromIntents), so the
    // first layout pass can measure the activity at full screen before the window shrinks. Cap that
    // measurement instead of skipping it: skipping would leave a pooled player on whatever viewport
    // its previous view pushed if PiP is never actually entered (per-app PiP off, no
    // FEATURE_PICTURE_IN_PICTURE). The shrink relayouts and pushes the real size.
    val activity = LocalActivity.current

    Box(
        modifier.constrainVideoQualityToViewport(
            player = controller.controller,
            maxShortSidePx = {
                if (activity?.isInPictureInPictureMode == true) 0 else PIP_PRESHRINK_MAX_SHORT_SIDE_PX
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        ContentFrame(
            player = controller.controller,
            keepContentOnReset = true,
            contentScale = ContentScale.Crop,
        )

        Row(VoiceHeightModifier, verticalAlignment = Alignment.CenterVertically) {
            waveformData?.let { Waveform(it, controller, Modifier) }
        }
    }
}

@Composable
fun KeepScreenOnWhilePlaying(controller: MediaControllerState) {
    val view = LocalView.current

    DisposableEffect(controller.controller, view) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (view.keepScreenOn != isPlaying) {
                        view.keepScreenOn = isPlaying
                    }
                }
            }

        // Set initial state
        view.keepScreenOn = controller.controller.isPlaying

        controller.controller.addListener(listener)
        onDispose {
            controller.controller.removeListener(listener)
            view.keepScreenOn = false
        }
    }
}

@Composable
fun RegisterControllerReceiver(controllerState: MediaControllerState) {
    val context = LocalContext.current

    // Keyed on the controller too: the PiP activity is reused for the next video, and a receiver
    // registered for the first one kept toggling that player's state.
    DisposableEffect(context, controllerState) {
        val receiver =
            ActionReceiver(
                onMute = controllerState::toggleMute,
                onPlayPause = controllerState::togglePlayPause,
            )

        // Not exported: the PiP buttons' PendingIntents are package-scoped broadcasts from this app,
        // and nothing else should be able to play, pause or mute the user's video.
        ContextCompat.registerReceiver(context, receiver, receiver.filterMute, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(context, receiver, receiver.filterPlayPause, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun WatchControllerForActions(
    mediaItemData: MediaItemData,
    controllerState: MediaControllerState,
) {
    val playPauseState = rememberPlayPauseButtonState(controllerState.controller)
    val muteState = rememberMuteButtonState(controllerState.controller)

    val activity = LocalContext.current.getActivity()
    LaunchedEffect(activity, playPauseState.showPlay, muteState.showMuted) {
        activity.setPictureInPictureParams(
            activity.makePipParams(
                isPlaying = !playPauseState.showPlay,
                isMuted = !muteState.showMuted,
                ratio = MediaAspectRatioCache.videoRatio(mediaItemData.videoUri, mediaItemData.aspectRatio),
                bounds = null,
            ),
        )
    }
}
