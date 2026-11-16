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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageFormatSnifferTest {
    @Test
    fun sniffsJpeg() {
        val bytes = byteArrayOf(0xFF.b, 0xD8.b, 0xFF.b, 0xE0.b, 0x00, 0x10)
        assertEquals(ImageFormat.Jpeg, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsPng() {
        val bytes = byteArrayOf(0x89.b, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)
        assertEquals(ImageFormat.Png, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsBmp() {
        val bytes = byteArrayOf('B'.b, 'M'.b, 0x36, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertEquals(ImageFormat.Bmp, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsTiffLittleEndian() {
        val bytes = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00)
        assertEquals(ImageFormat.Tiff, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsTiffBigEndian() {
        val bytes = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08)
        assertEquals(ImageFormat.Tiff, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsStaticGif() {
        val bytes = ascii("GIF89a") + byteArrayOf(0x10, 0x00, 0x10, 0x00)
        assertEquals(ImageFormat.Gif(animated = false), ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsGif87aStatic() {
        val bytes = ascii("GIF87a") + byteArrayOf(0x10, 0x00, 0x10, 0x00)
        assertEquals(ImageFormat.Gif(animated = false), ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsAnimatedGifViaNetscapeExtension() {
        val bytes =
            ascii("GIF89a") +
                ByteArray(50) { 0x00 } +
                ascii("NETSCAPE2.0") +
                ByteArray(20) { 0x00 }
        assertEquals(ImageFormat.Gif(animated = true), ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsStaticWebpRiffOnly() {
        val bytes =
            ascii("RIFF") + byteArrayOf(0x10, 0x00, 0x00, 0x00) + ascii("WEBP") +
                ascii("VP8L") + ByteArray(80) { 0x00 }
        assertEquals(ImageFormat.WebP(animated = false), ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsAnimatedWebpViaVp8xAnimFlag() {
        // RIFF + size + WEBP + VP8X chunk header + size + flags(0x02 = anim)
        val bytes =
            ascii("RIFF") + byteArrayOf(0x20, 0x00, 0x00, 0x00) + ascii("WEBP") +
                ascii("VP8X") + byteArrayOf(0x0A, 0x00, 0x00, 0x00) +
                byteArrayOf(0x02.b) + ByteArray(40) { 0x00 }
        assertEquals(ImageFormat.WebP(animated = true), ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsAnimatedWebpViaAnimChunk() {
        // RIFF + WEBP + ... + ANIM chunk somewhere in the prefix (no
        // VP8X animation flag — covers files that put ANIM directly).
        val bytes =
            ascii("RIFF") + byteArrayOf(0x40, 0x00, 0x00, 0x00) + ascii("WEBP") +
                ascii("VP8X") + byteArrayOf(0x0A, 0x00, 0x00, 0x00) +
                byteArrayOf(0x00.b) + ByteArray(9) { 0x00 } +
                ascii("ANIM") + ByteArray(30) { 0x00 }
        assertEquals(ImageFormat.WebP(animated = true), ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsAvifBrand() {
        val bytes =
            byteArrayOf(0x00, 0x00, 0x00, 0x20) + ascii("ftyp") + ascii("avif") +
                ByteArray(32) { 0x00 }
        assertEquals(ImageFormat.Avif, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsAvifSequenceBrand() {
        val bytes =
            byteArrayOf(0x00, 0x00, 0x00, 0x20) + ascii("ftyp") + ascii("avis") +
                ByteArray(32) { 0x00 }
        assertEquals(ImageFormat.Avif, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsHeicBrand() {
        val bytes =
            byteArrayOf(0x00, 0x00, 0x00, 0x20) + ascii("ftyp") + ascii("heic") +
                ByteArray(32) { 0x00 }
        assertEquals(ImageFormat.Heic, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsHeifMif1Brand() {
        val bytes =
            byteArrayOf(0x00, 0x00, 0x00, 0x20) + ascii("ftyp") + ascii("mif1") +
                ByteArray(32) { 0x00 }
        assertEquals(ImageFormat.Heic, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsSvgWithXmlProlog() {
        val bytes = ascii("""<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"></svg>""")
        assertEquals(ImageFormat.Svg, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun sniffsSvgWithoutProlog() {
        val bytes = ascii("""<svg xmlns="http://www.w3.org/2000/svg"></svg>""")
        assertEquals(ImageFormat.Svg, ImageFormatSniffer.sniff(bytes))
    }

    @Test
    fun returnsUnknownForRandomBytes() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        assertTrue(ImageFormatSniffer.sniff(bytes) is ImageFormat.Unknown)
    }

    @Test
    fun returnsUnknownForEmptyBytes() {
        assertTrue(ImageFormatSniffer.sniff(ByteArray(0)) is ImageFormat.Unknown)
    }

    @Test
    fun returnsUnknownForTooShort() {
        assertTrue(ImageFormatSniffer.sniff(byteArrayOf(0xFF.b)) is ImageFormat.Unknown)
    }

    @Test
    fun returnsUnknownForEmptyFile() {
        val empty = File.createTempFile("amethyst_empty_", ".bin")
        try {
            assertTrue(ImageFormatSniffer.sniff(empty) is ImageFormat.Unknown)
        } finally {
            empty.delete()
        }
    }

    @Test
    fun returnsUnknownForMissingFile() {
        val missing = File("/nonexistent/path/never/exists/file.bin")
        assertTrue(ImageFormatSniffer.sniff(missing) is ImageFormat.Unknown)
    }

    @Test
    fun sniffsJpegFromFile() {
        val jpeg = File.createTempFile("amethyst_sniff_jpeg_", ".jpg")
        try {
            val bytes = byteArrayOf(0xFF.b, 0xD8.b, 0xFF.b, 0xE0.b) + ByteArray(200) { 0x00 }
            jpeg.writeBytes(bytes)
            assertEquals(ImageFormat.Jpeg, ImageFormatSniffer.sniff(jpeg))
        } finally {
            jpeg.delete()
        }
    }

    // ----- tiny helpers -----

    private val Int.b: Byte get() = this.toByte()

    private val Char.b: Byte get() = this.code.toByte()

    private fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)
}
