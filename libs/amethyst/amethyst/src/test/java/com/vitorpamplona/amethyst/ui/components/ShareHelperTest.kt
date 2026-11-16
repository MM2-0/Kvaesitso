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
package com.vitorpamplona.amethyst.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShareHelperTest {
    @Test
    fun moveTempFileForSharing_renameFails_copiesAndDeletesSource() {
        val tempDir = Files.createTempDirectory("sharehelpertest").toFile()
        val tempFile = File(tempDir, "video.tmp")
        val content = "test-content"
        tempFile.writeText(content)
        val targetFile = File(tempDir, "shared.mp4")

        ShareHelper.moveTempFileForSharing(
            tempFile,
            targetFile,
            rename = { _, _ -> false },
        )

        assertTrue(targetFile.exists())
        assertEquals(content, targetFile.readText())
        assertFalse(tempFile.exists())

        targetFile.delete()
        tempDir.delete()
    }

    @Test
    fun getImageExtension_jpeg() {
        val file = createTempFileWithBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x00))
        assertEquals("jpg", ShareHelper.getImageExtension(file))
        file.delete()
    }

    @Test
    fun getImageExtension_png() {
        val file = createTempFileWithBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        assertEquals("png", ShareHelper.getImageExtension(file))
        file.delete()
    }

    @Test
    fun getImageExtension_gif() {
        val file = createTempFileWithBytes("GIF89a".toByteArray())
        assertEquals("gif", ShareHelper.getImageExtension(file))
        file.delete()
    }

    @Test
    fun getImageExtension_webp() {
        // RIFF....WEBP
        val header = ByteArray(12)
        "RIFF".toByteArray().copyInto(header, 0)
        "WEBP".toByteArray().copyInto(header, 8)
        val file = createTempFileWithBytes(header)
        assertEquals("webp", ShareHelper.getImageExtension(file))
        file.delete()
    }

    @Test
    fun getImageExtension_unknownDefaultsToJpg() {
        val file = createTempFileWithBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
        assertEquals("jpg", ShareHelper.getImageExtension(file))
        file.delete()
    }

    @Test
    fun getImageExtension_tooShortDefaultsToJpg() {
        val file = createTempFileWithBytes(byteArrayOf(0x00, 0x01))
        assertEquals("jpg", ShareHelper.getImageExtension(file))
        file.delete()
    }

    @Test
    fun getVideoExtension_webm() {
        val file = createTempFileWithBytes(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x00, 0x00))
        assertEquals("webm", ShareHelper.getVideoExtension(file))
        file.delete()
    }

    @Test
    fun getVideoExtension_avi() {
        // RIFF....AVI
        val header = ByteArray(12)
        "RIFF".toByteArray().copyInto(header, 0)
        "AVI ".toByteArray().copyInto(header, 8)
        val file = createTempFileWithBytes(header)
        assertEquals("avi", ShareHelper.getVideoExtension(file))
        file.delete()
    }

    @Test
    fun getVideoExtension_mp4Isom() {
        // ....ftypisom
        val header = ByteArray(12)
        "ftyp".toByteArray().copyInto(header, 4)
        "isom".toByteArray().copyInto(header, 8)
        val file = createTempFileWithBytes(header)
        assertEquals("mp4", ShareHelper.getVideoExtension(file))
        file.delete()
    }

    @Test
    fun getVideoExtension_mov() {
        // ....ftypqt__
        val header = ByteArray(12)
        "ftyp".toByteArray().copyInto(header, 4)
        "qt  ".toByteArray().copyInto(header, 8)
        val file = createTempFileWithBytes(header)
        assertEquals("mov", ShareHelper.getVideoExtension(file))
        file.delete()
    }

    @Test
    fun getVideoExtension_unknownDefaultsToMp4() {
        val file = createTempFileWithBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
        assertEquals("mp4", ShareHelper.getVideoExtension(file))
        file.delete()
    }

    @Test
    fun getMediaExtension_emptyFileDefaultsToImage() {
        val file = createTempFileWithBytes(byteArrayOf())
        assertEquals("jpg", ShareHelper.getMediaExtension(file, isVideo = false))
        file.delete()
    }

    @Test
    fun getMediaExtension_emptyFileDefaultsToVideo() {
        val file = createTempFileWithBytes(byteArrayOf())
        assertEquals("mp4", ShareHelper.getMediaExtension(file, isVideo = true))
        file.delete()
    }

    private fun createTempFileWithBytes(bytes: ByteArray): File {
        val file = File.createTempFile("sharehelpertest", ".tmp")
        file.writeBytes(bytes)
        return file
    }

    // A slash-less Intent.type matches no IntentFilter, so the chooser opens with zero targets and
    // the share silently fails. Author-supplied `m` tags can carry exactly that (Primal iOS emits
    // `m jpeg`), so a bare subtype must be repaired rather than forwarded.
    @Test
    fun resolveShareMimeType_bareSubtype_isExpandedToFullMimeType() {
        assertEquals("image/jpeg", ShareHelper.resolveShareMimeType("jpeg", "jpg"))
        assertEquals("video/mp4", ShareHelper.resolveShareMimeType("mp4", "mp4"))
        assertEquals("image/jpeg", ShareHelper.resolveShareMimeType("JPEG", "jpg"))
    }

    @Test
    fun resolveShareMimeType_wellFormedDeclaration_isPreserved() {
        assertEquals("image/png", ShareHelper.resolveShareMimeType("image/png", "png"))
    }

    // An unusable declaration falls back to the extension, which is sniffed from the file's magic
    // numbers and so is not attacker-controlled.
    @Test
    fun resolveShareMimeType_unrecognizableDeclaration_fallsBackToSniffedExtension() {
        assertEquals("image/png", ShareHelper.resolveShareMimeType("notatype", "png"))
        assertEquals("image/png", ShareHelper.resolveShareMimeType("", "png"))
    }

    @Test
    fun resolveShareMimeType_noDeclaration_usesCanonicalTypeForExtension() {
        // Not "image/jpg" -- jpg is not a registered subtype.
        assertEquals("image/jpeg", ShareHelper.resolveShareMimeType(null, "jpg"))
        assertEquals("video/quicktime", ShareHelper.resolveShareMimeType(null, "mov"))
    }

    // The invariant the share sheet depends on, pinned across the whole set of extensions
    // getMediaExtension can sniff: whatever the event declared, Intent.type stays matchable.
    @Test
    fun resolveShareMimeType_alwaysProducesASlashSeparatedType() {
        listOf("jpg", "png", "gif", "webp", "webm", "avi", "mp4", "mov").forEach { extension ->
            val resolved = ShareHelper.resolveShareMimeType("notatype", extension)
            assertTrue("`$extension` resolved to un-matchable type `$resolved`", resolved.contains("/"))
        }
    }
}
