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
package com.vitorpamplona.amethyst.commons.blurhash

import com.vitorpamplona.amethyst.commons.blurhash.Base83.encode
import com.vitorpamplona.amethyst.commons.blurhash.SRGB.Companion.linearToSrgb
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.withSign

class BlurHashEncoder {
    fun signPow(
        value: Double,
        exp: Double,
    ): Double = abs(value).pow(exp).withSign(value)

    private fun encodeAC(
        value: DoubleArray,
        maximumValue: Double,
    ): Long {
        val quantR = floor(max(0.0, min(18.0, floor(signPow(value[0] / maximumValue, 0.5) * 9 + 9.5))))
        val quantG = floor(max(0.0, min(18.0, floor(signPow(value[1] / maximumValue, 0.5) * 9 + 9.5))))
        val quantB = floor(max(0.0, min(18.0, floor(signPow(value[2] / maximumValue, 0.5) * 9 + 9.5))))
        return (quantR * 19 * 19 + quantG * 19 + quantB).roundToLong()
    }

    private fun encodeDC(value: DoubleArray): Long {
        val r = linearToSrgb(value[0]).toLong()
        val g = linearToSrgb(value[1]).toLong()
        val b = linearToSrgb(value[2]).toLong()
        return (r shl 16) + (g shl 8) + b
    }

    fun max(
        values: Array<DoubleArray>,
        from: Int,
        endExclusive: Int,
    ): Double {
        var result = Double.NEGATIVE_INFINITY
        for (i in from until endExclusive) {
            for (j in values[i].indices) {
                val value = values[i][j]
                if (value > result) {
                    result = value
                }
            }
        }
        return result
    }

    fun encode(
        pixels: IntArray,
        width: Int,
        height: Int,
        componentX: Int,
        componentY: Int,
        useCache: Boolean = true,
    ): String {
        require(!(componentX < 1 || componentX > 9 || componentY < 1 || componentY > 9)) { "Blur hash must have between 1 and 9 components" }
        require(width * height == pixels.size) { "Width and height must match the pixels array" }

        val factors = Array(componentX * componentY) { DoubleArray(3) }

        val cosinesX = CosineCache.getArrayForCosinesX(useCache, width, componentX)
        val cosinesY = CosineCache.getArrayForCosinesY(useCache, height, componentY)

        val scale = 1.0 / (width * height)

        var r = 0.0
        var g = 0.0
        var b = 0.0

        for (j in 0 until componentY) {
            for (i in 0 until componentX) {
                val normalisation = (if (i == 0 && j == 0) 1 else 2).toDouble()

                r = 0.0
                g = 0.0
                b = 0.0
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        val basis = normalisation * cosinesY[j + componentY * y] * cosinesX[i + componentX * x]
                        val pixel = pixels[y * width + x]
                        r += basis * SRGB.srgbToLinear((pixel shr 16) and 0xff)
                        g += basis * SRGB.srgbToLinear((pixel shr 8) and 0xff)
                        b += basis * SRGB.srgbToLinear(pixel and 0xff)
                    }
                }

                val colors = factors[j * componentX + i]
                colors[0] = r * scale
                colors[1] = g * scale
                colors[2] = b * scale
            }
        }

        val hash = CharArray(1 + 1 + 4 + 2 * (factors.size - 1)) // size flag + max AC + DC + 2 * AC components
        val sizeFlag = (componentX - 1 + (componentY - 1) * 9).toLong()
        encode(sizeFlag, 1, hash, 0)

        val maximumValue: Double
        if (factors.size > 1) {
            val actualMaximumValue = max(factors, 1, factors.size)
            val quantisedMaximumValue = floor(max(0.0, min(82.0, floor(actualMaximumValue * 166 - 0.5))))
            maximumValue = (quantisedMaximumValue + 1) / 166
            encode(quantisedMaximumValue.roundToLong(), 1, hash, 1)
        } else {
            maximumValue = 1.0
            encode(0, 1, hash, 1)
        }

        val dc = factors[0]
        encode(encodeDC(dc), 4, hash, 2)

        for (i in 1 until factors.size) {
            encode(encodeAC(factors[i], maximumValue), 2, hash, 6 + 2 * (i - 1))
        }
        return hash.concatToString()
    }
}
