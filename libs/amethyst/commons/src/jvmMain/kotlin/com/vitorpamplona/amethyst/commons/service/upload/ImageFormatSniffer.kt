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

/**
 * Magic-byte sniffer for image upload payloads. Detection runs on a
 * small header prefix — full file decode is left to ImageReencoder.
 *
 * For containers that can be either still or animated (GIF, WebP) the
 * sniffer scans deeper into the bytes to set the `animated` flag, so
 * the orchestrator can short-circuit to byte-identical pass-through
 * for animations (ImageIO would otherwise encode only frame 0).
 */
object ImageFormatSniffer {
    /** Bytes from the start of the file used to sniff. */
    private const val PREFIX_BYTES = 8192

    fun sniff(file: File): ImageFormat {
        if (!file.exists() || file.length() == 0L) return ImageFormat.Unknown("application/octet-stream")
        val prefix =
            file.inputStream().use { input ->
                val buf = ByteArray(minOf(PREFIX_BYTES.toLong(), file.length()).toInt())
                var read = 0
                while (read < buf.size) {
                    val n = input.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read == buf.size) buf else buf.copyOf(read)
            }
        return sniff(prefix)
    }

    fun sniff(bytes: ByteArray): ImageFormat {
        if (bytes.size < 4) return ImageFormat.Unknown("application/octet-stream")

        // JPEG: FF D8 FF
        if (bytes.startsWith(0xFF, 0xD8, 0xFF)) return ImageFormat.Jpeg

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return ImageFormat.Png

        // GIF: "GIF87a" or "GIF89a". Animation detected via NETSCAPE2.0
        // application extension (animated GIFs almost always carry it).
        if (bytes.startsWith('G', 'I', 'F', '8') && bytes.size >= 6 &&
            (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte()
        ) {
            return ImageFormat.Gif(animated = bytes.containsAscii("NETSCAPE2.0"))
        }

        // BMP: "BM"
        if (bytes.startsWith('B', 'M')) return ImageFormat.Bmp

        // TIFF: II*\0 (little-endian) or MM\0* (big-endian)
        if (bytes.startsWith(0x49, 0x49, 0x2A, 0x00) || bytes.startsWith(0x4D, 0x4D, 0x00, 0x2A)) {
            return ImageFormat.Tiff
        }

        // RIFF container: WebP. "RIFF....WEBP". Animation requires VP8X
        // chunk (byte after 'VP8X' tag, then 4-byte chunk size, then a
        // single flags byte whose bit 1 = animation, OR the presence of
        // an 'ANIM' chunk somewhere in the prefix.
        if (bytes.size >= 12 && bytes.startsWith('R', 'I', 'F', 'F') &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            val hasAnim = bytes.containsAscii("ANIM") || webpHasAnimationFlag(bytes)
            return ImageFormat.WebP(animated = hasAnim)
        }

        // ISO Base Media File Format (ftyp box). Used by HEIC, AVIF.
        // Layout: 4-byte size, "ftyp", 4-byte major brand, 4-byte minor
        // version, then compatible brands.
        if (bytes.size >= 12 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) {
            val brand = String(bytes, 8, 4)
            return when (brand) {
                "avif", "avis" -> ImageFormat.Avif
                "heic", "heix", "heim", "heis", "hevc", "hevm", "hevs", "mif1", "msf1" -> ImageFormat.Heic
                else -> ImageFormat.Unknown("application/octet-stream")
            }
        }

        // SVG: optional XML prolog then "<svg". A lenient check that
        // tolerates a leading UTF-8 BOM and whitespace.
        val bomLen =
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            ) {
                3
            } else {
                0
            }
        val text =
            String(bytes, bomLen, minOf(bytes.size - bomLen, 1024), Charsets.US_ASCII)
                .trimStart(' ', '\n', '\r', '\t')
        if (text.startsWith("<?xml") || text.startsWith("<svg")) {
            // Either an XML doc that contains <svg, or starts with <svg directly.
            if (text.contains("<svg")) return ImageFormat.Svg
        }

        return ImageFormat.Unknown("application/octet-stream")
    }

    /**
     * For RIFF/WebP: walk to the VP8X chunk (if present at the start
     * of the container body) and read its single flags byte. Bit 1
     * (value 0x02) means animation per RFC 9649.
     */
    private fun webpHasAnimationFlag(bytes: ByteArray): Boolean {
        // RIFF header: 12 bytes ("RIFF" + size + "WEBP"). The first
        // chunk header is at offset 12: 4-byte FourCC + 4-byte size,
        // then payload.
        if (bytes.size < 21) return false
        if (bytes[12] != 'V'.code.toByte() || bytes[13] != 'P'.code.toByte() ||
            bytes[14] != '8'.code.toByte() || bytes[15] != 'X'.code.toByte()
        ) {
            return false
        }
        // Skip the 4-byte size at [16..19]; flags byte is at [20].
        return (bytes[20].toInt() and 0x02) != 0
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean {
        if (this.size < expected.size) return false
        for (i in expected.indices) {
            if (this[i] != expected[i].toByte()) return false
        }
        return true
    }

    private fun ByteArray.startsWith(vararg expected: Char): Boolean {
        if (this.size < expected.size) return false
        for (i in expected.indices) {
            if (this[i] != expected[i].code.toByte()) return false
        }
        return true
    }

    private fun ByteArray.containsAscii(needle: String): Boolean {
        if (needle.isEmpty() || needle.length > this.size) return false
        outer@ for (i in 0..this.size - needle.length) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j].code.toByte()) continue@outer
            }
            return true
        }
        return false
    }
}
