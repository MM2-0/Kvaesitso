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

import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round

/**
 * ThumbHash decoder.
 *
 * Port of the reference implementation by Evan Wallace
 * (https://github.com/evanw/thumbhash, public domain), with performance
 * optimisations for the decode hot path:
 *
 *  - cosine tables for the inverse DCT are precomputed once per decode and
 *    cached across decodes keyed by `(size, componentCount)`; the reference
 *    JS impl recomputes them for every single output pixel.
 *  - AC coefficients are unpacked into fixed-size `DoubleArray`s, avoiding
 *    `ArrayList<Double>` boxing and the final array copy.
 *  - The LPQA → sRGB conversion uses an inline branch clamp instead of
 *    `min/max/round/coerceIn` chains.
 */
object ThumbHashDecoder {
    // Cosine tables are small and decoded sizes repeat heavily in practice
    // (every Coil request at a given target width shares the same table).
    // Keep an unbounded map — there are at most a few dozen distinct
    // (size, components) pairs across the entire app lifetime, each table is
    // a few KB, so the memory ceiling is tiny.
    private val cosineCache = HashMap<Long, DoubleArray>()
    private val cosineCacheLock = KmpLock()

    /**
     * Clear the cosine table cache. Tables are tiny but callers under memory
     * pressure can release them; they will be recomputed on demand.
     */
    fun clearCache() {
        cosineCacheLock.withLock { cosineCache.clear() }
    }

    private fun cosTable(
        size: Int,
        components: Int,
    ): DoubleArray {
        val key = (size.toLong() shl 32) or components.toLong()
        val cached = cosineCacheLock.withLock { cosineCache[key] }
        if (cached != null) return cached
        val table = DoubleArray(size * components)
        val piOverSize = PI / size
        for (i in 0 until size) {
            val phase = piOverSize * (i + 0.5)
            val rowOffset = i * components
            for (c in 0 until components) {
                table[rowOffset + c] = cos(phase * c)
            }
        }
        return cosineCacheLock.withLock { cosineCache.getOrPut(key) { table } }
    }

    /**
     * Returns width/height. Returns null if the hash is malformed.
     */
    fun aspectRatio(hash: ByteArray): Float? {
        if (hash.size < 5) return null
        val header = hash[3].toInt() and 0xff
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val isLandscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else (header and 7)
        val ly = if (isLandscape) (header and 7) else (if (hasAlpha) 5 else 7)
        if (lx == 0 || ly == 0) return null
        return lx.toFloat() / ly.toFloat()
    }

    /**
     * Returns width/height. Returns null if the string is malformed.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun aspectRatio(base64Hash: String?): Float? {
        if (base64Hash.isNullOrBlank()) return null
        return try {
            aspectRatio(Base64.decode(padBase64(base64Hash)))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    data class RGBAImage(
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RGBAImage) return false
            return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + pixels.contentHashCode()
            return result
        }
    }

    /**
     * Decode a ThumbHash byte array into ARGB pixels.
     * Returns null if the hash is malformed.
     */
    fun decode(hash: ByteArray): RGBAImage? {
        if (hash.size < 5) return null

        val b0 = hash[0].toInt() and 0xff
        val b1 = hash[1].toInt() and 0xff
        val b2 = hash[2].toInt() and 0xff
        val b3 = hash[3].toInt() and 0xff
        val b4 = hash[4].toInt() and 0xff
        val header24 = b0 or (b1 shl 8) or (b2 shl 16)
        val header16 = b3 or (b4 shl 8)

        val lDc = (header24 and 63) / 63.0
        val pDc = ((header24 shr 6) and 63) / 31.5 - 1.0
        val qDc = ((header24 shr 12) and 63) / 31.5 - 1.0
        val lScale = ((header24 shr 18) and 31) / 31.0
        val hasAlpha = (header24 shr 23) and 1 == 1
        val pScale = ((header16 shr 3) and 63) / 63.0
        val qScale = ((header16 shr 9) and 63) / 63.0
        val isLandscape = (header16 shr 15) and 1 == 1
        val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else (header16 and 7))
        val ly = max(3, if (isLandscape) (header16 and 7) else (if (hasAlpha) 5 else 7))

        val aDc: Double
        val aScale: Double
        if (hasAlpha) {
            if (hash.size < 6) return null
            aDc = (hash[5].toInt() and 15) / 15.0
            aScale = ((hash[5].toInt() shr 4) and 15) / 15.0
        } else {
            aDc = 1.0
            aScale = 0.0
        }

        // Pre-size and unpack AC coefficients
        val lAcCount = countAc(lx, ly)
        val pqAcCount = countAc(3, 3)
        val aAcCount = if (hasAlpha) countAc(5, 5) else 0
        val totalAc = lAcCount + pqAcCount * 2 + aAcCount
        val acStart = if (hasAlpha) 6 else 5
        val acBytesAvailable = hash.size - acStart
        // 2 coefficients per byte
        if (acBytesAvailable * 2 < totalAc) return null

        val lAc = DoubleArray(lAcCount)
        val pAc = DoubleArray(pqAcCount)
        val qAc = DoubleArray(pqAcCount)
        val aAc = if (hasAlpha) DoubleArray(aAcCount) else EMPTY_DOUBLE

        var acIndex = 0
        acIndex = readAcInto(hash, acStart, acIndex, lx, ly, lScale, lAc)
        acIndex = readAcInto(hash, acStart, acIndex, 3, 3, pScale * 1.25, pAc)
        acIndex = readAcInto(hash, acStart, acIndex, 3, 3, qScale * 1.25, qAc)
        if (hasAlpha) readAcInto(hash, acStart, acIndex, 5, 5, aScale, aAc)

        // Output size
        val ratio = lx.toDouble() / ly.toDouble()
        val w = round(if (ratio > 1) 32.0 else 32.0 * ratio).toInt()
        val h = round(if (ratio > 1) 32.0 / ratio else 32.0).toInt()
        val pixels = IntArray(w * h)

        // Precomputed cosine tables (shared across decodes with matching size/components)
        val cosXL = cosTable(w, lx)
        val cosYL = cosTable(h, ly)
        val cosXPQ = cosTable(w, 3)
        val cosYPQ = cosTable(h, 3)
        val cosXA: DoubleArray
        val cosYA: DoubleArray
        if (hasAlpha) {
            cosXA = cosTable(w, 5)
            cosYA = cosTable(h, 5)
        } else {
            cosXA = EMPTY_DOUBLE
            cosYA = EMPTY_DOUBLE
        }

        // Decode pixels using the inverse DCT
        var pixelIdx = 0
        for (y in 0 until h) {
            val cosYLBase = y * ly
            val cosYPQBase = y * 3
            val cosYABase = y * 5
            for (x in 0 until w) {
                val cosXLBase = x * lx
                val cosXPQBase = x * 3
                val cosXABase = x * 5

                var l = lDc
                var p = pDc
                var q = qDc
                var a = aDc

                // L channel — triangular iteration over (cx, cy)
                var j = 0
                var cy = 0
                while (cy < ly) {
                    val fyL2 = cosYL[cosYLBase + cy] * 2.0
                    var cx = if (cy != 0) 0 else 1
                    val cxLimit = cxLimitForL(lx, ly, cy)
                    while (cx < cxLimit) {
                        l += lAc[j] * cosXL[cosXLBase + cx] * fyL2
                        j++
                        cx++
                    }
                    cy++
                }

                // P and Q share the same 3x3 triangular iteration
                j = 0
                cy = 0
                while (cy < 3) {
                    val fyPQ2 = cosYPQ[cosYPQBase + cy] * 2.0
                    var cx = if (cy != 0) 0 else 1
                    val cxLimit = 3 - cy
                    while (cx < cxLimit) {
                        val f = cosXPQ[cosXPQBase + cx] * fyPQ2
                        p += pAc[j] * f
                        q += qAc[j] * f
                        j++
                        cx++
                    }
                    cy++
                }

                // Alpha channel
                if (hasAlpha) {
                    j = 0
                    cy = 0
                    while (cy < 5) {
                        val fyA2 = cosYA[cosYABase + cy] * 2.0
                        var cx = if (cy != 0) 0 else 1
                        val cxLimit = 5 - cy
                        while (cx < cxLimit) {
                            a += aAc[j] * cosXA[cosXABase + cx] * fyA2
                            j++
                            cx++
                        }
                        cy++
                    }
                }

                // LPQA → sRGB with inline clamp
                val bCh = l - 2.0 / 3.0 * p
                val rCh = (3.0 * l - bCh + q) * 0.5
                val gCh = rCh - q
                val rOut = clamp255(rCh)
                val gOut = clamp255(gCh)
                val bOut = clamp255(bCh)
                val aOut = if (hasAlpha) clamp255(a) else 255
                pixels[pixelIdx++] = (aOut shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }
        }

        return RGBAImage(w, h, pixels)
    }

    /**
     * Decode a base64-encoded ThumbHash string to ARGB pixels.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decode(base64Hash: String?): RGBAImage? {
        if (base64Hash.isNullOrBlank()) return null
        return try {
            decode(Base64.decode(padBase64(base64Hash)))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Decode a ThumbHash string into a [PlatformImage] whose aspect ratio
     * matches the original image. [targetWidth] is accepted for API symmetry
     * with [com.vitorpamplona.amethyst.commons.blurhash.BlurHashDecoder.decodeKeepAspectRatio]
     * but the intrinsic decode output size is used because ThumbHash's own
     * reconstruction is already aspect-correct at ~32px.
     */
    @Suppress("UNUSED_PARAMETER")
    fun decodeKeepAspectRatio(
        hash: String?,
        targetWidth: Int,
    ): PlatformImage? {
        val rgba = decode(hash) ?: return null
        return PlatformImage.create(rgba.pixels, rgba.width, rgba.height)
    }

    // --- internal helpers --- //

    private val EMPTY_DOUBLE = DoubleArray(0)

    /**
     * Count the number of AC coefficients carried by a channel of size nx × ny,
     * following the reference implementation's triangular traversal.
     */
    private fun countAc(
        nx: Int,
        ny: Int,
    ): Int {
        var count = 0
        var cy = 0
        while (cy < ny) {
            var cx = if (cy != 0) 0 else 1
            while (cx * ny < nx * (ny - cy)) {
                count++
                cx++
            }
            cy++
        }
        return count
    }

    /**
     * Row limit for the L channel's triangular traversal. For nx == ny this
     * collapses to `nx - cy`; keeping the explicit form avoids a mispredicted
     * branch in the inner loop for non-square L blocks.
     */
    private fun cxLimitForL(
        lx: Int,
        ly: Int,
        cy: Int,
    ): Int {
        // cx * ly < lx * (ly - cy)  ⇔  cx < (lx * (ly - cy)) / ly
        // Use integer ceil emulation: smallest cx that fails the condition.
        val numerator = lx * (ly - cy)
        // Largest cx satisfying cx * ly < numerator:
        //   cx <= ceil(numerator / ly) - 1 when numerator is exact,
        //   otherwise cx <= floor(numerator / ly).
        // So the limit (exclusive) is ceil(numerator / ly) when numerator % ly != 0,
        // else numerator / ly.
        return if (numerator % ly == 0) numerator / ly else numerator / ly + 1
    }

    private fun readAcInto(
        hash: ByteArray,
        acStart: Int,
        startIndex: Int,
        nx: Int,
        ny: Int,
        scale: Double,
        out: DoubleArray,
    ): Int {
        var acIndex = startIndex
        var outIdx = 0
        val hashLen = hash.size
        var cy = 0
        while (cy < ny) {
            var cx = if (cy != 0) 0 else 1
            while (cx * ny < nx * (ny - cy)) {
                val byteIdx = acStart + (acIndex shr 1)
                if (byteIdx >= hashLen) return acIndex
                val shift = (acIndex and 1) shl 2
                val q4 = (hash[byteIdx].toInt() ushr shift) and 15
                out[outIdx++] = (q4 / 7.5 - 1.0) * scale
                acIndex++
                cx++
            }
            cy++
        }
        return acIndex
    }

    /** Clamp v into 0..1 and scale to 0..255 with rounding, branchlessly on the hot path. */
    private fun clamp255(v: Double): Int {
        if (v <= 0.0) return 0
        if (v >= 1.0) return 255
        return (v * 255.0 + 0.5).toInt()
    }

    private fun padBase64(s: String): String {
        val remainder = s.length % 4
        return if (remainder == 0) s else s + "=".repeat(4 - remainder)
    }
}
