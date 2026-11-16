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
package com.vitorpamplona.amethyst.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The audio player sizes itself as a square (see AudioPlayerSquare.audioSquare), which is taller than
 * 16:9. Handing audio the video default therefore cages the square in a shorter, centre-aligned box
 * that nothing clips, and the player — visualizer included — paints over the note header above and
 * the reactions row below. Audio must stay ratio-less so the box wraps the square instead.
 */
class UnknownMediaAspectRatioTest {
    @Test
    fun unknownVideoAssumes16by9() {
        assertEquals(16f / 9f, unknownMediaAspectRatio(null, "https://example.com/a.mp4"))
    }

    @Test
    fun liveStreamPlaylistKeeps16by9() {
        assertEquals(16f / 9f, unknownMediaAspectRatio(null, "https://example.com/stream.m3u8"))
    }

    @Test
    fun bareAudioUrlHasNoRatio() {
        assertNull(unknownMediaAspectRatio(null, "https://haven.sdbitcoiners.com/f28a5a2e.mp3"))
    }

    @Test
    fun audioMimeHasNoRatioEvenWithoutAnExtension() {
        assertNull(unknownMediaAspectRatio("audio/mpeg", "https://example.com/download?id=7"))
    }

    @Test
    fun videoMimeWinsOverNothing() {
        assertEquals(16f / 9f, unknownMediaAspectRatio("video/mp4", "https://example.com/download?id=7"))
    }

    @Test
    fun unknownEverythingAssumes16by9() {
        assertEquals(16f / 9f, unknownMediaAspectRatio(null, "https://example.com/download?id=7"))
    }
}
