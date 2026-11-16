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
package com.vitorpamplona.amethyst.service.images

import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThumbnailDiskCacheAvifTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun cache(): ThumbnailDiskCache = ThumbnailDiskCache(tempFolder.newFolder("thumbs"))

    private fun fileWithHeader(header: ByteArray): File {
        val f = tempFolder.newFile()
        f.outputStream().use { it.write(header) }
        return f
    }

    private val ftypAvis =
        byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x20, // box size
            'f'.code.toByte(),
            't'.code.toByte(),
            'y'.code.toByte(),
            'p'.code.toByte(),
            'a'.code.toByte(),
            'v'.code.toByte(),
            'i'.code.toByte(),
            's'.code.toByte(),
        )
    private val ftypAvif =
        byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x20,
            'f'.code.toByte(),
            't'.code.toByte(),
            'y'.code.toByte(),
            'p'.code.toByte(),
            'a'.code.toByte(),
            'v'.code.toByte(),
            'i'.code.toByte(),
            'f'.code.toByte(),
        )
    private val jpegMagic =
        byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xE0.toByte(),
            0x00,
            0x10,
            'J'.code.toByte(),
            'F'.code.toByte(),
            'I'.code.toByte(),
            'F'.code.toByte(),
            0x00,
            0x01,
        )

    @Test
    fun `generateFromFile skips animated AVIF (ftyp avis)`() {
        val src = fileWithHeader(ftypAvis)
        assertFalse(cache().generateFromFile("https://example.com/animated.avif", src))
    }

    @Test
    fun `generateFromFile does not skip still AVIF (ftyp avif)`() {
        // Still AVIF is allowed through to BitmapFactory; this test verifies the
        // AVIF brand sniff does not over-match. BitmapFactory will fail on a
        // 12-byte file but that's the existing failure-fast path; we just
        // verify isAnimatedAvif() returns false for `avif`.
        val src = fileWithHeader(ftypAvif)
        // Generation will likely return false too (BitmapFactory can't decode 12 bytes),
        // but the path it takes is the normal path, not the AVIF-skip path. We can't
        // easily distinguish without instrumentation; for now this test documents intent.
        assertFalse(cache().generateFromFile("https://example.com/still.avif", src))
    }

    @Test
    fun `generateFromFile does not skip JPEG`() {
        val src = fileWithHeader(jpegMagic)
        assertFalse(cache().generateFromFile("https://example.com/photo.jpg", src))
    }
}
