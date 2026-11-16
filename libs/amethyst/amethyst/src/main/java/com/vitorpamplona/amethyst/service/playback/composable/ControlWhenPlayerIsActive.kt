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

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia

@Composable
fun ControlWhenPlayerIsActive(
    mediaControllerState: MediaControllerState,
    automaticallyStartPlayback: Boolean,
    isClosestToTheCenterOfTheScreen: MutableState<Boolean>,
) {
    val controller = mediaControllerState.controller

    LaunchedEffect(key1 = isClosestToTheCenterOfTheScreen.value, key2 = mediaControllerState) {
        // active means being fully visible
        if (isClosestToTheCenterOfTheScreen.value) {
            // should auto start video from settings?
            if (!automaticallyStartPlayback) {
                if (controller.isPlaying) {
                    // if it is visible, it's playing but it wasn't supposed to start automatically.
                    controller.pause()
                }
            } else if (!controller.isPlaying) {
                // if it is visible, was supposed to start automatically, but it's not

                // If something else is playing, play on mute.
                if (BackgroundMedia.hasBackgroundButNot(mediaControllerState)) {
                    controller.volume = 0f
                }
                controller.play()
            }
        } else if (controller.isPlaying) {
            // Pauses the video when it becomes invisible.
            // Destroys the video later when it Disposes the element
            // meanwhile if the user comes back, the position in the track is saved.
            controller.pause()
        }
    }

    val view = LocalView.current

    // Keeps the screen on while playing and viewing videos.
    DisposableEffect(key1 = controller, key2 = view) {
        val listener = PlayerEventListener(view)

        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
            listener.destroy()
        }
    }

    // Pause when the host activity leaves the foreground; resume the visible
    // video when it comes back. The scroll-based mutex above only reacts to
    // on-screen position, which doesn't change on background — so without
    // this, videos play forever behind another app.
    // Skip the explicit BackgroundMedia (PiP) instance: the user opted that
    // one into keep-playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaControllerState) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        if (controller.isPlaying && !BackgroundMedia.isMutex(mediaControllerState)) {
                            controller.pause()
                        }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        if (automaticallyStartPlayback &&
                            isClosestToTheCenterOfTheScreen.value &&
                            !controller.isPlaying
                        ) {
                            if (BackgroundMedia.hasBackgroundButNot(mediaControllerState)) {
                                controller.volume = 0f
                            }
                            controller.play()
                        }
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Pauses [mediaControllerState] when the host activity leaves the foreground.
 *
 * Standalone version of the ON_PAUSE arm of [ControlWhenPlayerIsActive] for
 * callers (e.g. voice notes) that don't have the visibility mutex / auto-resume
 * logic but still need to stop playback when the app backgrounds. Pairs with
 * the 30s release timer in [GetVideoController] — pause immediately, release
 * the controller after the timeout, reassemble on resume.
 *
 * Skips the explicit BackgroundMedia (PiP) instance: that one is opted in to
 * keep-playing.
 */
@Composable
fun PauseControllerWhenInBackground(mediaControllerState: MediaControllerState) {
    val controller = mediaControllerState.controller
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaControllerState) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE &&
                    controller.isPlaying &&
                    !BackgroundMedia.isMutex(mediaControllerState)
                ) {
                    controller.pause()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

class PlayerEventListener(
    val view: View,
) : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // doesn't consider the mutex because the screen can turn off if the video
        // being played in the mutex is not visible.
        if (view.keepScreenOn != isPlaying) {
            view.keepScreenOn = isPlaying
        }
    }

    fun destroy() {
        if (view.keepScreenOn) {
            view.keepScreenOn = false
        }
    }
}
