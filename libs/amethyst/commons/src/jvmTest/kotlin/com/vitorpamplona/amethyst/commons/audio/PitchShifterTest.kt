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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchShifterTest {
    private val sampleRate = 44100

    /** One second of a clean mono sine at [freq] Hz, amplitude 0.8. */
    private fun sine(
        freq: Double,
        seconds: Double = 1.0,
    ): FloatArray {
        val n = (sampleRate * seconds).toInt()
        return FloatArray(n) { i -> (0.8 * sin(2.0 * PI * freq * i / sampleRate)).toFloat() }
    }

    /**
     * Estimates the fundamental frequency of a (near-)periodic signal via
     * autocorrelation over a stable middle window. Dependency-free and robust
     * for sine inputs.
     */
    private fun estimateFrequency(signal: FloatArray): Double {
        // Use the central 60% to avoid edge ramp-up/ramp-down artifacts.
        val start = (signal.size * 0.2).toInt()
        val end = (signal.size * 0.8).toInt()
        val chunk = signal.copyOfRange(start, end)

        val minFreq = 50.0
        val maxFreq = 2000.0
        val minLag = (sampleRate / maxFreq).toInt()
        val maxLag = (sampleRate / minFreq).toInt().coerceAtMost(chunk.size - 1)

        var bestLag = minLag
        var bestCorr = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until chunk.size - lag) {
                corr += chunk[i] * chunk[i + lag]
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        return sampleRate.toDouble() / bestLag
    }

    @Test
    fun `ratio of one preserves length`() {
        val input = sine(440.0)
        val output = PitchShifter().shift(input, 1.0)
        // Duration must be preserved (within a frame of tolerance).
        assertEquals(input.size.toDouble(), output.size.toDouble(), 4096.0)
    }

    @Test
    fun `shift up one octave roughly doubles the fundamental`() {
        val output = PitchShifter().shift(sine(440.0), 2.0)
        val measured = estimateFrequency(output)
        assertEquals(880.0, measured, 880.0 * 0.06)
    }

    @Test
    fun `shift down one octave roughly halves the fundamental`() {
        val output = PitchShifter().shift(sine(440.0), 0.5)
        val measured = estimateFrequency(output)
        assertEquals(220.0, measured, 220.0 * 0.06)
    }

    @Test
    fun `preserves duration for the deep preset ratio`() {
        // DEEP preset lowers pitch: frequencyRatio = 1 / 1.4.
        val input = sine(300.0)
        val output = PitchShifter().shift(input, 1.0 / 1.4)
        assertEquals(input.size.toDouble(), output.size.toDouble(), 4096.0)
        val measured = estimateFrequency(output)
        assertEquals(300.0 / 1.4, measured, (300.0 / 1.4) * 0.06)
    }

    @Test
    fun `preserves duration for the high preset ratio`() {
        // HIGH preset raises pitch: frequencyRatio = 1 / 0.75.
        val input = sine(300.0)
        val output = PitchShifter().shift(input, 1.0 / 0.75)
        assertEquals(input.size.toDouble(), output.size.toDouble(), 4096.0)
        val measured = estimateFrequency(output)
        assertEquals(300.0 / 0.75, measured, (300.0 / 0.75) * 0.06)
    }

    @Test
    fun `empty input returns empty output`() {
        val output = PitchShifter().shift(FloatArray(0), 1.5)
        assertTrue(output.isEmpty())
    }
}
