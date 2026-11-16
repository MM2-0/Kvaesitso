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

import com.vitorpamplona.amethyst.commons.ui.playback.audioSquareSide
import com.vitorpamplona.amethyst.commons.ui.playback.shouldSquareAudioPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerSquareTest {
    @Test
    fun squareWhenMimeSaysAudio() {
        assertTrue(shouldSquareAudioPlayer(isFullscreen = false, isVoiceNote = false, isAudioMime = true, tracksAreAudio = false))
    }

    @Test
    fun squareWhenTracksResolveAudio() {
        assertTrue(shouldSquareAudioPlayer(isFullscreen = false, isVoiceNote = false, isAudioMime = false, tracksAreAudio = true))
    }

    @Test
    fun noSquareForVideo() {
        assertFalse(shouldSquareAudioPlayer(isFullscreen = false, isVoiceNote = false, isAudioMime = false, tracksAreAudio = false))
    }

    @Test
    fun noSquareForVoiceNoteEvenIfAudio() {
        assertFalse(shouldSquareAudioPlayer(isFullscreen = false, isVoiceNote = true, isAudioMime = true, tracksAreAudio = true))
    }

    @Test
    fun noSquareInFullscreen() {
        assertFalse(shouldSquareAudioPlayer(isFullscreen = true, isVoiceNote = false, isAudioMime = true, tracksAreAudio = true))
    }

    @Test
    fun sideIsWidthBelowCap() {
        assertEquals(360, audioSquareSide(widthPx = 360, maxHeightPx = 1000))
    }

    @Test
    fun sideIsCappedAboveCap() {
        assertEquals(1000, audioSquareSide(widthPx = 1800, maxHeightPx = 1000))
    }

    @Test
    fun sideEqualsCapAtBoundary() {
        assertEquals(1000, audioSquareSide(widthPx = 1000, maxHeightPx = 1000))
    }

    @Test
    fun sideIsZeroWhenWidthIsZero() {
        assertEquals(0, audioSquareSide(widthPx = 0, maxHeightPx = 1000))
    }
}
