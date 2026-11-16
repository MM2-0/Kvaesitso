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
import kotlin.math.cos

object AudioWindow {
    /** Hann window of length [n]. */
    fun hann(n: Int): FloatArray {
        if (n <= 1) return FloatArray(n) { 1f }
        return FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    }

    /**
     * Converts 16-bit PCM [samples] to normalized floats in [-1, 1], multiplies by
     * [window], and pads/truncates to `window.size`.
     */
    fun shortsToWindowed(
        samples: ShortArray,
        window: FloatArray,
    ): FloatArray =
        FloatArray(window.size) { i ->
            if (i < samples.size) (samples[i] / 32768f) * window[i] else 0f
        }

    /** Like [shortsToWindowed] but writes into [out] (size must equal [window].size); no allocation. */
    fun shortsToWindowedInto(
        samples: ShortArray,
        window: FloatArray,
        out: FloatArray,
    ) {
        require(out.size == window.size) { "out buffer must match window size" }
        for (i in window.indices) {
            out[i] = if (i < samples.size) (samples[i] / 32768f) * window[i] else 0f
        }
    }
}
