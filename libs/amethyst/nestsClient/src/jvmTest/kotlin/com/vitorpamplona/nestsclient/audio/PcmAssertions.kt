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
package com.vitorpamplona.nestsclient.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Signal-domain assertions on decoded Float32 PCM, used by the
 * cross-stack interop tests. All assertions take the raw PCM array
 * + the sample rate; the FFT helper allocates internally so callers
 * don't need to size buffers.
 *
 * **What these catch.** A round-trip Opus encode/decode against an
 * out-of-spec wire frame won't always throw — an
 * `OpusHead`-prefixed first frame, for instance, decodes to
 * silence-ish noise rather than the expected tone. Asserting
 * specific signal properties (peak frequency, RMS, zero-crossing
 * rate) catches every variant of "the bytes round-tripped but the
 * audio is wrong" without false positives from frame-loss
 * smoothing.
 */
object PcmAssertions {
    /**
     * Sample count within ±[tolerance] (fractional) of the expected
     * duration × sample rate. Tolerance ≥ 0.05 covers Opus look-ahead
     * + WebCodecs warmup + container framing slack.
     */
    fun assertSampleCount(
        samples: FloatArray,
        expectedDurationSec: Double,
        sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
        tolerance: Double = 0.05,
    ) {
        val expected = expectedDurationSec * sampleRate
        val deviation = abs(samples.size - expected) / expected
        check(deviation <= tolerance) {
            "sample count ${samples.size} differs from expected $expected by ${"%.3f".format(deviation)} (> $tolerance)"
        }
    }

    /**
     * RMS amplitude of [samples] is in `[minRms, maxRms]`. Useful
     * to catch full-scale clipping (peak too high) and silence
     * (peak too low).
     */
    fun assertRms(
        samples: FloatArray,
        minRms: Float = 0.05f,
        maxRms: Float = 0.95f,
    ) {
        val rms = rms(samples)
        check(rms in minRms..maxRms) {
            "RMS ${"%.4f".format(rms)} not in [$minRms, $maxRms]"
        }
    }

    /**
     * Peak FFT frequency of [samples] is within ±[halfWindowHz]
     * of [expectedHz]. Uses a simple Hann-windowed radix-2 FFT
     * inline (~50 lines, no JTransforms / JCommons dep).
     */
    fun assertFftPeak(
        samples: FloatArray,
        expectedHz: Double,
        halfWindowHz: Double = 5.0,
        sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
    ) {
        val peak = peakFrequencyHz(samples, sampleRate)
        val deviation = abs(peak - expectedHz)
        check(deviation <= halfWindowHz) {
            "FFT peak ${"%.2f".format(peak)} Hz off expected $expectedHz Hz by ${"%.2f".format(deviation)} (> $halfWindowHz)"
        }
    }

    /**
     * Stereo / multi-channel variant of [assertFftPeak]. [interleaved]
     * is L/R/L/R/... (or N-channel interleaved); [expectedHzPerChannel]
     * has one entry per channel and each per-channel slice is asserted
     * independently. Used by the I4 stereo scenario where left = 440
     * and right = 660 — a regression that mixes channels (or sums
     * them into mono) trips the per-channel FFT.
     */
    fun assertFftPeakPerChannel(
        interleaved: FloatArray,
        expectedHzPerChannel: DoubleArray,
        halfWindowHz: Double = 5.0,
        sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
    ) {
        val channels = expectedHzPerChannel.size
        require(interleaved.size % channels == 0) {
            "interleaved size ${interleaved.size} not divisible by channels=$channels"
        }
        val perChannelLen = interleaved.size / channels
        for (ch in 0 until channels) {
            val slice = FloatArray(perChannelLen)
            for (i in 0 until perChannelLen) {
                slice[i] = interleaved[i * channels + ch]
            }
            try {
                assertFftPeak(slice, expectedHzPerChannel[ch], halfWindowHz, sampleRate)
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "channel $ch (expected ${expectedHzPerChannel[ch]} Hz): ${t.message}",
                    t,
                )
            }
        }
    }

    /**
     * Zero crossings per second within ±[tolerance] (fractional)
     * of [expectedPerSecond]. Catches Opus predictor warble that
     * preserves average power but distorts waveform shape — e.g.
     * an "OpusHead" first-frame regression decodes to noisy garbage
     * with a very different zero-crossing rate than a clean tone.
     */
    fun assertZeroCrossingRate(
        samples: FloatArray,
        expectedPerSecond: Double,
        tolerance: Double = 0.10,
        sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
    ) {
        if (samples.size < 2) error("need at least 2 samples for ZCR")
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0f) != (samples[i] >= 0f)) crossings++
        }
        val durationSec = samples.size.toDouble() / sampleRate
        val rate = crossings / durationSec
        val deviation = abs(rate - expectedPerSecond) / expectedPerSecond
        check(deviation <= tolerance) {
            "zero-crossing rate ${"%.1f".format(rate)}/s differs from $expectedPerSecond/s by " +
                "${"%.3f".format(deviation)} (> $tolerance)"
        }
    }

    /**
     * Find a contiguous silence window of at least [minDurSec]
     * (RMS below [threshold] over a 100-ms sliding window). Returns
     * `[startSec, endSec]` if found, null otherwise. Used by I3
     * (mute window) — speaker mutes for 1 s mid-3-s broadcast,
     * listener should observe a corresponding silence window.
     */
    fun findSilenceWindow(
        samples: FloatArray,
        minDurSec: Double,
        threshold: Float = 0.01f,
        sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
    ): ClosedRange<Double>? {
        val windowSamples = max(1, sampleRate / 10) // 100-ms window
        if (samples.size < windowSamples) return null
        var inSilence = false
        var silenceStart = 0
        for (start in 0..samples.size - windowSamples step windowSamples / 2) {
            val rms = rms(samples, start, windowSamples)
            if (rms < threshold) {
                if (!inSilence) {
                    inSilence = true
                    silenceStart = start
                }
            } else if (inSilence) {
                val durSec = (start - silenceStart).toDouble() / sampleRate
                if (durSec >= minDurSec) {
                    return (silenceStart.toDouble() / sampleRate)..(start.toDouble() / sampleRate)
                }
                inSilence = false
            }
        }
        if (inSilence) {
            val durSec = (samples.size - silenceStart).toDouble() / sampleRate
            if (durSec >= minDurSec) {
                return (silenceStart.toDouble() / sampleRate)..(samples.size.toDouble() / sampleRate)
            }
        }
        return null
    }

    // ---- internals ---------------------------------------------------------

    private fun rms(
        samples: FloatArray,
        from: Int = 0,
        len: Int = samples.size,
    ): Float {
        if (len == 0) return 0f
        var sum = 0.0
        for (i in from until from + len) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / len).toFloat()
    }

    /**
     * Find the bin with the largest magnitude in a Hann-windowed
     * radix-2 FFT of [samples] (truncated to the next power of 2 ≤ N),
     * returning the centre frequency of that bin in Hz. Plenty
     * accurate for tone detection at 5-Hz resolution given a
     * 10000-sample window @ 48 kHz.
     */
    private fun peakFrequencyHz(
        samples: FloatArray,
        sampleRate: Int,
    ): Double {
        if (samples.size < 16) error("need ≥ 16 samples for FFT")
        val n = largestPow2AtMost(samples.size)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) {
            // Hann window suppresses spectral leakage so the peak
            // bin reliably matches the input frequency.
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            re[i] = samples[i] * w
        }
        fftRadix2(re, im)
        // Only the first n/2 bins are unique (real input → mirror).
        var maxBin = 1
        var maxMag2 = -1.0
        for (k in 1 until n / 2) {
            val mag2 = re[k] * re[k] + im[k] * im[k]
            if (mag2 > maxMag2) {
                maxMag2 = mag2
                maxBin = k
            }
        }
        return maxBin.toDouble() * sampleRate / n
    }

    private fun largestPow2AtMost(n: Int): Int {
        var p = 1
        while (p shl 1 <= n) p = p shl 1
        return p
    }

    /**
     * In-place iterative radix-2 Cooley-Tukey FFT. [re] / [im] must
     * be the same length and a power of 2. Adapted from the standard
     * textbook recipe — kept inline so we don't pull a transform
     * library (jtransforms, commons-math) into nestsClient test
     * dependencies.
     */
    private fun fftRadix2(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        val n = re.size
        require(n > 0 && (n and (n - 1)) == 0) { "fft length must be power of 2; got $n" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n ushr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit ushr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }

        // Butterflies.
        var size = 2
        while (size <= n) {
            val half = size / 2
            val theta = -2.0 * PI / size
            val wReStep = cos(theta)
            val wImStep = sin(theta)
            var k = 0
            while (k < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (m in 0 until half) {
                    val tRe = wRe * re[k + m + half] - wIm * im[k + m + half]
                    val tIm = wRe * im[k + m + half] + wIm * re[k + m + half]
                    re[k + m + half] = re[k + m] - tRe
                    im[k + m + half] = im[k + m] - tIm
                    re[k + m] = re[k + m] + tRe
                    im[k + m] = im[k + m] + tIm
                    val nwRe = wRe * wReStep - wIm * wImStep
                    val nwIm = wRe * wImStep + wIm * wReStep
                    wRe = nwRe
                    wIm = nwIm
                }
                k += size
            }
            size = size shl 1
        }
    }
}
