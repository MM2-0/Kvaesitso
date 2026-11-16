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
package com.vitorpamplona.amethyst.commons.thumbhash

import kotlin.io.encoding.Base64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round

/**
 * ThumbHash encoder.
 *
 * Port of the reference implementation by Evan Wallace
 * (https://github.com/evanw/thumbhash, public domain), adapted to Kotlin.
 *
 * Input pixels are ARGB (0xAARRGGBB) to match [com.vitorpamplona.amethyst.commons.blurhash.PlatformImage.getPixels].
 */
object ThumbHashEncoder {
    /**
     * Encodes an ARGB image to a ThumbHash byte array.
     *
     * @param pixels ARGB pixels (0xAARRGGBB), row-by-row; must contain [width] * [height] entries.
     * @param width Image width in pixels; must be ≤ 100.
     * @param height Image height in pixels; must be ≤ 100.
     */
    fun encode(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): ByteArray {
        require(width in 1..100 && height in 1..100) { "ThumbHash input must be ≤100x100 (got ${width}x$height)" }
        require(pixels.size == width * height) { "pixels.size must equal width * height" }

        // Average color (premultiplied by alpha)
        var avgR = 0.0
        var avgG = 0.0
        var avgB = 0.0
        var avgA = 0.0
        for (i in 0 until width * height) {
            val argb = pixels[i]
            val alpha = ((argb ushr 24) and 0xff) / 255.0
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            avgR += alpha / 255.0 * r
            avgG += alpha / 255.0 * g
            avgB += alpha / 255.0 * b
            avgA += alpha
        }
        if (avgA > 0.0) {
            avgR /= avgA
            avgG /= avgA
            avgB /= avgA
        }

        val hasAlpha = avgA < width * height
        val lLimit = if (hasAlpha) 5 else 7
        val lx = max(1, round(lLimit * width.toDouble() / max(width, height)).toInt())
        val ly = max(1, round(lLimit * height.toDouble() / max(width, height)).toInt())

        val size = width * height
        val l = DoubleArray(size) // luminance
        val p = DoubleArray(size) // yellow - blue
        val q = DoubleArray(size) // red - green
        val a = DoubleArray(size) // alpha

        // Convert ARGB to LPQA, composited over the average color
        for (i in 0 until size) {
            val argb = pixels[i]
            val alpha = ((argb ushr 24) and 0xff) / 255.0
            val rPx = (argb ushr 16) and 0xff
            val gPx = (argb ushr 8) and 0xff
            val bPx = argb and 0xff
            val r = avgR * (1 - alpha) + alpha / 255.0 * rPx
            val g = avgG * (1 - alpha) + alpha / 255.0 * gPx
            val b = avgB * (1 - alpha) + alpha / 255.0 * bPx
            l[i] = (r + g + b) / 3.0
            p[i] = (r + g) / 2.0 - b
            q[i] = r - g
            a[i] = alpha
        }

        val lEnc = encodeChannel(l, width, height, max(3, lx), max(3, ly))
        val pEnc = encodeChannel(p, width, height, 3, 3)
        val qEnc = encodeChannel(q, width, height, 3, 3)
        val aEnc = if (hasAlpha) encodeChannel(a, width, height, 5, 5) else null

        val isLandscape = width > height
        val lCount = if (isLandscape) ly else lx
        val hasAlphaBit = if (hasAlpha) 1 else 0
        val isLandscapeBit = if (isLandscape) 1 else 0

        val header24 =
            round(63.0 * lEnc.dc).toInt() or
                (round(31.5 + 31.5 * pEnc.dc).toInt() shl 6) or
                (round(31.5 + 31.5 * qEnc.dc).toInt() shl 12) or
                (round(31.0 * lEnc.scale).toInt() shl 18) or
                (hasAlphaBit shl 23)
        val header16 =
            lCount or
                (round(63.0 * pEnc.scale).toInt() shl 3) or
                (round(63.0 * qEnc.scale).toInt() shl 9) or
                (isLandscapeBit shl 15)

        val acChannels = if (hasAlpha) listOf(lEnc.ac, pEnc.ac, qEnc.ac, aEnc!!.ac) else listOf(lEnc.ac, pEnc.ac, qEnc.ac)
        val totalAc = acChannels.sumOf { it.size }
        val acStart = if (hasAlpha) 6 else 5
        val hashSize = acStart + ((totalAc + 1) / 2)
        val hash = ByteArray(hashSize)
        hash[0] = (header24 and 0xff).toByte()
        hash[1] = ((header24 ushr 8) and 0xff).toByte()
        hash[2] = ((header24 ushr 16) and 0xff).toByte()
        hash[3] = (header16 and 0xff).toByte()
        hash[4] = ((header16 ushr 8) and 0xff).toByte()

        if (hasAlpha) {
            val aDcQ = round(15.0 * aEnc!!.dc).toInt() and 0xf
            val aScaleQ = round(15.0 * aEnc.scale).toInt() and 0xf
            hash[5] = (aDcQ or (aScaleQ shl 4)).toByte()
        }

        var acIndex = 0
        for (ac in acChannels) {
            for (f in ac) {
                val q4 = round(15.0 * f).toInt() and 0xf
                val byteIdx = acStart + (acIndex shr 1)
                val shift = (acIndex and 1) shl 2
                hash[byteIdx] = (hash[byteIdx].toInt() or (q4 shl shift)).toByte()
                acIndex++
            }
        }

        return hash
    }

    /**
     * Encodes an ARGB image to a base64 ThumbHash string (no padding).
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun encodeToBase64(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): String = Base64.encode(encode(pixels, width, height)).trimEnd('=')

    private data class ChannelEncoded(
        val dc: Double,
        val ac: DoubleArray,
        val scale: Double,
    )

    private fun encodeChannel(
        channel: DoubleArray,
        w: Int,
        h: Int,
        nx: Int,
        ny: Int,
    ): ChannelEncoded {
        var dc = 0.0
        var scale = 0.0
        val acList = ArrayList<Double>((nx * ny))
        val fx = DoubleArray(w)

        var cy = 0
        while (cy < ny) {
            var cx = 0
            while (cx * ny < nx * (ny - cy)) {
                var f = 0.0
                for (x in 0 until w) {
                    fx[x] = cos(PI / w * cx * (x + 0.5))
                }
                for (y in 0 until h) {
                    val fy = cos(PI / h * cy * (y + 0.5))
                    for (x in 0 until w) {
                        f += channel[x + y * w] * fx[x] * fy
                    }
                }
                f /= (w * h).toDouble()
                if (cx != 0 || cy != 0) {
                    acList.add(f)
                    if (abs(f) > scale) scale = abs(f)
                } else {
                    dc = f
                }
                cx++
            }
            cy++
        }

        val ac = DoubleArray(acList.size) { acList[it] }
        if (scale != 0.0) {
            for (i in ac.indices) {
                ac[i] = 0.5 + 0.5 / scale * ac[i]
            }
        }
        return ChannelEncoded(dc, ac, scale)
    }
}
