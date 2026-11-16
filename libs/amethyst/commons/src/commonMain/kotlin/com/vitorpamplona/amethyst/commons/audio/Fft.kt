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
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal in-place iterative radix-2 Cooley–Tukey FFT (pure Kotlin, no JNI).
 * Adapted from Nayuki's public-domain "Free small FFT". Only power-of-2 sizes
 * are supported, which is all an audio visualiser needs (256/512/1024).
 */
object Fft {
    /** In-place forward transform. [re]/[im] must be the same power-of-2 length. */
    fun transform(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        val n = re.size
        require(n != 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2, was $n" }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
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

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang)
            val wLenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val bIm = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[i + k + half] = aRe - bRe
                    im[i + k + half] = aIm - bIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Forward-transforms real [signal] and returns magnitudes for bins 0..N/2 (DC..Nyquist).
     * Reference/allocating variant — production uses the *Into / in-place form on the audio thread.
     */
    fun magnitudes(signal: FloatArray): FloatArray {
        val out = FloatArray(signal.size / 2 + 1)
        magnitudesInto(signal, DoubleArray(signal.size), DoubleArray(signal.size), out)
        return out
    }

    /**
     * Like [magnitudes] but writes into caller-owned buffers (no allocation). [re] and [im] must be
     * the same length as [signal] (a power of 2); [out] must be `signal.size / 2 + 1`. Used on the
     * audio thread to avoid per-frame garbage.
     */
    fun magnitudesInto(
        signal: FloatArray,
        re: DoubleArray,
        im: DoubleArray,
        out: FloatArray,
    ) {
        val n = signal.size
        require(n != 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2, was $n" }
        require(re.size == n && im.size == n) { "re/im buffers must be size $n" }
        require(out.size == n / 2 + 1) { "out buffer must be size ${n / 2 + 1}" }
        for (i in 0 until n) {
            re[i] = signal[i].toDouble()
            im[i] = 0.0
        }
        transform(re, im)
        for (i in out.indices) out[i] = sqrt(re[i] * re[i] + im[i] * im[i]).toFloat()
    }
}
