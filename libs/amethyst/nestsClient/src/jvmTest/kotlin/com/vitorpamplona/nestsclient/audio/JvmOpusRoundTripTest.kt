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
import org.junit.Assume.assumeTrue
import kotlin.test.Test

/**
 * Sanity check that [JvmOpusEncoder] + [JvmOpusDecoder] round-trip a
 * sine wave: the test pumps 1 s of 440 Hz from
 * [SineWaveAudioCapture] through encode → decode and asserts the
 * decoded float-PCM still has its peak at 440 Hz.
 *
 * Catches: native-load failures (missing platform support), wrong
 * sample-rate / channel-count plumbing, encoder/decoder state-
 * leak bugs that distort the waveform.
 */
class JvmOpusRoundTripTest {
    @Test
    fun sine_440_round_trips_through_libopus() {
        val capture = SineWaveAudioCapture(freqHz = 440)
        // club.minnced:opus-java doesn't ship natives for darwin-aarch64 (Apple
        // Silicon). Skip rather than fail so dev machines without a usable
        // native still pass the pre-push hook; Linux x86_64 CI keeps coverage.
        val encoder: JvmOpusEncoder
        val decoder: JvmOpusDecoder
        try {
            encoder = JvmOpusEncoder()
            decoder = JvmOpusDecoder()
        } catch (e: IllegalStateException) {
            assumeTrue("Opus natives not available: ${e.message}", false)
            return
        }
        try {
            val decoded = mutableListOf<Float>()
            runBlocking {
                // 50 frames × 20 ms = 1.0 s at 48 kHz.
                repeat(50) {
                    val pcm = capture.readFrame() ?: return@runBlocking
                    val packet = encoder.encode(pcm)
                    val out = decoder.decode(packet)
                    for (s in out) decoded.add(s.toFloat() / Short.MAX_VALUE.toFloat())
                }
            }
            val floats = decoded.toFloatArray()
            // Opus has ~6.5 ms look-ahead → first frame is silence.
            // Drop the first 20 ms (one frame) to keep the FFT clean.
            val skip = AudioFormat.FRAME_SIZE_SAMPLES
            val analysed = floats.copyOfRange(skip, floats.size)
            PcmAssertions.assertSampleCount(analysed, expectedDurationSec = 0.98, tolerance = 0.05)
            PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
            PcmAssertions.assertZeroCrossingRate(
                analysed,
                expectedPerSecond = 880.0,
                tolerance = 0.05,
            )
        } finally {
            encoder.release()
            decoder.release()
        }
    }
}
