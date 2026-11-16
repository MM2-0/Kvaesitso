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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.ui.actions.uploads.downsampled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bars a recording reports.
 *
 * Sampling used to run once a second, so a five-second note produced five
 * numbers and the drawn waveform could not track speech at all. It now samples
 * ten times a second and reduces here, which is only worth doing if the
 * reduction keeps the shape and bounds the size.
 */
class VoiceWaveformDownsampleTest {
    @Test
    fun `a short recording is left exactly as it is`() {
        val short = listOf(1f, 2f, 3f)

        assertEquals(short, short.downsampled(100))
    }

    @Test
    fun `a long recording is bounded`() {
        // Ten minutes at ten samples a second. These travel inside events and
        // imeta tags, so the size has to depend on the detail wanted rather
        // than on how long somebody spoke.
        val long = List(6000) { it.toFloat() }

        assertEquals(100, long.downsampled(100).size)
    }

    @Test
    fun `reducing averages rather than dropping samples`() {
        // Taking every Nth would let one loud frame stand for a whole bucket
        // and make the bars flicker with the sampling phase. The first bucket
        // here averages 0..9, whose mean is 4.5.
        val ramp = List(1000) { it.toFloat() }

        val reduced = ramp.downsampled(100)

        assertEquals(4.5f, reduced.first(), 0.001f)
        assertEquals(994.5f, reduced.last(), 0.001f)
    }

    @Test
    fun `a loud burst survives the reduction`() {
        // Averaging must not flatten real signal away: a quiet recording with
        // one loud moment should still show that moment.
        val quietWithBurst = MutableList(1000) { 100f }.also { for (i in 500..509) it[i] = 30000f }

        val reduced = quietWithBurst.downsampled(100)

        assertTrue("the burst was averaged away: ${reduced.max()}", reduced.max() > 10000f)
    }
}
