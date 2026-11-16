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

import kotlin.math.roundToInt

/**
 * Pure-Kotlin pitch shifter for mono float PCM.
 *
 * Shifts pitch by a frequency [ratio][shift] while preserving the original
 * duration, by combining two textbook DSP steps:
 *
 * 1. **WSOLA time-stretch** (Waveform Similarity Overlap-Add) stretches the
 *    signal in time by the pitch ratio without changing its pitch. Unlike plain
 *    overlap-add, WSOLA searches a small window around each analysis frame for
 *    the offset that best correlates with the natural continuation of the
 *    previous frame, which avoids the phase-discontinuity "warble" of OLA.
 * 2. **Linear resampling** back to the original length raises (or lowers) the
 *    pitch by the same ratio and restores the duration.
 *
 * This is a clean-room implementation of the standard WSOLA algorithm; it
 * carries no third-party code and keeps the app free of GPL-licensed DSP
 * dependencies. Quality is more than sufficient for voice anonymization, whose
 * pitch factors are modest (~0.7x–1.5x).
 */
class PitchShifter(
    /** Analysis/synthesis window length in samples. ~46 ms at 44.1 kHz. */
    private val frameSize: Int = 2048,
    /** Half-radius (in samples) of the WSOLA cross-correlation search. */
    private val seekWindow: Int = 256,
) {
    private val synthesisHop = frameSize / 2
    private val overlap = frameSize - synthesisHop
    private val window = AudioWindow.hann(frameSize)

    /**
     * Returns [input] pitch-shifted by [frequencyRatio] with the same length.
     *
     * @param frequencyRatio output-to-input frequency multiplier. `> 1` raises
     *   the pitch, `< 1` lowers it, `1.0` is a no-op copy.
     */
    fun shift(
        input: FloatArray,
        frequencyRatio: Double,
    ): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        if (frequencyRatio == 1.0) return input.copyOf()

        // Stretch in time by the ratio (pitch unchanged, length scaled), then
        // resample back to the original length (pitch scaled by the ratio).
        val stretched = timeStretch(input, frequencyRatio)
        return resampleLinear(stretched, input.size)
    }

    /**
     * WSOLA time-stretch. Output length is approximately `input.size * stretch`.
     */
    private fun timeStretch(
        input: FloatArray,
        stretch: Double,
    ): FloatArray {
        if (input.size < frameSize + seekWindow) {
            // Too short for the overlap machinery; resampling alone still yields
            // the requested length change without crashing on tiny clips.
            return resampleLinear(input, (input.size * stretch).roundToInt().coerceAtLeast(1))
        }

        val analysisHop = synthesisHop / stretch
        val outLength = (input.size * stretch).roundToInt() + frameSize
        val out = FloatArray(outLength)
        val norm = FloatArray(outLength)

        // Integer start of the current analysis frame inside the input.
        var analysisStart = 0
        // Fractional nominal position; the next frame is sought around it.
        var nominal = 0.0
        var synthesisPos = 0

        while (analysisStart + frameSize < input.size && synthesisPos + frameSize < outLength) {
            // Overlap-add the windowed analysis frame at the synthesis position.
            for (i in 0 until frameSize) {
                val w = window[i]
                out[synthesisPos + i] += input[analysisStart + i] * w
                norm[synthesisPos + i] += w
            }

            synthesisPos += synthesisHop
            nominal += analysisHop

            // The samples that should naturally follow what we just wrote: the
            // tail of the current frame advanced by one synthesis hop.
            val naturalStart = analysisStart + synthesisHop
            if (naturalStart + overlap >= input.size) break

            // Search around the nominal next analysis position for the offset
            // whose head best correlates with that natural continuation.
            val center = nominal.roundToInt()
            analysisStart = bestMatchOffset(input, naturalStart, center)
        }

        // Normalize where windows overlapped to keep unity gain at the edges.
        for (i in out.indices) {
            if (norm[i] > 1e-6f) out[i] /= norm[i]
        }
        // Trim the padding tail to the expected stretched length.
        val expected = (input.size * stretch).roundToInt()
        return if (expected < out.size) out.copyOf(expected) else out
    }

    /**
     * Finds, within ±[seekWindow] of [center], the input offset whose
     * `overlap`-length head best cross-correlates with the `overlap`-length
     * segment starting at [naturalStart]. Returns a bounds-safe offset.
     */
    private fun bestMatchOffset(
        input: FloatArray,
        naturalStart: Int,
        center: Int,
    ): Int {
        val low = (center - seekWindow).coerceAtLeast(0)
        val high = (center + seekWindow).coerceAtMost(input.size - frameSize - 1)
        if (high <= low) return low.coerceIn(0, input.size - frameSize - 1)

        var bestOffset = low
        var bestCorr = Double.NEGATIVE_INFINITY
        for (offset in low..high) {
            var corr = 0.0
            for (i in 0 until overlap) {
                corr += input[naturalStart + i].toDouble() * input[offset + i]
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestOffset = offset
            }
        }
        return bestOffset
    }

    /** Linear-interpolation resample of [input] to exactly [targetLength] samples. */
    private fun resampleLinear(
        input: FloatArray,
        targetLength: Int,
    ): FloatArray {
        if (targetLength <= 0 || input.isEmpty()) return FloatArray(0)
        if (input.size == 1) return FloatArray(targetLength) { input[0] }

        val out = FloatArray(targetLength)
        val step = (input.size - 1).toDouble() / (targetLength - 1).coerceAtLeast(1)
        for (i in 0 until targetLength) {
            val pos = i * step
            val idx = pos.toInt()
            val frac = pos - idx
            val a = input[idx]
            val b = if (idx + 1 < input.size) input[idx + 1] else a
            out[i] = (a + (b - a) * frac).toFloat()
        }
        return out
    }
}
