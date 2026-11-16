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

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File

/**
 * The EXIF orientation tag: cameras store the pixels as the sensor read them and record the turn a
 * viewer must apply. Every viewer (Skia, browsers, Android) honours it, so a re-encode that drops
 * the EXIF must apply the turn to the pixels, and a metadata strip must keep the tag.
 */
object ExifOrientation {
    const val NORMAL = 1

    /** The JPEG's orientation, 1 to 8, or [NORMAL] when it has none or is not a JPEG. */
    fun read(file: File): Int = runCatching { file.inputStream().use { read(it.readNBytes(SCAN_LIMIT)) } }.getOrDefault(NORMAL)

    /**
     * [read] for a file already in memory.
     *
     * Walks the segments itself instead of asking Commons Imaging: a file edited by more than one
     * tool can carry two Exif blocks, and Commons Imaging reports the tags of a later one, so a
     * phone photo re-saved by an editor came out sideways. Viewers take the tag from the first
     * block that has it, and so does this.
     */
    fun read(bytes: ByteArray): Int = runCatching { firstOrientation(bytes) }.getOrNull()?.takeIf { it in 1..8 } ?: NORMAL

    /** Metadata segments precede the scan, and one is at most 64 KiB. */
    private const val SCAN_LIMIT = 1 shl 20

    private fun firstOrientation(b: ByteArray): Int? =
        JpegSegments.leadingSegments(b).firstNotNullOfOrNull { segment ->
            if (JpegSegments.isExif(b, segment)) orientationInTiff(b, JpegSegments.tiffStart(segment), segment.end) else null
        }

    /** The Orientation tag of IFD0 in the TIFF structure at [start] until [end]. */
    private fun orientationInTiff(
        b: ByteArray,
        start: Int,
        end: Int,
    ): Int? {
        if (end - start < 8) return null
        val littleEndian =
            when {
                b[start] == 'I'.code.toByte() && b[start + 1] == 'I'.code.toByte() -> true
                b[start] == 'M'.code.toByte() && b[start + 1] == 'M'.code.toByte() -> false
                else -> return null
            }

        fun u16(at: Int): Int {
            val a = b[at].toInt() and 0xFF
            val c = b[at + 1].toInt() and 0xFF
            return if (littleEndian) a or (c shl 8) else (a shl 8) or c
        }

        fun u32(at: Int): Long = if (littleEndian) u16(at).toLong() or (u16(at + 2).toLong() shl 16) else (u16(at).toLong() shl 16) or u16(at + 2).toLong()

        if (u16(start + 2) != 42) return null
        val ifdOffset = u32(start + 4)
        if (ifdOffset > end - start - 2) return null
        val ifd = start + ifdOffset.toInt()

        val entries = u16(ifd)
        for (k in 0 until entries) {
            val entry = ifd + 2 + 12 * k
            if (entry + 12 > end) return null
            if (u16(entry) == ORIENTATION_TAG) {
                // A SHORT holding one value, stored in the entry itself.
                return if (u16(entry + 2) == TYPE_SHORT && u32(entry + 4) >= 1) u16(entry + 8) else null
            }
        }
        return null
    }

    private const val ORIENTATION_TAG = 0x0112
    private const val TYPE_SHORT = 3

    /** Orientations 5 to 8 turn the picture a quarter: displayed width is the stored height. */
    fun swapsAxes(orientation: Int): Boolean = orientation in 5..8

    /** [image] as it displays under [orientation]. */
    fun apply(
        image: BufferedImage,
        orientation: Int,
    ): BufferedImage {
        val w = image.width.toDouble()
        val h = image.height.toDouble()
        // Maps a stored pixel to its displayed place: x' = m00 x + m01 y + m02, y' = m10 x + m11 y + m12.
        val transform =
            when (orientation) {
                2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
                3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
                4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
                5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
                6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
                7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
                8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
                else -> return image
            }
        val swap = swapsAxes(orientation)
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val out = BufferedImage(if (swap) image.height else image.width, if (swap) image.width else image.height, type)
        val g = out.createGraphics()
        try {
            g.drawImage(image, transform, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
