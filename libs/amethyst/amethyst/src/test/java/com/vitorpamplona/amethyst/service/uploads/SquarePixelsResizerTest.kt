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
package com.vitorpamplona.amethyst.service.uploads

import com.davotoula.lightcompressor.config.VideoResizer
import org.junit.Assert.assertEquals
import org.junit.Test

/** LightCompressor stretches frames to whatever size the resizer returns and writes no pixel ratio. */
class SquarePixelsResizerTest {
    @Test
    fun narrowPixelsShrinkTheWidthToTheDisplayShape() {
        // 1080x720 stored with 4:9 pixels is a 480x720 portrait video.
        assertEquals(Pair(480.0, 720.0), SquarePixelsResizer(4f / 9f, null).resize(1080.0, 720.0))
    }

    @Test
    fun widePixelsGrowTheWidth() {
        // DV widescreen: 720x480 stored with 32:27 pixels shows as 853x480, evened down for the encoder.
        assertEquals(Pair(852.0, 480.0), SquarePixelsResizer(32f / 27f, null).resize(720.0, 480.0))
    }

    @Test
    fun theLimitAppliesToTheDisplayShape() {
        val (w, h) = SquarePixelsResizer(4f / 9f, VideoResizer.limitShortSide(360.0)).resize(1080.0, 720.0)
        assertEquals(360.0, minOf(w, h), 0.0)
        assertEquals(480.0 / 720.0, w / h, 0.01)
    }

    @Test
    fun displayWidthLeavesSquarePixelsAlone() {
        assertEquals(1920, displayWidth(1920, null))
        assertEquals(480, displayWidth(1080, 4f / 9f))
    }
}
