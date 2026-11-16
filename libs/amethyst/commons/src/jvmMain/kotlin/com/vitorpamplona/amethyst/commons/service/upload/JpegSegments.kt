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
package com.vitorpamplona.amethyst.commons.service.upload

import java.io.ByteArrayOutputStream

/** One marker segment of a JPEG header: [start] is its first 0xFF, [end] is one past its last byte. */
internal class JpegSegment(
    val marker: Int,
    val start: Int,
    val payload: Int,
    val end: Int,
)

/** Reads the marker segments that precede a JPEG's image data, with every length bounds-checked. */
internal object JpegSegments {
    private const val SOS = 0xDA
    private const val EOI = 0xD9
    private const val APP1 = 0xE1

    private val EXIF_HEADER = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)

    /** Where the image data starts and the segments before it, or null when [b] is not a well-formed JPEG header. */
    fun header(b: ByteArray): Pair<List<JpegSegment>, Int>? {
        val segments = mutableListOf<JpegSegment>()
        val scan = walk(b, segments)
        return if (scan >= 0) segments to scan else null
    }

    /** The whole segments at the start of [b], which may be a file prefix that ends mid-header. */
    fun leadingSegments(b: ByteArray): List<JpegSegment> = mutableListOf<JpegSegment>().also { walk(b, it) }

    /** Adds each segment to [into] and returns where the image data starts, or -1 where the header breaks off. */
    private fun walk(
        b: ByteArray,
        into: MutableList<JpegSegment>,
    ): Int {
        val n = b.size
        if (n < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) return -1

        var i = 2
        while (true) {
            if (i >= n || u8(b, i) != 0xFF) return -1
            val start = i
            // A marker may be padded with any number of 0xFF fill bytes.
            while (i < n && u8(b, i) == 0xFF) i++
            if (i >= n) return -1
            val marker = u8(b, i++)
            // Start of scan or end of image: no metadata after this.
            if (marker == SOS || marker == EOI) return start
            // Markers without a length.
            if (marker == 0x01 || marker in 0xD0..0xD7) continue

            if (i + 2 > n) return -1
            val length = (u8(b, i) shl 8) or u8(b, i + 1)
            if (length < 2 || i + length > n) return -1
            into += JpegSegment(marker, start, i + 2, i + length)
            i += length
        }
    }

    /** Whether [segment] is an Exif block: an APP1 whose payload starts with "Exif\0\0". */
    fun isExif(
        b: ByteArray,
        segment: JpegSegment,
    ): Boolean =
        segment.marker == APP1 &&
            segment.end - segment.payload > EXIF_HEADER.size &&
            EXIF_HEADER.indices.all { b[segment.payload + it] == EXIF_HEADER[it] }

    /** Where the TIFF structure of an Exif [segment] starts. */
    fun tiffStart(segment: JpegSegment): Int = segment.payload + EXIF_HEADER.size

    /** [b] without any of its Exif blocks, or null when its header is not well-formed. */
    fun withoutExif(b: ByteArray): ByteArray? {
        val (segments, scan) = header(b) ?: return null
        val out = ByteArrayOutputStream(b.size)
        out.write(b, 0, 2)
        var copied = 2
        segments.forEach { segment ->
            if (isExif(b, segment)) {
                out.write(b, copied, segment.start - copied)
                copied = segment.end
            }
        }
        out.write(b, copied, scan - copied)
        out.write(b, scan, b.size - scan)
        return out.toByteArray()
    }

    private fun u8(
        b: ByteArray,
        at: Int,
    ) = b[at].toInt() and 0xFF
}
