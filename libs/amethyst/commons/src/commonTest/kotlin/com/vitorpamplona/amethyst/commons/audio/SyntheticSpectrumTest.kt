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
import kotlin.test.assertTrue

class SyntheticSpectrumTest {
    @Test
    fun frameHasRequestedBinsClampedZeroToOne() {
        val frame = SyntheticSpectrum.frame(timeSec = 1.0f, binCount = 48)
        assertEquals(48, frame.bins.size)
        assertTrue(frame.bins.all { it in 0f..1f })
    }

    @Test
    fun frameIsDeterministic() {
        val a = SyntheticSpectrum.frame(2.0f, 32).bins
        val b = SyntheticSpectrum.frame(2.0f, 32).bins
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun bassBinsCarryMoreEnergyThanTreble() {
        var low = 0f
        var high = 0f
        var t = 0f
        repeat(20) {
            val bins = SyntheticSpectrum.frame(t, 32).bins
            low += bins.take(6).sum()
            high += bins.takeLast(6).sum()
            t += 0.05f
        }
        assertTrue(low > high)
    }
}
