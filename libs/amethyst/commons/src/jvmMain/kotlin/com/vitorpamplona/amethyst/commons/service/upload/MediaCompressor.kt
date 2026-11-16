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

import com.vitorpamplona.quartz.utils.Log
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.io.ByteArrayOutputStream
import java.io.File

object MediaCompressor {
    /**
     * Strip EXIF from a JPEG. Caller owns the returned file's cleanup
     * — the temp file lands under [AmethystTempDir] and is NOT
     * registered with deleteOnExit (long-running desktop sessions
     * leaked under the old behavior, see
     * `docs/temp-file-cleanup-analysis.md`).
     *
     * Returns the input file unchanged if the source is not a JPEG,
     * carries no EXIF, or strip fails.
     */
    fun stripExif(file: File): File {
        // Sniff the bytes rather than trusting the name: a camera JPEG handed
        // over as an extension-less temp file must still lose its GPS tags.
        if (ImageFormatSniffer.sniff(file) !is ImageFormat.Jpeg) {
            return file
        }

        return try {
            val bytes = file.readBytes()
            // Drops the Exif blocks by hand: Commons Imaging refuses a file with more than one (an
            // editor re-saving a camera photo adds its own), and the camera's, with the GPS, stays.
            val withoutExif = JpegSegments.withoutExif(bytes) ?: throw IllegalArgumentException("malformed JPEG header")
            if (withoutExif.size == bytes.size) return file
            // The orientation tag is the one EXIF field viewers need: without it a phone photo,
            // stored sideways, displays on its side. It says nothing about the person.
            val orientation = ExifOrientation.read(bytes)
            val result =
                if (orientation == ExifOrientation.NORMAL) {
                    withoutExif
                } else {
                    val onlyOrientation = TiffOutputSet()
                    onlyOrientation.orCreateRootDirectory.add(TiffTagConstants.TIFF_TAG_ORIENTATION, orientation.toShort())
                    ByteArrayOutputStream().also { ExifRewriter().updateExifMetadataLossless(withoutExif, it, onlyOrientation) }.toByteArray()
                }
            val stripped = AmethystTempDir.createTempFile("amethyst_stripped_", ".jpg")
            stripped.writeBytes(result)
            stripped
        } catch (e: Exception) {
            // Never silent: the UI promises the original is stripped before upload.
            Log.w("MediaCompressor", "EXIF strip failed for ${file.name}; uploading unstripped original", e)
            file
        }
    }
}
