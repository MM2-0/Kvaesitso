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
import kotlin.random.Random

private const val SYNTHETIC_WAVEFORM_SAMPLES = 96
private const val TWO_PI = (PI * 2).toFloat()

/**
 * Builds a stable, decorative waveform from a [seed] (usually an event id). ExoPlayer drives
 * actual playback progress — these bars only exist so an audio player has the familiar
 * "audio silhouette" shape rather than a flat strip when the source carries no `waveform` tag.
 * Shared by every audio renderer whose spec omits waveforms (music tracks, podcast episodes, …).
 *
 * Every shape parameter (phase offset, carrier frequency, baseline, envelope skew, jitter
 * envelope) is drawn from the seeded RNG before the per-bar loop, so two different seeds
 * produce visibly different waveforms — earlier versions used a fixed envelope + carrier that
 * only varied by per-bar noise, which made every track look like the same slow-fade sine with
 * minor wiggle.
 */
fun syntheticWaveformFor(seed: String): WaveformData {
    // Fold the seed's 32-bit hash into a Long so two ids whose hashCode happens to collide
    // (rare for hex addresses but cheap to defend against) still differ via the bit shuffle.
    val rng = Random((seed.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor seed.length.toLong())

    // Shape parameters drawn once per seed — these are what makes seed A look different
    // from seed B at a glance.
    val phaseOffset = rng.nextFloat() * TWO_PI
    val carrierCycles = 2.5f + rng.nextFloat() * 5.5f // 2.5–8 full cycles across the strip
    val carrierWeight = 0.18f + rng.nextFloat() * 0.18f // 0.18–0.36
    val baseline = 0.28f + rng.nextFloat() * 0.22f // 0.28–0.50
    val envelopeStrength = rng.nextFloat() * 0.45f // 0.0–0.45; some fade in/out, others don't
    val noiseStrength = 0.18f + rng.nextFloat() * 0.22f // 0.18–0.40

    val bars =
        List(SYNTHETIC_WAVEFORM_SAMPLES) { index ->
            val phase = index.toFloat() / SYNTHETIC_WAVEFORM_SAMPLES
            // Optional gentle fade so some taper at the ends, others stay even.
            val envelope = 1f - envelopeStrength * (1f - sin(phase * PI).toFloat())
            val carrier = sin(phase * TWO_PI * carrierCycles + phaseOffset) * carrierWeight
            val noise = (rng.nextFloat() - 0.5f) * 2f * noiseStrength
            ((baseline + carrier + noise) * envelope).coerceIn(0.05f, 1.0f)
        }
    return WaveformData(bars)
}
