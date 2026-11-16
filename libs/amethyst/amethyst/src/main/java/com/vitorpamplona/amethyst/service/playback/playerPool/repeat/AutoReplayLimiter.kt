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
package com.vitorpamplona.amethyst.service.playback.playerPool.repeat

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Caps how many times a video loops on its own under [Player.REPEAT_MODE_ONE].
 *
 * Feed videos are configured to repeat forever (keepPlaying → REPEAT_MODE_ONE in
 * PlaybackService), which keeps decoders, network and the screen busy long after the
 * user stopped watching. This listener lets a video play [maxAutoPlays] full times and
 * then pauses it, so continuing requires an explicit press of the play button.
 *
 * Each loop under REPEAT_MODE_ONE surfaces as an [onMediaItemTransition] with
 * [Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT], marking one completed play. The
 * transition has already seeked back to the start, so pausing there leaves the video
 * on its first frame with the play button showing. Any resume — the user pressing
 * play, or the feed mutex auto-playing when the video scrolls back to the center —
 * grants a fresh allowance, and switching to a different media item resets it too.
 */
class AutoReplayLimiter(
    val maxAutoPlays: Int = DEFAULT_MAX_AUTO_PLAYS,
    val pause: () -> Unit,
) : Player.Listener {
    private var playsCompleted = 0

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            playsCompleted++
            if (playsCompleted >= maxAutoPlays) {
                pause()
            }
        } else {
            playsCompleted = 0
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (playWhenReady) {
            playsCompleted = 0
        }
    }

    companion object {
        const val DEFAULT_MAX_AUTO_PLAYS = 5
    }
}
