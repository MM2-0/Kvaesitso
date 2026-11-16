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

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoReplayLimiterTest {
    private var pauseCalls = 0
    private val limiter = AutoReplayLimiter(maxAutoPlays = 5) { pauseCalls++ }

    private fun startPlaying() = limiter.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

    private fun completeOnePlay() = limiter.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

    private fun newMediaItem() = limiter.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

    @Test
    fun pausesWhenTheFifthPlayCompletes() {
        startPlaying()

        // plays 1..4 complete: repeat transitions 1..4 start plays 2..5
        repeat(4) { completeOnePlay() }
        assertEquals(0, pauseCalls)

        // play 5 completes: the 5th repeat transition would start play 6
        completeOnePlay()
        assertEquals(1, pauseCalls)
    }

    @Test
    fun pressingPlayGrantsAFreshAllowance() {
        startPlaying()
        repeat(5) { completeOnePlay() }
        assertEquals(1, pauseCalls)

        // user presses play again
        startPlaying()
        repeat(4) { completeOnePlay() }
        assertEquals(1, pauseCalls)

        completeOnePlay()
        assertEquals(2, pauseCalls)
    }

    @Test
    fun switchingMediaItemsResetsTheCount() {
        startPlaying()
        repeat(4) { completeOnePlay() }

        // the pooled player is handed a different video
        newMediaItem()

        repeat(4) { completeOnePlay() }
        assertEquals(0, pauseCalls)

        completeOnePlay()
        assertEquals(1, pauseCalls)
    }

    @Test
    fun pausingMidPlayDoesNotResetTheCount() {
        startPlaying()
        repeat(3) { completeOnePlay() }

        // a pause alone (e.g. scrolled off screen) does not reset; only a resume does
        limiter.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        repeat(2) { completeOnePlay() }
        assertEquals(1, pauseCalls)
    }
}
