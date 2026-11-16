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
package com.vitorpamplona.amethyst.commons.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class AudioSpectrumTest {
    @Test
    fun normalizedToPeakScalesMaxToOne() {
        val out = floatArrayOf(0f, 2f, 4f, 1f).normalizedToPeak()
        assertEquals(1f, out[2], 1e-6f)
        assertEquals(0.5f, out[1], 1e-6f)
    }

    @Test
    fun normalizedToPeakHandlesAllZero() {
        val out = floatArrayOf(0f, 0f, 0f).normalizedToPeak()
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun normalizedToPeakReturnsNewArrayEvenForAllZero() {
        val input = floatArrayOf(0f, 0f, 0f)
        assertNotSame(input, input.normalizedToPeak())
    }

    @Test
    fun toLogBinsProducesRequestedCountClampedZeroToOne() {
        val mags = FloatArray(256) { if (it < 4) 1f else 0f }
        val bins = mags.toLogBins(32)
        assertEquals(32, bins.size)
        assertTrue(bins.all { it in 0f..1f })
        assertTrue(bins.first() > bins.last())
    }

    @Test
    fun normalizeToPeakInPlaceMatchesAllocatingVersion() {
        val input = floatArrayOf(0f, 2f, 4f, 1f)
        val expected = input.normalizedToPeak()
        val inPlace = input.copyOf()
        inPlace.normalizeToPeakInPlace()
        for (i in inPlace.indices) assertEquals(expected[i], inPlace[i], 1e-6f)
    }
}
