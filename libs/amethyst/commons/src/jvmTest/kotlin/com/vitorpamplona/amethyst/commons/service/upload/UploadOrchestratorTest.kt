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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadOrchestratorTest {
    private val createdFiles = mutableListOf<File>()
    private val signer = NostrSignerInternal(KeyPair())

    @BeforeTest fun setup() {
        createdFiles.clear()
    }

    @AfterTest fun cleanup() {
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun reencodesJpegAndUploadsCompressedBytes() =
        runTest {
            val src = makeJpeg(2000, 1500)
            val fake = FakeBlossomClient()
            val orchestrator = UploadOrchestrator(fake)

            val result =
                orchestrator.upload(
                    file = src,
                    alt = "test",
                    serverBaseUrl = "https://example.test",
                    signer = signer,
                    quality = CompressionQuality.MEDIUM,
                )

            val captured = requireNotNull(fake.lastUploadedFile)
            assertTrue(
                captured.canonicalPath.startsWith(AmethystTempDir.rootDir().canonicalPath),
                "uploaded file must be the re-encoded temp under AmethystTempDir, got $captured",
            )
            assertTrue(captured.length() < src.length(), "compressed bytes should be smaller than source")
            assertEquals("image/jpeg", result.metadata.mimeType)
            // The metadata hash is computed on the SAME bytes that were uploaded.
            val expectedSha = sha256Hex(captured.exists().let { fake.lastUploadedBytes!! })
            assertEquals(expectedSha, result.metadata.sha256)
        }

    @Test
    fun aFallbackServerGoesOutOnItsOwnClient() =
        runTest {
            val selected = FakeBlossomClient(failNext = true)
            val fallback = FakeBlossomClient()
            val clients = mapOf("https://down.test" to selected, "https://up.test" to fallback)
            val result =
                UploadOrchestrator(selected) { clients.getValue(it) }.upload(
                    file = makeJpeg(40, 30),
                    alt = "test",
                    serverBaseUrl = "https://down.test",
                    signer = signer,
                    fallbackServerBaseUrls = listOf("https://up.test"),
                )
            assertTrue(result.blossom.url!!.startsWith("https://up.test/"), "landed on the fallback: ${result.blossom.url}")
            assertEquals(1, selected.uploads)
            assertEquals(1, fallback.uploads)
        }

    @Test
    fun aRefusedUploadTheServerAlreadyHoldsIsDoneThere() =
        runTest {
            val selected = FakeBlossomClient(failNext = true, holds = true)
            val fallback = FakeBlossomClient()
            val clients = mapOf("https://picked.test" to selected, "https://other.test" to fallback)
            val result =
                UploadOrchestrator(selected) { clients.getValue(it) }.upload(
                    file = makeJpeg(40, 30),
                    alt = "test",
                    serverBaseUrl = "https://picked.test",
                    signer = signer,
                    fallbackServerBaseUrls = listOf("https://other.test"),
                )
            assertEquals("https://picked.test/${result.metadata.sha256}", result.blossom.url, "kept on the server the user picked")
            assertEquals(0, fallback.uploads)
        }

    @Test
    fun passesThroughAnimatedGifUnchanged() =
        runTest {
            // Synthetic animated-GIF magic so the sniffer returns Animated.
            val src = makeAnimatedGif()
            val fake = FakeBlossomClient()
            val orchestrator = UploadOrchestrator(fake)

            val result =
                orchestrator.upload(
                    file = src,
                    alt = null,
                    serverBaseUrl = "https://example.test",
                    signer = signer,
                    quality = CompressionQuality.MEDIUM,
                )

            assertEquals(src.canonicalPath, fake.lastUploadedFile!!.canonicalPath, "must upload original bytes for animated GIF")
            assertEquals(src.length(), fake.lastUploadedFile!!.length())
            assertEquals("image/gif", result.metadata.mimeType)
        }

    @Test
    fun refusesAvifWithUnsupportedFormat() =
        runTest {
            val src =
                writeBytes(
                    "avif",
                    byteArrayOf(0x00, 0x00, 0x00, 0x20) + "ftyp".toByteArray() + "avif".toByteArray() + ByteArray(32),
                )
            val fake = FakeBlossomClient()
            val orchestrator = UploadOrchestrator(fake)

            assertFailsWith<CompressionException.UnsupportedFormat> {
                orchestrator.upload(
                    file = src,
                    alt = null,
                    serverBaseUrl = "https://example.test",
                    signer = signer,
                    quality = CompressionQuality.MEDIUM,
                )
            }
            assertEquals(null, fake.lastUploadedFile, "must not call client.upload when reencoder refuses")
        }

    @Test
    fun cleansUpTempFileAfterSuccessfulUpload() =
        runTest {
            val src = makeJpeg(800, 600)
            val fake = FakeBlossomClient()
            val orchestrator = UploadOrchestrator(fake)

            orchestrator.upload(
                file = src,
                alt = null,
                serverBaseUrl = "https://example.test",
                signer = signer,
                quality = CompressionQuality.MEDIUM,
            )

            val capturedPath = fake.lastUploadedFile!!.canonicalPath
            assertFalse(
                File(capturedPath).exists(),
                "re-encoded temp must be deleted after a successful upload, still exists at $capturedPath",
            )
        }

    @Test
    fun cleansUpTempFileAfterUploadFailure() =
        runTest {
            val src = makeJpeg(800, 600)
            val fake = FakeBlossomClient(failNext = true)
            val orchestrator = UploadOrchestrator(fake)

            assertFailsWith<RuntimeException> {
                orchestrator.upload(
                    file = src,
                    alt = null,
                    serverBaseUrl = "https://example.test",
                    signer = signer,
                    quality = CompressionQuality.MEDIUM,
                )
            }
            // FakeBlossomClient captures BEFORE it throws, so we have a path to check.
            val capturedPath = fake.lastUploadedFile!!.canonicalPath
            assertFalse(
                File(capturedPath).exists(),
                "re-encoded temp must be deleted even when upload throws, still exists at $capturedPath",
            )
        }

    @Test
    fun preparedFileIsTheReencodedOneAndIsDeletedAfterwards() =
        runTest {
            val src = makeJpeg(2000, 1500)

            var preparedPath: String? = null
            val hash =
                UploadOrchestrator.withPreparedFile(src, stripExif = true, quality = CompressionQuality.MEDIUM) { prepared, metadata ->
                    preparedPath = prepared.canonicalPath
                    assertTrue(prepared.canonicalPath != src.canonicalPath, "MEDIUM must hand over a re-encoded copy")
                    assertEquals(sha256Hex(prepared.readBytes()), metadata.sha256, "metadata must describe the prepared bytes")
                    metadata.sha256
                }

            assertTrue(hash.isNotEmpty())
            assertFalse(File(preparedPath!!).exists(), "the prepared temp must be deleted once the block returns")
            assertTrue(src.exists(), "the source file is never deleted")
        }

    @Test
    fun preparedFileWithoutQualityIsTheSourceItself() =
        runTest {
            val src = writeBytes("bin", byteArrayOf(1, 2, 3, 4))

            UploadOrchestrator.withPreparedFile(src, stripExif = false, quality = null) { prepared, metadata ->
                assertEquals(src.canonicalPath, prepared.canonicalPath)
                assertEquals(sha256Hex(byteArrayOf(1, 2, 3, 4)), metadata.sha256)
            }

            assertTrue(src.exists())
        }

    // ---------- helpers ----------

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
        val out = File.createTempFile("orchestrator_src_", ".jpg")
        createdFiles += out
        ImageIO.write(img, "jpg", out)
        return out
    }

    private fun makeAnimatedGif(): File {
        val out = File.createTempFile("orchestrator_src_", ".gif")
        createdFiles += out
        out.writeBytes(
            "GIF89a".toByteArray() + ByteArray(50) + "NETSCAPE2.0".toByteArray() + ByteArray(20),
        )
        return out
    }

    private fun writeBytes(
        ext: String,
        bytes: ByteArray,
    ): File {
        val out = File.createTempFile("orchestrator_src_", ".$ext")
        createdFiles += out
        out.writeBytes(bytes)
        return out
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Fake BlossomClient that snapshots the uploaded file's bytes
     * (so we can assert on them AFTER the orchestrator's cleanup
     * deletes the file) and returns a canned [BlossomUploadResult].
     */
    private class FakeBlossomClient(
        private val failNext: Boolean = false,
        // Whether a HEAD for the blob finds it (BUD-01).
        private val holds: Boolean = false,
    ) : BlossomClient() {
        var lastUploadedFile: File? = null
        var lastUploadedBytes: ByteArray? = null
        var uploads = 0

        override suspend fun has(
            hash: String,
            serverBaseUrl: String,
        ) = holds

        override suspend fun upload(
            file: File,
            contentType: String,
            serverBaseUrl: String,
            authHeader: String?,
            onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?,
        ): BlossomUploadResult {
            uploads++
            lastUploadedFile = file
            lastUploadedBytes = file.readBytes()
            if (failNext) throw RuntimeException("simulated upload failure")
            return BlossomUploadResult(
                url = "$serverBaseUrl/${sha256HexInternal(lastUploadedBytes!!)}",
                sha256 = sha256HexInternal(lastUploadedBytes!!),
                size = file.length(),
                type = contentType,
                uploaded = System.currentTimeMillis() / 1000,
            )
        }

        private fun sha256HexInternal(bytes: ByteArray): String =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
