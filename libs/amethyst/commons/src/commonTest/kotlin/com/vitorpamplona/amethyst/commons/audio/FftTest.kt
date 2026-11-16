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

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FftTest {
    @Test
    fun sineProducesPeakAtItsBin() {
        val n = 64
        val k = 8
        val signal = FloatArray(n) { sin(2.0 * PI * k * it / n).toFloat() }

        val mags = Fft.magnitudes(signal)

        assertEquals(n / 2 + 1, mags.size)
        val peakBin = mags.indices.maxByOrNull { mags[it] }
        assertEquals(k, peakBin)
    }

    @Test
    fun dcOffsetLandsInBinZero() {
        val n = 32
        val signal = FloatArray(n) { 0.5f }

        val mags = Fft.magnitudes(signal)

        val peakBin = mags.indices.maxByOrNull { mags[it] }
        assertEquals(0, peakBin)
    }

    @Test
    fun rejectsNonPowerOfTwo() {
        var threw = false
        try {
            Fft.magnitudes(FloatArray(30))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun magnitudesIntoMatchesMagnitudes() {
        val n = 64
        val signal = FloatArray(n) { sin(2.0 * PI * 5 * it / n).toFloat() }
        val expected = Fft.magnitudes(signal)
        val out = FloatArray(n / 2 + 1)
        Fft.magnitudesInto(signal, DoubleArray(n), DoubleArray(n), out)
        for (i in out.indices) assertEquals(expected[i], out[i], 1e-3f)
    }
}
