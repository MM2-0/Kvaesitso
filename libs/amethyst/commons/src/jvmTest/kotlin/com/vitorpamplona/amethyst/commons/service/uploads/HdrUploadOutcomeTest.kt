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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoHdrInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HdrUploadOutcomeTest {
    private val sdr = VideoHdrInfo(isHdr = false, canKeepHdr = false)
    private val keepableHdr = VideoHdrInfo(isHdr = true, canKeepHdr = true)
    private val unkeepableHdr = VideoHdrInfo(isHdr = true, canKeepHdr = false)

    @Test
    fun nothingProbedYetShowsNothing() {
        assertNull(hdrUploadOutcome(emptyList(), useH265 = true))
        assertNull(hdrUploadOutcome(emptyList(), useH265 = false))
    }

    @Test
    fun sdrVideosShowNothingWhateverTheCodec() {
        assertNull(hdrUploadOutcome(listOf(sdr, sdr), useH265 = true))
        assertNull(hdrUploadOutcome(listOf(sdr), useH265 = false))
    }

    @Test
    fun h264AlwaysConvertsEvenWhereTheDeviceCouldKeepHdr() {
        assertEquals(HdrUploadOutcome.CONVERTED_BY_CODEC, hdrUploadOutcome(listOf(keepableHdr), useH265 = false))
        assertEquals(HdrUploadOutcome.CONVERTED_BY_CODEC, hdrUploadOutcome(listOf(unkeepableHdr), useH265 = false))
    }

    @Test
    fun h265KeepsHdrWhenTheDeviceCan() {
        assertEquals(HdrUploadOutcome.KEPT, hdrUploadOutcome(listOf(keepableHdr), useH265 = true))
    }

    @Test
    fun h265OnAnIncapableDeviceConverts() {
        assertEquals(HdrUploadOutcome.CONVERTED_BY_DEVICE, hdrUploadOutcome(listOf(unkeepableHdr), useH265 = true))
    }

    @Test
    fun anHdrClipAfterAnSdrOneStillGetsTheLine() {
        assertEquals(HdrUploadOutcome.KEPT, hdrUploadOutcome(listOf(sdr, keepableHdr), useH265 = true))
        assertEquals(HdrUploadOutcome.CONVERTED_BY_CODEC, hdrUploadOutcome(listOf(sdr, keepableHdr), useH265 = false))
    }
}
