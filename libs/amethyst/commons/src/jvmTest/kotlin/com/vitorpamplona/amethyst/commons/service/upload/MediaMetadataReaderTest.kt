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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaMetadataReaderTest {
    // --- guessMimeType ---

    @Test
    fun guessMimeTypeForJpeg() {
        assertEquals("image/jpeg", MediaMetadataReader.guessMimeType(File("photo.jpg")))
        assertEquals("image/jpeg", MediaMetadataReader.guessMimeType(File("photo.jpeg")))
        assertEquals("image/jpeg", MediaMetadataReader.guessMimeType(File("photo.JPEG")))
    }

    @Test
    fun guessMimeTypeForPng() {
        assertEquals("image/png", MediaMetadataReader.guessMimeType(File("image.png")))
    }

    @Test
    fun guessMimeTypeForGif() {
        assertEquals("image/gif", MediaMetadataReader.guessMimeType(File("anim.gif")))
    }

    @Test
    fun guessMimeTypeForWebp() {
        assertEquals("image/webp", MediaMetadataReader.guessMimeType(File("image.webp")))
    }

    @Test
    fun guessMimeTypeForSvg() {
        assertEquals("image/svg+xml", MediaMetadataReader.guessMimeType(File("icon.svg")))
    }

    @Test
    fun guessMimeTypeForAvif() {
        assertEquals("image/avif", MediaMetadataReader.guessMimeType(File("photo.avif")))
    }

    @Test
    fun guessMimeTypeForVideoFormats() {
        assertEquals("video/mp4", MediaMetadataReader.guessMimeType(File("clip.mp4")))
        assertEquals("video/webm", MediaMetadataReader.guessMimeType(File("clip.webm")))
        assertEquals("video/quicktime", MediaMetadataReader.guessMimeType(File("clip.mov")))
    }

    @Test
    fun guessMimeTypeForAudioFormats() {
        assertEquals("audio/mpeg", MediaMetadataReader.guessMimeType(File("song.mp3")))
        assertEquals("audio/ogg", MediaMetadataReader.guessMimeType(File("track.ogg")))
        assertEquals("audio/wav", MediaMetadataReader.guessMimeType(File("sound.wav")))
        assertEquals("audio/flac", MediaMetadataReader.guessMimeType(File("lossless.flac")))
    }

    @Test
    fun guessMimeTypeForUnknownExtension() {
        assertEquals("application/octet-stream", MediaMetadataReader.guessMimeType(File("data.xyz")))
        assertEquals("application/octet-stream", MediaMetadataReader.guessMimeType(File("noext")))
    }

    // --- compute ---

    @Test
    fun computeForPngImage() {
        val file = createTempPng(width = 10, height = 5)
        try {
            val meta = MediaMetadataReader.compute(file)

            assertEquals("image/png", meta.mimeType)
            assertTrue(meta.size > 0)
            assertTrue(meta.sha256.length == 64) // hex-encoded SHA-256
            assertEquals(10, meta.width)
            assertEquals(5, meta.height)
            assertNotNull(meta.blurhash)
        } finally {
            file.delete()
        }
    }

    @Test
    fun computeForTextFile() {
        val file = File.createTempFile("test_", ".txt")
        file.deleteOnExit()
        file.writeText("hello world")
        try {
            val meta = MediaMetadataReader.compute(file)

            assertEquals("application/octet-stream", meta.mimeType)
            assertEquals(11L, meta.size)
            assertTrue(meta.sha256.isNotEmpty())
            assertNull(meta.width)
            assertNull(meta.height)
            assertNull(meta.blurhash)
        } finally {
            file.delete()
        }
    }

    @Test
    fun computeProducesConsistentHash() {
        val file = File.createTempFile("hash_", ".txt")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        try {
            val meta1 = MediaMetadataReader.compute(file)
            val meta2 = MediaMetadataReader.compute(file)
            assertEquals(meta1.sha256, meta2.sha256)
        } finally {
            file.delete()
        }
    }

    @Test
    fun computeForMp4GivesNoDimensions() {
        val file = File.createTempFile("video_", ".mp4")
        file.deleteOnExit()
        file.writeBytes(byteArrayOf(0, 0, 0))
        try {
            val meta = MediaMetadataReader.compute(file)
            assertEquals("video/mp4", meta.mimeType)
            assertNull(meta.width)
            assertNull(meta.height)
            assertNull(meta.blurhash)
        } finally {
            file.delete()
        }
    }

    private fun createTempPng(
        width: Int,
        height: Int,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        // Draw something so blurhash has data
        val g = img.createGraphics()
        g.color = java.awt.Color.BLUE
        g.fillRect(0, 0, width, height)
        g.dispose()

        val file = File.createTempFile("test_", ".png")
        file.deleteOnExit()
        ImageIO.write(img, "png", file)
        return file
    }
}
