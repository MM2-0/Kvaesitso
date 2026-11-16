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

import com.vitorpamplona.amethyst.commons.blurhash.SRGB.Companion.linearToSrgb
import com.vitorpamplona.amethyst.commons.blurhash.SRGB.Companion.srgbToLinear
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.withSign

object BlurHashDecoder {
    /** Returns width/height */
    fun aspectRatio(blurHash: String?): Float? {
        if (blurHash == null || blurHash.length < 6) {
            return null
        }
        val numCompEnc = Base83.decodeAt(blurHash, 0)
        val numCompX = (numCompEnc % 9) + 1
        val numCompY = (numCompEnc / 9) + 1
        if (blurHash.length != 4 + 2 * numCompX * numCompY) {
            return null
        }

        return numCompX.toFloat() / numCompY.toFloat()
    }

    fun computeColors(
        numCompX: Int,
        numCompY: Int,
        blurHash: String,
    ): Array<FloatArray> {
        val maxAc = (Base83.decodeAt(blurHash, 1) + 1) / 166f
        return Array(numCompX * numCompY) { i ->
            if (i == 0) {
                decodeDc(Base83.decode(blurHash, 2, 6))
            } else {
                decodeAc(Base83.decodeFixed2(blurHash, 4 + i * 2), maxAc)
            }
        }
    }

    fun computeNumComponets(blurHash: String): Pair<Int, Int> {
        val numCompEnc = Base83.decodeAt(blurHash, 0)
        val numCompX = (numCompEnc % 9) + 1
        val numCompY = (numCompEnc / 9) + 1
        return Pair(numCompX, numCompY)
    }

    /**
     * Decode a blur hash into a new PlatformImage.
     *
     * @param useCache use in memory cache for the calculated math, reused by images with same size.
     *   if the cache does not exist yet it will be created and populated with new calculations. By
     *   default it is true.
     */
    fun decodeKeepAspectRatio(
        blurHash: String?,
        width: Int,
        useCache: Boolean = true,
    ): PlatformImage? {
        if (blurHash == null || blurHash.length < 6) {
            return null
        }
        val (numCompX, numCompY) = computeNumComponets(blurHash)
        if (blurHash.length != 4 + 2 * numCompX * numCompY) {
            return null
        }
        val height = (width * (1 / (numCompX.toFloat() / numCompY.toFloat()))).roundToInt()
        val colors = computeColors(numCompX, numCompY, blurHash)
        val imageArray = composeImageArray(width, height, numCompX, numCompY, colors, useCache)
        return PlatformImage.create(imageArray, width, height)
    }

    private fun decodeDc(colorEnc: Int): FloatArray {
        val r = colorEnc shr 16
        val g = (colorEnc shr 8) and 255
        val b = colorEnc and 255
        return floatArrayOf(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
    }

    private fun decodeAc(
        value: Int,
        maxAc: Float,
    ): FloatArray {
        val r = value / (19 * 19)
        val g = (value / 19) % 19
        val b = value % 19
        return floatArrayOf(
            signedPow2((r - 9) / 9.0f) * maxAc,
            signedPow2((g - 9) / 9.0f) * maxAc,
            signedPow2((b - 9) / 9.0f) * maxAc,
        )
    }

    private fun signedPow2(value: Float) = value.pow(2f).withSign(value)

    private fun composeImageArray(
        width: Int,
        height: Int,
        numCompX: Int,
        numCompY: Int,
        colors: Array<FloatArray>,
        useCache: Boolean,
    ): IntArray {
        // use an array for better performance when writing pixel colors
        val imageArray = IntArray(width * height)
        val cosinesX = CosineCache.getArrayForCosinesX(useCache, width, numCompX)
        val cosinesY = CosineCache.getArrayForCosinesY(useCache, height, numCompY)

        var r = 0.0f
        var g = 0.0f
        var b = 0.0f

        for (y in 0 until height) {
            for (x in 0 until width) {
                r = 0.0f
                g = 0.0f
                b = 0.0f
                for (j in 0 until numCompY) {
                    for (i in 0 until numCompX) {
                        val cosY = cosinesY[j + numCompY * y]
                        val cosX = cosinesX[i + numCompX * x]
                        val basis = (cosX * cosY).toFloat()
                        val color = colors[j * numCompX + i]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }

                imageArray[x + width * y] = rgb(linearToSrgb(r), linearToSrgb(g), linearToSrgb(b))
            }
        }

        return imageArray
    }

    fun rgb(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = -0x1000000 or (red shl 16) or (green shl 8) or blue
}
