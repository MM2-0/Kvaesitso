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
package com.vitorpamplona.amethyst.commons.drawing

/**
 * A looping GIF89a writer for drawings, in plain Kotlin.
 *
 * GIF because it is the one animated format every Nostr client plays inline,
 * with no player controls: Amethyst decodes it on Android and on desktop, and
 * so does every browser. Drawings suit its limits. Ink on paper needs far fewer
 * than 256 colours, and a replay changes only a small patch of the picture per
 * frame, so each frame after the first stores just the rectangle that changed,
 * with the unchanged pixels inside it see-through, which is what keeps the file
 * small.
 *
 * Feed it whole frames as ARGB pixels with [addFrame]; a frame identical to the
 * one before only lengthens that one's delay. [finish] returns the file.
 *
 * The palette is fixed up front from one image, normally the finished drawing,
 * since every colour a replay shows is already in its last frame.
 */
class AnimatedGifEncoder(
    private val width: Int,
    private val height: Int,
    private val palette: GifPalette,
) {
    private val out = ByteSink(width * height / 4 + 1024)
    private val lzw = GifLzw()
    private var previous: ByteArray? = null
    private var pending: PendingFrame? = null

    init {
        require(width in 1..0xFFFF && height in 1..0xFFFF) { "GIF dimensions out of range: ${width}x$height" }
        out.ascii("GIF89a")
        out.u16(width)
        out.u16(height)
        // Global colour table, 8 bits per channel, 256 entries.
        out.u8(0xF7)
        out.u8(0)
        out.u8(0)
        out.bytes(palette.rgb)
        // Loop forever.
        out.u8(0x21)
        out.u8(0xFF)
        out.u8(11)
        out.ascii("NETSCAPE2.0")
        out.u8(3)
        out.u8(1)
        out.u16(0)
        out.u8(0)
    }

    /** Adds a frame of [argb] pixels (row-major, [width] × [height]) shown for [delayCs] hundredths of a second. */
    fun addFrame(
        argb: IntArray,
        delayCs: Int,
    ) {
        require(argb.size == width * height) { "Frame has ${argb.size} pixels, expected ${width * height}" }
        val current = palette.indicesOf(argb, width)
        val before = previous

        if (before == null) {
            pending = PendingFrame(0, 0, width, height, current, delayCs)
            previous = current
            return
        }

        var x0 = width
        var y0 = height
        var x1 = -1
        var y1 = -1
        for (p in current.indices) {
            if (current[p] == before[p]) continue
            val x = p % width
            val y = p / width
            if (x < x0) x0 = x
            if (x > x1) x1 = x
            if (y < y0) y0 = y
            if (y > y1) y1 = y
        }

        if (x1 < 0) {
            pending?.let { it.delayCs += delayCs }
            return
        }

        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        val patch = ByteArray(w * h)
        for (row in 0 until h) {
            val src = (y0 + row) * width + x0
            for (col in 0 until w) {
                val q = src + col
                patch[row * w + col] = if (current[q] == before[q]) GifPalette.TRANSPARENT.toByte() else current[q]
            }
        }

        flushPending()
        pending = PendingFrame(x0, y0, w, h, patch, delayCs)
        previous = current
    }

    /** Writes the last frame and the trailer, and returns the whole file. */
    fun finish(): ByteArray {
        flushPending()
        out.u8(0x3B)
        return out.toByteArray()
    }

    private fun flushPending() {
        val frame = pending ?: return
        pending = null
        // Graphic control: leave the frame in place for the next (disposal 1); index 255 is see-through.
        out.u8(0x21)
        out.u8(0xF9)
        out.u8(4)
        out.u8(0x05)
        out.u16(frame.delayCs.coerceIn(0, 0xFFFF))
        out.u8(GifPalette.TRANSPARENT)
        out.u8(0)
        // Image descriptor, no local colour table.
        out.u8(0x2C)
        out.u16(frame.x)
        out.u16(frame.y)
        out.u16(frame.w)
        out.u16(frame.h)
        out.u8(0)
        out.u8(MIN_CODE_SIZE)
        val data = lzw.encode(frame.pixels)
        var i = 0
        while (i < data.size) {
            val n = minOf(255, data.size - i)
            out.u8(n)
            out.bytes(data, i, n)
            i += n
        }
        out.u8(0)
    }

    private class PendingFrame(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val pixels: ByteArray,
        var delayCs: Int,
    )

    companion object {
        internal const val MIN_CODE_SIZE = 8
    }
}

/**
 * GIF's variable-width LZW over 8-bit indices. Codes are keyed by
 * `prefix << 8 | next` in a flat table; the code width grows as the table fills
 * and a clear code resets it at 4096 entries. The table is allocated once and
 * reused for every frame: only the keys actually written are wiped.
 */
internal class GifLzw {
    // prefix (< 4096) << 8 | byte is 20 bits, so the key indexes the table directly.
    // Entries are stored +1 so that zero means empty.
    private val table = IntArray(MAX_CODES shl 8)
    private val written = IntArray(MAX_CODES)
    private var writtenCount = 0

    private fun wipe() {
        for (w in 0 until writtenCount) table[written[w]] = 0
        writtenCount = 0
    }

    fun encode(indices: ByteArray): ByteArray {
        wipe()
        val out = ByteSink(indices.size / 2 + 16)
        val minCodeSize = AnimatedGifEncoder.MIN_CODE_SIZE
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var next = eoi + 1
        var codeSize = minCodeSize + 1
        var bits = 0
        var bitCount = 0

        fun emit(code: Int) {
            bits = bits or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                out.u8(bits and 0xFF)
                bits = bits ushr 8
                bitCount -= 8
            }
        }

        emit(clear)
        if (indices.isNotEmpty()) {
            var prefix = indices[0].toInt() and 0xFF
            for (i in 1 until indices.size) {
                val k = indices[i].toInt() and 0xFF
                val key = (prefix shl 8) or k
                val found = table[key]
                if (found != 0) {
                    prefix = found - 1
                    continue
                }
                emit(prefix)
                if (next == MAX_CODES) {
                    emit(clear)
                    wipe()
                    next = eoi + 1
                    codeSize = minCodeSize + 1
                } else {
                    if (next >= (1 shl codeSize)) codeSize++
                    table[key] = next + 1
                    written[writtenCount++] = key
                    next++
                }
                prefix = k
            }
            emit(prefix)
        }
        emit(eoi)
        if (bitCount > 0) out.u8(bits and 0xFF)
        return out.toByteArray()
    }

    private companion object {
        const val MAX_CODES = 4096
    }
}

/**
 * Up to 255 colours plus a see-through slot at index [TRANSPARENT].
 *
 * Ink on paper rarely needs more than 255 colours, and then the palette is
 * simply every colour in the picture (bucketed to 5 bits per channel), so the
 * GIF shows the drawing exactly. A drawing over a photo has thousands; then the
 * palette is cut by median cut and pixels are spread between neighbouring
 * entries with an ordered (Bayer) dither. That dither depends only on a pixel's
 * position and colour, so a pixel that does not change between two frames maps
 * to the same index in both and the frame-to-frame patches stay small.
 *
 * Other colours map to their nearest entry, looked up once per bucket.
 */
class GifPalette private constructor(
    /** 256 RGB triples, as the GIF colour table stores them. */
    val rgb: ByteArray,
    val size: Int,
    /** Whether [indicesOf] dithers: only when the picture had more colours than fit. */
    val dithers: Boolean,
) {
    private val nearest = ShortArray(BUCKETS) { -1 }

    /** Palette indices for a row-major frame [width] pixels wide. */
    fun indicesOf(
        argb: IntArray,
        width: Int,
    ): ByteArray {
        val out = ByteArray(argb.size)
        if (!dithers) {
            for (p in argb.indices) out[p] = indexOf(argb[p]).toByte()
            return out
        }
        for (p in argb.indices) {
            val x = p % width
            val y = p / width
            val offset = BAYER[(y and 3) * 4 + (x and 3)]
            val pixel = argb[p]
            val r = (((pixel shr 16) and 0xFF) + offset).coerceIn(0, 255)
            val g = (((pixel shr 8) and 0xFF) + offset).coerceIn(0, 255)
            val b = ((pixel and 0xFF) + offset).coerceIn(0, 255)
            out[p] = indexOf((r shl 16) or (g shl 8) or b).toByte()
        }
        return out
    }

    fun indexOf(argb: Int): Int {
        val bucket = bucketOf(argb)
        val cached = nearest[bucket]
        if (cached >= 0) return cached.toInt()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (c in 0 until size) {
            val dr = (rgb[c * 3].toInt() and 0xFF) - r
            val dg = (rgb[c * 3 + 1].toInt() and 0xFF) - g
            val db = (rgb[c * 3 + 2].toInt() and 0xFF) - b
            // Green counts most and blue least, roughly as the eye weighs them.
            val d = 3 * dr * dr + 4 * dg * dg + 2 * db * db
            if (d < bestDistance) {
                bestDistance = d
                best = c
            }
        }
        nearest[bucket] = best.toShort()
        return best
    }

    /**
     * Counts colours across several pictures before choosing the palette. A
     * replay needs the colours of its middle frames too: ink that is erased or
     * painted over later never reaches the last frame, and without it those
     * frames would show it in whichever colour happens to be nearest.
     */
    class Builder {
        private val count = IntArray(BUCKETS)
        private val sumR = LongArray(BUCKETS)
        private val sumG = LongArray(BUCKETS)
        private val sumB = LongArray(BUCKETS)

        fun add(argb: IntArray): Builder {
            for (pixel in argb) {
                val bucket = bucketOf(pixel)
                count[bucket]++
                sumR[bucket] += ((pixel shr 16) and 0xFF).toLong()
                sumG[bucket] += ((pixel shr 8) and 0xFF).toLong()
                sumB[bucket] += (pixel and 0xFF).toLong()
            }
            return this
        }

        /** Exact when the colours fit in 255 entries, median cut and dithered when they do not. */
        fun build(): GifPalette {
            val used = (0 until BUCKETS).filter { count[it] > 0 }
            val groups = if (used.size <= MAX_COLORS) used.map { listOf(it) } else medianCut(used, count)

            val rgb = ByteArray(256 * 3)
            groups.forEachIndexed { i, group ->
                var n = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (bucket in group) {
                    n += count[bucket]
                    r += sumR[bucket]
                    g += sumG[bucket]
                    b += sumB[bucket]
                }
                rgb[i * 3] = (r / n).toInt().toByte()
                rgb[i * 3 + 1] = (g / n).toInt().toByte()
                rgb[i * 3 + 2] = (b / n).toInt().toByte()
            }
            return GifPalette(rgb, maxOf(1, groups.size), dithers = used.size > MAX_COLORS)
        }
    }

    companion object {
        const val TRANSPARENT = 255
        private const val MAX_COLORS = 255
        private const val BUCKETS = 1 shl 15

        /** A 4x4 Bayer matrix, centred on zero and scaled to about one 5-bit bucket. */
        private val BAYER =
            intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5).map { (it - 7.5f) * 0.6f }.map { it.toInt() }.toIntArray()

        private fun bucketOf(argb: Int) = (((argb shr 19) and 0x1F) shl 10) or (((argb shr 11) and 0x1F) shl 5) or ((argb shr 3) and 0x1F)

        private fun channel(
            bucket: Int,
            axis: Int,
        ) = (bucket shr (10 - 5 * axis)) and 0x1F

        /** The palette for the colours of [argb] alone: exact when they fit, median cut when they do not. */
        fun from(argb: IntArray): GifPalette = Builder().add(argb).build()

        /**
         * Splits the used buckets into [MAX_COLORS] groups: each time, the group
         * spanning the widest channel range is cut at the pixel-weighted median
         * of that channel.
         */
        private fun medianCut(
            used: List<Int>,
            count: IntArray,
        ): List<List<Int>> {
            val groups = mutableListOf(used)
            while (groups.size < MAX_COLORS) {
                var widest = -1
                var widestAxis = 0
                var widestRange = 0
                for ((index, group) in groups.withIndex()) {
                    if (group.size < 2) continue
                    for (axis in 0..2) {
                        var lo = 31
                        var hi = 0
                        for (bucket in group) {
                            val v = channel(bucket, axis)
                            if (v < lo) lo = v
                            if (v > hi) hi = v
                        }
                        if (hi - lo > widestRange) {
                            widestRange = hi - lo
                            widest = index
                            widestAxis = axis
                        }
                    }
                }
                if (widest < 0) break
                val sorted = groups[widest].sortedBy { channel(it, widestAxis) }
                val half = sorted.sumOf { count[it].toLong() } / 2
                var running = 0L
                var cut = 1
                for (i in sorted.indices) {
                    running += count[sorted[i]]
                    if (running >= half) {
                        cut = (i + 1).coerceIn(1, sorted.size - 1)
                        break
                    }
                }
                groups[widest] = sorted.subList(0, cut)
                groups.add(sorted.subList(cut, sorted.size))
            }
            return groups
        }
    }
}

/** A growable byte buffer with the little-endian writes GIF needs. */
internal class ByteSink(
    initialCapacity: Int,
) {
    private var buffer = ByteArray(maxOf(16, initialCapacity))
    private var length = 0

    private fun room(extra: Int) {
        if (length + extra <= buffer.size) return
        buffer = buffer.copyOf(maxOf(buffer.size * 2, length + extra))
    }

    fun u8(value: Int) {
        room(1)
        buffer[length++] = value.toByte()
    }

    fun u16(value: Int) {
        u8(value and 0xFF)
        u8((value shr 8) and 0xFF)
    }

    fun ascii(text: String) {
        for (c in text) u8(c.code)
    }

    fun bytes(
        data: ByteArray,
        offset: Int = 0,
        count: Int = data.size,
    ) {
        room(count)
        data.copyInto(buffer, length, offset, offset + count)
        length += count
    }

    fun toByteArray(): ByteArray = buffer.copyOf(length)
}
