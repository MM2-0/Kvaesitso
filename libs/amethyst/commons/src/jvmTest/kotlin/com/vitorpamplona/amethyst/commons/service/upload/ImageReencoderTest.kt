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

import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder.ReencodeResult
import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImageReencoderTest {
    private val createdFiles = mutableListOf<File>()

    @BeforeTest
    fun setup() {
        createdFiles.clear()
    }

    @AfterTest
    fun cleanup() {
        createdFiles.forEach { it.delete() }
        // Clear any per-test pixel override.
        System.clearProperty("amethyst.compression.maxPixels")
    }

    @Test
    fun reencodesJpegAtDesktopHigh() =
        runTest {
            val src = makeJpeg(4032, 3024)
            val result = ImageReencoder.reencode(src, CompressionQuality.DESKTOP_HIGH)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            val decoded = ImageIO.read(reencoded.file)
            assertTrue(decoded.width <= 1920, "width within Desktop High box, got ${decoded.width}")
            assertTrue(decoded.height <= 1920, "height within Desktop High box, got ${decoded.height}")
            // Source was 4032×3024 → landscape → longer edge clamps to 1920.
            assertEquals(1920, decoded.width, "longer edge = max-dim")
        }

    @Test
    fun reencodesEachComposerStepToItsSize() =
        runTest {
            // The shared composer's steps reach the re-encoder as plain targets.
            val src = makeJpeg(3000, 4000)
            for (maxDim in listOf(640, 1280, 1920, 2560)) {
                val reencoded = assertIs<ReencodeResult.Reencoded>(ImageReencoder.reencode(src, ImageSizeTarget(maxDim, 0.8f)))
                track(reencoded.file)
                val decoded = ImageIO.read(reencoded.file)
                // Portrait: the height is the longer edge.
                assertEquals(maxDim, decoded.height)
                assertEquals(maxDim * 3 / 4, decoded.width)
            }
        }

    @Test
    fun reencodesJpegAtMedium() =
        runTest {
            val src = makeJpeg(2000, 1500)
            val result = ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            val decoded = ImageIO.read(reencoded.file)
            // Medium = 640 max-dim. Longer edge clamps to 640.
            assertEquals(640, decoded.width)
        }

    @Test
    fun reencodesJpegAtLow() =
        runTest {
            val src = makeJpeg(2000, 1500)
            val result = ImageReencoder.reencode(src, CompressionQuality.LOW)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            val decoded = ImageIO.read(reencoded.file)
            assertEquals(640, decoded.width)
        }

    @Test
    fun outputSizeMonotonicAcrossPresets() =
        runTest {
            val src = makeJpeg(2000, 1500)
            val low = (ImageReencoder.reencode(src, CompressionQuality.LOW) as ReencodeResult.Reencoded).file.also(::track)
            val medium = (ImageReencoder.reencode(src, CompressionQuality.MEDIUM) as ReencodeResult.Reencoded).file.also(::track)
            val high = (ImageReencoder.reencode(src, CompressionQuality.HIGH) as ReencodeResult.Reencoded).file.also(::track)
            // DESKTOP_HIGH at 1920 is bigger than 640-based presets, so monotonicity is
            // strict only over the same-dim subset (LOW < MEDIUM < HIGH).
            assertTrue(low.length() < medium.length(), "LOW (${low.length()}) < MEDIUM (${medium.length()})")
            assertTrue(medium.length() < high.length(), "MEDIUM (${medium.length()}) < HIGH (${high.length()})")
        }

    @Test
    fun reencodesJpegAtHigh() =
        runTest {
            val src = makeJpeg(2000, 1500)
            val result = ImageReencoder.reencode(src, CompressionQuality.HIGH)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            val decoded = ImageIO.read(reencoded.file)
            // High = 640 max-dim. Longer edge clamps to 640.
            assertEquals(640, decoded.width)
        }

    @Test
    fun neverUpscalesSmallSource() =
        runTest {
            val src = makeJpeg(320, 240)
            val result = ImageReencoder.reencode(src, CompressionQuality.DESKTOP_HIGH)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            val decoded = ImageIO.read(reencoded.file)
            assertEquals(320, decoded.width, "small source must keep its dimensions")
            assertEquals(240, decoded.height, "small source must keep its dimensions")
        }

    @Test
    fun throwsInputTooLargeWhenAboveCap() =
        runTest {
            // Lower the cap so a tiny test JPEG triggers the guard.
            System.setProperty("amethyst.compression.maxPixels", "1000")
            val src = makeJpeg(100, 100) // 10 000 pixels > 1000 cap
            assertFailsWith<CompressionException.InputTooLarge> {
                ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            }
        }

    @Test
    fun refusesAvifInput() =
        runTest {
            val src = makeFile("avif", byteArrayOf(0, 0, 0, 0x20) + "ftyp".toByteArray() + "avif".toByteArray() + ByteArray(32))
            val e =
                assertFailsWith<CompressionException.UnsupportedFormat> {
                    ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
                }
            assertEquals("avif", e.format)
        }

    @Test
    fun refusesHeicInput() =
        runTest {
            val src = makeFile("heic", byteArrayOf(0, 0, 0, 0x20) + "ftyp".toByteArray() + "heic".toByteArray() + ByteArray(32))
            assertFailsWith<CompressionException.UnsupportedFormat> {
                ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            }
        }

    @Test
    fun passesThroughAnimatedGif() =
        runTest {
            // Synthetic animated-GIF magic: GIF89a + NETSCAPE2.0 application extension.
            val src =
                makeFile(
                    "gif",
                    "GIF89a".toByteArray() + ByteArray(50) + "NETSCAPE2.0".toByteArray() + ByteArray(20),
                )
            val result = ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            val pt = assertIs<ReencodeResult.PassThrough>(result)
            assertEquals(ImageReencoder.PassReason.Animated, pt.reason)
        }

    @Test
    fun passesThroughSvg() =
        runTest {
            val src = makeFile("svg", """<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"></svg>""".toByteArray())
            val result = ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            val pt = assertIs<ReencodeResult.PassThrough>(result)
            assertEquals(ImageReencoder.PassReason.Vector, pt.reason)
        }

    @Test
    fun outputIsValidJpeg() =
        runTest {
            val src = makeJpeg(800, 600)
            val result = ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            val bytes = reencoded.file.readBytes()
            assertTrue(bytes.size > 0, "output is non-empty")
            // SOI marker
            assertEquals(0xFF.toByte(), bytes[0])
            assertEquals(0xD8.toByte(), bytes[1])
        }

    @Test
    fun reencodesPngWithAlphaToJpeg() =
        runTest {
            // Regression: TYPE_INT_ARGB inputs (typical PNG decode)
            // used to crash the JPEG writer with "Bogus input
            // colorspace". encodeJpeg now flattens onto a white RGB
            // canvas before handing off to the writer.
            val src = makePngWithAlpha(800, 600)
            val result = ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            val decoded = ImageIO.read(reencoded.file)
            assertTrue(decoded != null, "output must be a valid JPEG")
            assertEquals(640, decoded.width, "Medium clamps long edge to 640")
        }

    @Test
    fun encodeFailedWrapsCauseWithoutCrashing() {
        // Regression: CompressionException had a double-set cause
        // (super(message, cause) + initCause(cause)) that threw
        // IllegalStateException at construction time, masking the
        // real encode error.
        val original = javax.imageio.IIOException("Bogus input colorspace")
        val wrapped = CompressionException.EncodeFailed(original)
        assertEquals(original, wrapped.cause)
        assertTrue(wrapped.message!!.contains("Bogus input colorspace"))
    }

    @Test
    fun outputLivesInAmethystTmpDir() =
        runTest {
            val src = makeJpeg(800, 600)
            val result = ImageReencoder.reencode(src, CompressionQuality.MEDIUM)
            val reencoded = assertIs<ReencodeResult.Reencoded>(result)
            track(reencoded.file)

            assertEquals(
                AmethystTempDir.rootDir().canonicalPath,
                reencoded.file.parentFile.canonicalPath,
                "temp files must land in AmethystTempDir, not /tmp",
            )
            assertTrue(
                reencoded.file.name.startsWith("amethyst_compress_"),
                "temp file name must carry the sweep prefix",
            )
        }

    // ---------- helpers ----------

    private fun track(file: File) {
        createdFiles += file
    }

    private fun makePngWithAlpha(
        width: Int,
        height: Int,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            // Transparent left half, solid colored right half — the
            // "Bogus input colorspace" trap fires regardless of
            // pixel data, just on the color model.
            for (y in 0 until height step 8) {
                for (x in 0 until width step 8) {
                    val alpha = if (x < width / 2) 0 else 255
                    val r = (x * 255 / width).coerceIn(0, 255)
                    val gg = (y * 255 / height).coerceIn(0, 255)
                    val b = ((x + y) * 255 / (width + height)).coerceIn(0, 255)
                    g.color = Color(r, gg, b, alpha)
                    g.fillRect(x, y, 8, 8)
                }
            }
        } finally {
            g.dispose()
        }
        val out = File.createTempFile("reencoder_alpha_", ".png")
        track(out)
        ImageIO.write(img, "png", out)
        return out
    }

    private fun makeJpeg(
        width: Int,
        height: Int,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            for (y in 0 until height step 8) {
                for (x in 0 until width step 8) {
                    val r = (x * 255 / width).coerceIn(0, 255)
                    val gg = (y * 255 / height).coerceIn(0, 255)
                    val b = ((x + y) * 255 / (width + height)).coerceIn(0, 255)
                    g.color = Color(r, gg, b)
                    g.fillRect(x, y, 8, 8)
                }
            }
        } finally {
            g.dispose()
        }
        val out = File.createTempFile("reencoder_src_", ".jpg")
        track(out)
        ImageIO.write(img, "jpg", out)
        return out
    }

    private fun makeFile(
        extension: String,
        bytes: ByteArray,
    ): File {
        val out = File.createTempFile("reencoder_src_", ".$extension")
        track(out)
        out.writeBytes(bytes)
        return out
    }
}
