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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phone cameras store pixels sideways and record the turn in the EXIF orientation tag, which every
 * viewer applies. The re-encoder writes no EXIF, so it must apply the turn itself; the metadata
 * strip must keep the tag while it drops the rest. And a cancelled re-encode must not leave its
 * temp file behind.
 */
class ImageOrientationAndCancellationTest {
    private val createdFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun reencodeAppliesExifRotation() =
        runTest {
            // Stored 400 x 200 with a red block in the stored top-left; orientation 6 means "turn
            // 90 degrees clockwise to display", which puts that block in the displayed top-right.
            val src = makeJpegWithMarker(400, 200, orientation = 6)
            val out = assertIs<ReencodeResult.Reencoded>(ImageReencoder.reencode(src, ImageSizeTarget(100, 0.9f))).file.also(createdFiles::add)

            val decoded = ImageIO.read(out)
            assertEquals(50, decoded.width, "displayed width")
            assertEquals(100, decoded.height, "displayed height")
            assertTrue(isRed(decoded.getRGB(decoded.width - 5, 5)), "marker lands top-right")
            assertTrue(!isRed(decoded.getRGB(5, 5)), "top-left is not the marker")
        }

    @Test
    fun reencodeKeepsUnrotatedImagesAsTheyAre() =
        runTest {
            val src = makeJpegWithMarker(400, 200, orientation = 1)
            val out = assertIs<ReencodeResult.Reencoded>(ImageReencoder.reencode(src, ImageSizeTarget(100, 0.9f))).file.also(createdFiles::add)

            val decoded = ImageIO.read(out)
            assertEquals(100, decoded.width)
            assertEquals(50, decoded.height)
            assertTrue(isRed(decoded.getRGB(3, 3)), "marker stays top-left")
        }

    @Test
    fun stripExifKeepsOrientationAndDropsTheRest() {
        val src = makeJpegWithMarker(400, 200, orientation = 6, withGps = true)
        val stripped = MediaCompressor.stripExif(src).also(createdFiles::add)
        assertNotEquals(src, stripped, "a JPEG with GPS must be rewritten")

        val exif = (Imaging.getMetadata(stripped) as? JpegImageMetadata)
        assertEquals(6, exif?.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION)?.intValue, "orientation kept")
        assertNull(exif?.exif?.gpsInfo, "GPS removed")
    }

    @Test
    fun metadataReportsTheDisplayedSize() {
        val src = makeJpegWithMarker(400, 200, orientation = 6)
        val metadata = MediaMetadataReader.compute(src)
        assertEquals(200, metadata.width, "displayed width")
        assertEquals(400, metadata.height, "displayed height")
    }

    @Test
    fun orientationComesFromTheFirstExifBlock() =
        runTest {
            // An editor that re-saves a phone photo can add its own Exif block after the camera's.
            val src = makeJpegWithTwoExifBlocks(400, 200)
            assertEquals(2, exifSegments(src.readBytes()).size, "fixture has both blocks")
            assertEquals(6, ExifOrientation.read(src))
            assertEquals(6, ExifOrientation.read(src.readBytes()))

            val out = assertIs<ReencodeResult.Reencoded>(ImageReencoder.reencode(src, ImageSizeTarget(100, 0.9f))).file.also(createdFiles::add)
            val decoded = ImageIO.read(out)
            assertEquals(50, decoded.width, "displayed width")
            assertEquals(100, decoded.height, "displayed height")
            assertTrue(isRed(decoded.getRGB(decoded.width - 5, 5)), "marker lands top-right")

            val metadata = MediaMetadataReader.compute(src)
            assertEquals(200, metadata.width, "displayed width")
            assertEquals(400, metadata.height, "displayed height")
        }

    @Test
    fun stripExifDropsEveryExifBlock() {
        val src = makeJpegWithTwoExifBlocks(400, 200)
        val stripped = MediaCompressor.stripExif(src).also(createdFiles::add)
        assertNotEquals(src, stripped, "a JPEG with GPS must be rewritten")

        val bytes = stripped.readBytes()
        assertEquals(1, exifSegments(bytes).size, "only the rewritten block is left")
        assertEquals(6, ExifOrientation.read(bytes), "orientation kept")
        assertNull((Imaging.getMetadata(bytes) as? JpegImageMetadata)?.exif?.gpsInfo, "GPS removed")
    }

    @Test
    fun readIgnoresBrokenFiles() {
        val tagged = makeJpegWithMarker(40, 20, orientation = 6).readBytes()
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(ByteArray(0)))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
        // Every truncation of a tagged file reads as either its orientation or none, never throws.
        for (size in 0 until 200) {
            val read = ExifOrientation.read(tagged.copyOf(minOf(size, tagged.size)))
            assertTrue(read == 6 || read == ExifOrientation.NORMAL, "size $size read $read")
        }
    }

    @Test
    fun cancellingDuringEncodeLeavesNoTempFile() =
        runBlocking {
            val src = makeJpegWithMarker(400, 200, orientation = 1)
            // Check this one file, never list AmethystTempDir: the other test fork writes there.
            val out = File.createTempFile("reencoder_cancel_", ".jpg").also(createdFiles::add)

            // The temp file is made just before the (uncancellable) JPEG encode, so cancelling
            // there lands during the encode on any machine. Nothing else cancels this job, so the
            // exception also proves the re-encode got that far.
            val job =
                async {
                    ImageReencoder.reencode(src, ImageSizeTarget(100, 0.9f)) {
                        cancel()
                        out
                    }
                }
            assertFailsWith<CancellationException> { job.await() }

            assertFalse(out.exists(), "the cancelled re-encode's temp file is gone")
        }

    // ---------- helpers ----------

    private fun isRed(argb: Int): Boolean {
        val c = Color(argb)
        return c.red > 200 && c.green < 60 && c.blue < 60
    }

    private fun makeJpegWithMarker(
        width: Int,
        height: Int,
        orientation: Int,
        withGps: Boolean = false,
    ): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.color = Color.RED
            g.fillRect(0, 0, width / 4, height / 4)
        } finally {
            g.dispose()
        }
        val plain = ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()

        val set = TiffOutputSet()
        set.orCreateRootDirectory.add(TiffTagConstants.TIFF_TAG_ORIENTATION, orientation.toShort())
        if (withGps) set.setGpsInDegrees(-71.06, 42.36)
        val tagged = ByteArrayOutputStream().also { ExifRewriter().updateExifMetadataLossless(plain, it, set) }.toByteArray()

        return File.createTempFile("orientation_src_", ".jpg").also {
            it.writeBytes(tagged)
            createdFiles += it
        }
    }

    /** Orientation 6 in a first Exif block, and GPS without orientation in a second one. */
    private fun makeJpegWithTwoExifBlocks(
        width: Int,
        height: Int,
    ): File {
        val orientationBlock = exifSegments(makeJpegWithMarker(width, height, orientation = 6).readBytes()).single()

        val gpsOnly = TiffOutputSet().apply { setGpsInDegrees(-71.06, 42.36) }
        val plain = makeJpegWithMarker(width, height, orientation = 1).readBytes()
        val stripped = ByteArrayOutputStream().also { ExifRewriter().removeExifMetadata(plain, it) }.toByteArray()
        val withGps = ByteArrayOutputStream().also { ExifRewriter().updateExifMetadataLossless(stripped, it, gpsOnly) }.toByteArray()

        // SOI, then the orientation block, then everything the GPS file had.
        val bytes = withGps.copyOfRange(0, 2) + orientationBlock + withGps.copyOfRange(2, withGps.size)
        return File.createTempFile("two_exif_src_", ".jpg").also {
            it.writeBytes(bytes)
            createdFiles += it
        }
    }

    /** Each Exif APP1 segment before the scan, marker included. */
    private fun exifSegments(jpeg: ByteArray): List<ByteArray> {
        val found = mutableListOf<ByteArray>()
        var i = 2
        while (i + 4 <= jpeg.size && jpeg[i] == 0xFF.toByte()) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA) break
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val segment = jpeg.copyOfRange(i, i + 2 + length)
            if (marker == 0xE1 && segment.size > 10 && String(segment, 4, 4, Charsets.US_ASCII) == "Exif") found += segment
            i += 2 + length
        }
        return found
    }
}
