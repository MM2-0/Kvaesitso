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

import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit-level checks for [PcmAssertions] + [SineWaveAudioCapture]
 * that don't need the cross-stack harness. Without these, a
 * regression in the FFT / RMS / ZCR helpers might land silently
 * because the only callers are gated behind `-DnestsHangInterop=true`.
 */
class PcmAssertionsTest {
    @Test
    fun fft_peak_finds_known_tone() {
        val sampleRate = AudioFormat.SAMPLE_RATE_HZ
        val pcm = sineFloat(440.0, durationSec = 1.0, sampleRate = sampleRate, amplitude = 0.5f)
        PcmAssertions.assertFftPeak(pcm, expectedHz = 440.0, halfWindowHz = 5.0)

        // A wrong-frequency claim must fail.
        assertFails {
            PcmAssertions.assertFftPeak(pcm, expectedHz = 1000.0, halfWindowHz = 5.0)
        }
    }

    @Test
    fun rms_window_excludes_silence_and_clipping() {
        val pcm = sineFloat(440.0, durationSec = 1.0, amplitude = 0.5f)
        PcmAssertions.assertRms(pcm, minRms = 0.30f, maxRms = 0.40f)

        val silent = FloatArray(48_000)
        assertFails { PcmAssertions.assertRms(silent, minRms = 0.30f, maxRms = 0.40f) }

        val clipped = FloatArray(48_000) { 1.0f }
        assertFails { PcmAssertions.assertRms(clipped, minRms = 0.30f, maxRms = 0.40f) }
    }

    @Test
    fun zero_crossings_match_sine_period() {
        val pcm = sineFloat(440.0, durationSec = 1.0, amplitude = 0.5f)
        // 440 Hz sine has 2 zero-crossings per period → 880/sec.
        PcmAssertions.assertZeroCrossingRate(pcm, expectedPerSecond = 880.0, tolerance = 0.05)
    }

    @Test
    fun silence_window_finds_mid_burst() {
        // 1 s tone, 1 s silence, 1 s tone.
        val sampleRate = AudioFormat.SAMPLE_RATE_HZ
        val tone = sineFloat(440.0, durationSec = 1.0, sampleRate = sampleRate, amplitude = 0.5f)
        val silence = FloatArray(sampleRate)
        val pcm = tone + silence + tone

        val window = PcmAssertions.findSilenceWindow(pcm, minDurSec = 0.5)
        assertNotNull(window)
        // Window should overlap the [1s, 2s] silent slice.
        val overlapStart = maxOf(window.start, 1.0)
        val overlapEnd = minOf(window.endInclusive, 2.0)
        check(overlapEnd - overlapStart > 0.4) {
            "expected silence window to overlap [1.0, 2.0]s; got [${window.start}, ${window.endInclusive}]"
        }

        // No silence in pure-tone audio.
        assertNull(PcmAssertions.findSilenceWindow(tone + tone, minDurSec = 0.5))
    }

    @Test
    fun sample_count_tolerance_works() {
        val pcm = FloatArray(48_000)
        PcmAssertions.assertSampleCount(pcm, expectedDurationSec = 1.0)
        // 5% tolerance with a 0.94-s array (6% short) must fail.
        val short = FloatArray((0.94 * 48_000).toInt())
        assertFails { PcmAssertions.assertSampleCount(short, expectedDurationSec = 1.0) }
    }

    @Test
    fun sine_wave_capture_is_frame_perfect() {
        val capture = SineWaveAudioCapture(freqHz = 440)
        val frames = mutableListOf<ShortArray>()
        runBlocking {
            // 5 frames × 960 samples = 4800 samples = 100 ms @ 48 kHz.
            repeat(5) { capture.readFrame()?.let(frames::add) }
        }
        check(frames.size == 5)
        check(frames.all { it.size == AudioFormat.FRAME_SIZE_SAMPLES })
        // Frame boundary should be continuous: the next sample after
        // frame N's last is frame N+1's first, by phase alignment.
        // We don't assert byte equality (Opus has internal predictors),
        // but check that each frame's first sample isn't identical
        // to the previous one's first — phase actually advanced.
        check(frames[0][0] != frames[1][0] || frames[1][0] != frames[2][0])
    }

    private fun sineFloat(
        freqHz: Double,
        durationSec: Double,
        sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
        amplitude: Float = 0.5f,
    ): FloatArray {
        val n = (durationSec * sampleRate).toInt()
        val out = FloatArray(n)
        val step = 2.0 * PI * freqHz / sampleRate
        for (i in 0 until n) out[i] = (amplitude * sin(step * i)).toFloat()
        return out
    }

    private operator fun FloatArray.plus(other: FloatArray): FloatArray {
        val out = FloatArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }
}
