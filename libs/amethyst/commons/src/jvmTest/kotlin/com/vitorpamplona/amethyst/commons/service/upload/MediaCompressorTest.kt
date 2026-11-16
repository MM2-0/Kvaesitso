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

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaCompressorTest {
    @Test
    fun stripExifReturnsSameFileForPng() {
        val file = File.createTempFile("test_", ".png")
        file.deleteOnExit()
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "png", file)

        val result = MediaCompressor.stripExif(file)

        // Should return the same file object since it's not JPEG
        assertEquals(file, result)
        file.delete()
    }

    @Test
    fun stripExifReturnsSameFileForTextFile() {
        val file = File.createTempFile("test_", ".txt")
        file.deleteOnExit()
        file.writeText("not a jpeg")

        val result = MediaCompressor.stripExif(file)

        assertEquals(file, result)
        file.delete()
    }

    @Test
    fun stripExifReturnsSameFileForMp4() {
        val file = File.createTempFile("test_", ".mp4")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf(0, 0, 0))

        val result = MediaCompressor.stripExif(file)

        assertEquals(file, result)
        file.delete()
    }

    @Test
    fun stripExifHandlesJpegWithoutExif() {
        // Create a minimal JPEG without EXIF
        val file = createMinimalJpeg()
        try {
            val result = MediaCompressor.stripExif(file)
            // Should return the same file since there's no EXIF to strip
            assertEquals(file, result)
        } finally {
            file.delete()
        }
    }

    @Test
    fun stripExifProcessesJpegFile() {
        // Create a JPEG (may or may not have metadata depending on ImageIO)
        val file = File.createTempFile("test_", ".jpg")
        file.deleteOnExit()
        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "jpg", file)

        val result = MediaCompressor.stripExif(file)

        // Result should be a valid file regardless
        assertTrue(result.exists())
        assertTrue(result.length() > 0)

        // Clean up temp file if different from original
        if (result != file) {
            result.delete()
        }
        file.delete()
    }

    @Test
    fun stripExifHandlesUppercaseJpeg() {
        val file = File.createTempFile("test_", ".JPEG")
        file.deleteOnExit()
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "jpg", file)

        val result = MediaCompressor.stripExif(file)

        assertTrue(result.exists())
        if (result != file) result.delete()
        file.delete()
    }

    private fun createMinimalJpeg(): File {
        val file = File.createTempFile("minimal_", ".jpg")
        file.deleteOnExit()
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(img, "jpg", file)
        return file
    }
}
