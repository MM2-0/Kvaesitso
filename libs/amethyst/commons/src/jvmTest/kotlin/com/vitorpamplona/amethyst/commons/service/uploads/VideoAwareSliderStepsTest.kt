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

import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality.HIGH
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality.LOW
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality.MEDIUM
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality.UNCOMPRESSED
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality.VERY_HIGH
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoAwareSliderStepsTest {
    private val desktop = listOf(LOW, MEDIUM, HIGH, VERY_HIGH, UNCOMPRESSED)
    private val phone = listOf(LOW, MEDIUM, HIGH, UNCOMPRESSED)

    @Test
    fun videoStepsDropVeryHigh() {
        assertEquals(phone, VideoAwareSliderSteps.stepsFor(desktop, true))
        assertEquals(desktop, VideoAwareSliderSteps.stepsFor(desktop, false))
        assertEquals(phone, VideoAwareSliderSteps.stepsFor(phone, true))
    }

    @Test
    fun veryHighSnapsToHigh() {
        assertEquals(2, VideoAwareSliderSteps.snapForVideo(desktop, 3))
        assertEquals(2, VideoAwareSliderSteps.snapForVideo(desktop, 2))
        assertEquals(4, VideoAwareSliderSteps.snapForVideo(desktop, 4))
        assertEquals(3, VideoAwareSliderSteps.snapForVideo(phone, 3))
    }

    @Test
    fun displayedIndexRoundTrips() {
        for (p in desktop.indices) assertEquals(p, VideoAwareSliderSteps.fromDisplayedIndex(desktop, VideoAwareSliderSteps.toDisplayedIndex(desktop, p, false), false))
        assertEquals(3, VideoAwareSliderSteps.toDisplayedIndex(desktop, 4, true))
        assertEquals(4, VideoAwareSliderSteps.fromDisplayedIndex(desktop, 3, true))
        assertEquals(2, VideoAwareSliderSteps.toDisplayedIndex(desktop, 3, true))
        for (p in phone.indices) assertEquals(p, VideoAwareSliderSteps.fromDisplayedIndex(phone, VideoAwareSliderSteps.toDisplayedIndex(phone, p, true), true))
    }

    @Test
    fun uncompressedStaysLast() {
        assertEquals(desktop.lastIndex, VideoAwareSliderSteps.fromDisplayedIndex(desktop, VideoAwareSliderSteps.stepsFor(desktop, true).lastIndex, true))
    }
}
