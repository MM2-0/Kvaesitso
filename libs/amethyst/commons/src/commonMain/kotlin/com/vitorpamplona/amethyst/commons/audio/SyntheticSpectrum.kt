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

import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Deterministic decorative spectrum for previews and demos (no real audio). */
object SyntheticSpectrum {
    fun frame(
        timeSec: Float,
        binCount: Int,
    ): Spectrum {
        val t = timeSec
        val bins =
            FloatArray(binCount) { i ->
                val f = i / binCount.toFloat()
                val beat = (0.5 + 0.5 * sin(t * 2.0 * PI * 1.9)).pow(6.0)
                val bass = exp((-f * 9.0)) * (0.55 + 0.9 * beat)
                val mid = exp(-((f - 0.35) * 4.0).pow(2.0)) * (0.35 + 0.25 * sin(t * 5.0 + i))
                val treble = exp(-((f - 0.8) * 3.5).pow(2.0)) * max(0.0, sin(t * 14.0 + i * 2.3)) * 0.4
                val v = bass + mid + treble + 0.04 * sin(t * 3.0 + i * 0.7)
                v.coerceIn(0.0, 1.0).toFloat()
            }
        return Spectrum(bins)
    }

    /**
     * Compose-friendly stream that emits one [frame] per display frame.
     *
     * Must be collected inside a Compose frame-clock scope (e.g. a `LaunchedEffect`); it uses
     * `withFrameMillis`, which throws if collected from a plain coroutine with no frame clock.
     */
    fun flow(binCount: Int = 48): Flow<Spectrum> =
        flow {
            var startMs = -1L
            while (true) {
                val ms = withFrameMillis { it }
                if (startMs < 0) startMs = ms
                emit(frame((ms - startMs) / 1000f, binCount))
            }
        }
}
