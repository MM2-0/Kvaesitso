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

import android.graphics.Rect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Stable
class MediaControllerState(
    // each composable has an ID.
    val id: String = Uuid.random().toString(),
    val controller: Player,
    // visibility onscreen
    val visibility: VisibilityData = VisibilityData(),
    // latest unrecoverable playback error, or null when playback is healthy. Set by the
    // Player.Listener wired in WatchPlaybackErrors and read by RenderPlaybackError to show
    // the codec-not-supported overlay with the "open in browser" fallback.
    val playbackError: MutableState<PlaybackException?> = mutableStateOf(null),
) {
    fun isPlaying() = controller.isPlaying

    fun currentMedia() = controller.currentMediaItem?.mediaId

    fun toggleMute() {
        controller.volume = if (controller.volume == 0f) 1f else 0f
    }

    fun togglePlayPause() {
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }
}

@Stable
class VisibilityData {
    var bounds: Rect? = null
        private set
    var distanceToCenter: Float? = null

    fun setBounds(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val current = bounds
        if (current == null) {
            bounds = Rect(left, top, right, bottom)
        } else {
            current.set(left, top, right, bottom)
        }
    }
}
