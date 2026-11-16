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
package com.vitorpamplona.amethyst.service.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastMediaSummaryTest {
    @Test
    fun namesTheCodecTheReceiverIsMostLikelyToRefuse() {
        // The whole point: "HEVC" is the word that explains why a TV took the file and went quiet.
        assertEquals(
            "H.265 (HEVC) 1920x1080",
            summarizeCastFormat("video/hevc", codecs = "hev1.1.6.L93.B0", width = 1920, height = 1080),
        )
    }

    @Test
    fun namesTheCommonCodecsInTermsPeopleRecognise() {
        assertEquals("H.264 640x480", summarizeCastFormat("video/avc", null, 640, 480))
        assertEquals("AV1 3840x2160", summarizeCastFormat("video/av01", null, 3840, 2160))
        assertEquals("VP9 1280x720", summarizeCastFormat("video/x-vnd.on2.vp9", null, 1280, 720))
    }

    @Test
    fun fallsBackToTheRawMimeTypeForCodecsWeDoNotNameOurselves() {
        assertEquals("video/quirky 100x50", summarizeCastFormat("video/quirky", null, 100, 50))
    }

    @Test
    fun omitsTheResolutionWhenItIsNotKnown() {
        assertEquals("H.264", summarizeCastFormat("video/avc", null, width = -1, height = -1))
        assertEquals("H.264", summarizeCastFormat("video/avc", null, width = 0, height = 0))
    }

    @Test
    fun fallsBackToTheCodecStringWhenTheMimeTypeIsMissing() {
        // ExoPlayer can report a codec string without a sample mime type on some containers.
        assertEquals("hev1.1.6.L93.B0 1920x1080", summarizeCastFormat(null, "hev1.1.6.L93.B0", 1920, 1080))
    }

    @Test
    fun isNullWhenThereIsNothingWorthSaying() {
        assertNull(summarizeCastFormat(null, null, -1, -1))
        assertNull(summarizeCastFormat("", "", 0, 0))
    }
}
