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

class AudioWindowTest {
    @Test
    fun hannIsZeroAtEndsAndOneInMiddle() {
        val w = AudioWindow.hann(9)
        assertEquals(0f, w.first(), 1e-5f)
        assertEquals(0f, w.last(), 1e-5f)
        assertEquals(1f, w[4], 1e-4f)
    }

    @Test
    fun shortsToWindowedNormalizesAndPadsToWindowLength() {
        val window = FloatArray(4) { 1f }
        val out = AudioWindow.shortsToWindowed(shortArrayOf(32767, -32768), window)
        assertEquals(4, out.size)
        assertTrue(out[0] in 0.99f..1.01f)
        assertTrue(out[1] in -1.01f..-0.99f)
        assertEquals(0f, out[2])
        assertEquals(0f, out[3])
    }

    @Test
    fun shortsToWindowedIntoMatchesAllocatingVersion() {
        val window = AudioWindow.hann(8)
        val samples = ShortArray(8) { (it * 1000).toShort() }
        val expected = AudioWindow.shortsToWindowed(samples, window)
        val out = FloatArray(8)
        AudioWindow.shortsToWindowedInto(samples, window, out)
        for (i in out.indices) assertEquals(expected[i], out[i], 1e-6f)
    }
}
