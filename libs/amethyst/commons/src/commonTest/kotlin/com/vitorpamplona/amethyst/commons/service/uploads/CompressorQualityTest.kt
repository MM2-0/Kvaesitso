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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompressorQualityTest {
    @Test
    fun stepsGrowAndEndUncompressed() {
        val steps = CompressorQuality.sliderSteps
        assertEquals(CompressorQuality.UNCOMPRESSED, steps.last())
        assertEquals(steps.lastIndex, CompressorQuality.uncompressedSliderPosition)
        val sizes = steps.dropLast(1).map { assertNotNull(it.imageMaxDimension) }
        assertEquals(sizes.sorted(), sizes)
        assertEquals(sizes.distinct(), sizes)
    }

    @Test
    fun defaultIsACompressingStep() {
        val position = CompressorQuality.defaultSliderPosition
        assertTrue(position in 0 until CompressorQuality.uncompressedSliderPosition)
        assertNotNull(CompressorQuality.fromSlider(position).imageMaxDimension)
    }

    @Test
    fun outOfRangePositionsFallBackToTheDefault() {
        val default = CompressorQuality.fromSlider(CompressorQuality.defaultSliderPosition)
        assertEquals(default, CompressorQuality.fromSlider(-1))
        assertEquals(default, CompressorQuality.fromSlider(CompressorQuality.sliderSteps.size))
    }
}
