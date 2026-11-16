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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MultiOrchestratorEncryptedTest {
    /**
     * Encrypts the file's path as its bytes, once, the way the platform uploaders do, and serves the
     * blob at the returned URL. Fails the paths in [failing] the way Android's Blossom path does: it
     * returns the error without reporting it. Runs [during] before uploading.
     */
    private class FakeUploader(
        var failing: Set<String> = emptySet(),
        val during: (MediaUri) -> Unit = {},
    ) : MediaUploader {
        val blobs = ConcurrentHashMap<String, ByteArray>()
        val sent = ConcurrentLinkedQueue<String>()

        private fun send(
            progress: UploadOrchestrator,
            uri: MediaUri,
            mimeType: String?,
            bytes: (ByteArray) -> ByteArray,
        ): UploadingState.UploadingFinalState {
            during(uri)
            progress.updateState(0.5, UploadingState.Uploading)
            if (uri.toString() in failing) return UploadingState.Error(UploadError.FAILED_TO_UPLOAD_MEDIA, arrayOf("refused"))
            val blob = bytes(uri.toString().encodeToByteArray())
            val url = "https://blossom.example$uri"
            blobs[url] = blob
            sent += uri.toString()
            return progress.finish(
                UploadOrchestrator.OrchestratorResult.ServerResult(
                    FileHeader(mimeType, blob.size.toString(), blob.size, null, null),
                    url,
                    null,
                    null,
                    mimeType,
                    null,
                ),
            )
        }

        override suspend fun upload(
            progress: UploadOrchestrator,
            uri: MediaUri,
            mimeType: String?,
            alt: String?,
            contentWarningReason: String?,
            compressionQuality: CompressorQuality,
            server: ServerName,
            account: Account,
            useH265: Boolean,
            stripMetadata: Boolean,
            onStrippingFailed: suspend () -> Boolean,
            convertGifToMp4: Boolean,
            forcedSigner: NostrSigner?,
        ): UploadingState.UploadingFinalState = send(progress, uri, mimeType) { it }

        override suspend fun uploadEncrypted(
            progress: UploadOrchestrator,
            uri: MediaUri,
            mimeType: String?,
            alt: String?,
            contentWarningReason: String?,
            compressionQuality: CompressorQuality,
            encrypt: SymmetricCipher,
            server: ServerName,
            account: Account,
            useH265: Boolean,
            stripMetadata: Boolean,
            onStrippingFailed: suspend () -> Boolean,
            convertGifToMp4: Boolean,
            forcedSigner: NostrSigner?,
        ): UploadingState.UploadingFinalState = send(progress, uri, mimeType) { encrypt.encrypt(it) }

        override suspend fun compressIfNeeded(
            progress: UploadOrchestrator,
            uri: MediaUri,
            mimeType: String?,
            compressionQuality: CompressorQuality,
            useH265: Boolean,
            convertGifToMp4: Boolean,
        ): MediaCompressorResult = MediaCompressorResult(uri, mimeType, null)

        override suspend fun remoteFileHeader(
            url: String,
            httpClients: IRoleBasedHttpClientBuilder,
        ): FileHeader? = null
    }

    private val server = ServerName("test", "https://blossom.example")
    private val account = mockk<Account>()

    private fun files(vararg paths: String) = MultiOrchestrator(paths.map { SelectedMedia(StringMediaUri(it), "image/jpeg") })

    private suspend fun MultiOrchestrator.encrypted(
        uploader: MediaUploader,
        quality: CompressorQuality = CompressorQuality.UNCOMPRESSED,
    ) = uploadEncrypted(null, null, quality, { AESGCM() }, server, account, uploader)

    private val MultiOrchestrator.EncryptedUpload.url get() = (finished.result as UploadOrchestrator.OrchestratorResult.ServerResult).url

    private val UploadingState.Finished.url get() = (result as UploadOrchestrator.OrchestratorResult.ServerResult).url

    @Test
    fun everyFileDecryptsUnderTheCipherPairedWithIt() =
        runTest {
            val uploader = FakeUploader()
            val result = files("/tmp/a.jpg", "/tmp/b.jpg", "/tmp/c.jpg").encrypted(uploader)

            assertTrue(result.allGood, "errors: ${result.errors}")
            assertEquals(3, result.successful.size)
            assertEquals(
                3,
                result.successful
                    .map { it.cipher.nonce.toList() }
                    .toSet()
                    .size,
            )
            result.successful.forEach {
                val path = it.url.removePrefix("https://blossom.example")
                assertContentEquals(path.encodeToByteArray(), it.cipher.decrypt(uploader.blobs.getValue(it.url)))
            }
        }

    @Test
    fun aFailureTheUploaderDidNotReportIsStillAnError() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg")
            val result = files.encrypted(FakeUploader(failing = setOf("/tmp/b.jpg")))

            assertFalse(result.allGood)
            assertEquals(listOf(UploadError.FAILED_TO_UPLOAD_MEDIA), result.errors.map { it.error })
            assertEquals(listOf("https://blossom.example/tmp/a.jpg"), result.successful.map { it.url })
            // The bar no longer stays on Uploading.
            assertTrue(
                files
                    .get(1)
                    .orchestrator.progressState.value is UploadingState.Error,
            )
        }

    @Test
    fun aFileRemovedDuringTheUploadIsLeftOut() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg")
            val b = files.get(1)
            val result = files.encrypted(FakeUploader(during = { if (it == b.media.uri) files.remove(b) }))

            assertTrue(result.allGood)
            assertEquals(listOf("https://blossom.example/tmp/a.jpg"), result.successful.map { it.url })
        }

    @Test
    fun anEncryptedRetrySendsOnlyTheFilesThatFailed() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg", "/tmp/c.jpg")
            val uploader = FakeUploader(failing = setOf("/tmp/b.jpg"))
            val first = files.encrypted(uploader)
            assertFalse(first.allGood)

            uploader.failing = emptySet()
            val retry = files.encrypted(uploader)

            assertTrue(retry.allGood)
            assertEquals(listOf("/tmp/a.jpg", "/tmp/c.jpg", "/tmp/b.jpg"), uploader.sent.toList().let { it.take(2).sorted() + it.drop(2) })
            assertEquals(listOf("a", "b", "c"), retry.successful.map { it.url.substringAfterLast('/').take(1) })
            // The kept files carry the very cipher that sealed their blob.
            first.successful.forEach { kept -> assertSame(kept.cipher, retry.successful.single { it.url == kept.url }.cipher) }
            retry.successful.forEach { it.cipher.decrypt(uploader.blobs.getValue(it.url)) }
        }

    @Test
    fun aRetryUnderOtherSettingsSendsEverythingAgain() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg")
            val uploader = FakeUploader(failing = setOf("/tmp/b.jpg"))
            files.encrypted(uploader)

            uploader.failing = emptySet()
            files.encrypted(uploader, CompressorQuality.HIGH)

            assertEquals(3, uploader.sent.size)
        }

    @Test
    fun aPlaintextRetryKeepsTheFilesThatWentUpEncrypted() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg")
            val uploader = FakeUploader(failing = setOf("/tmp/b.jpg"))
            val first = files.encrypted(uploader)

            uploader.failing = emptySet()
            val retry = files.upload(null, null, CompressorQuality.UNCOMPRESSED, server, account, uploader, keepEncrypted = true)

            assertTrue(retry.allGood)
            assertEquals(listOf("https://blossom.example/tmp/b.jpg"), retry.successful.map { it.url })
            assertSame(first.successful.single().cipher, retry.keptEncrypted.single().cipher)
            assertEquals(listOf("/tmp/a.jpg", "/tmp/b.jpg"), uploader.sent.toList())
            assertContentEquals("/tmp/b.jpg".encodeToByteArray(), uploader.blobs.getValue("https://blossom.example/tmp/b.jpg"))
        }

    @Test
    fun aPlaintextUploadSendsEverythingUnlessAskedToKeep() =
        runTest {
            val files = files("/tmp/a.jpg")
            val uploader = FakeUploader()
            files.encrypted(uploader)
            val plain = files.upload(null, null, CompressorQuality.UNCOMPRESSED, server, account, uploader)

            assertTrue(plain.keptEncrypted.isEmpty())
            assertEquals(1, plain.successful.size)
            assertEquals(2, uploader.sent.size)
            assertNull(plain.errors.firstOrNull())
        }

    @Test
    fun aRetryWithAnotherCaptionStillReusesTheFiles() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg")
            val uploader = FakeUploader(failing = setOf("/tmp/b.jpg"))
            files.encrypted(uploader)

            uploader.failing = emptySet()
            val retry = files.uploadEncrypted("fixed typo", "spoilers", CompressorQuality.UNCOMPRESSED, { AESGCM() }, server, account, uploader)

            assertTrue(retry.allGood)
            assertEquals(listOf("/tmp/a.jpg", "/tmp/b.jpg"), uploader.sent.toList())
        }

    @Test
    fun aSecondPlaintextRetryStillKeepsTheEncryptedFiles() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg", "/tmp/c.jpg")
            val uploader = FakeUploader(failing = setOf("/tmp/b.jpg", "/tmp/c.jpg"))
            val first = files.encrypted(uploader)

            uploader.failing = setOf("/tmp/c.jpg")
            assertFalse(files.upload(null, null, CompressorQuality.UNCOMPRESSED, server, account, uploader, keepEncrypted = true).allGood)

            uploader.failing = emptySet()
            val again = files.upload(null, null, CompressorQuality.UNCOMPRESSED, server, account, uploader, keepEncrypted = true)

            assertTrue(again.allGood)
            assertSame(first.successful.single().cipher, again.keptEncrypted.single().cipher)
            // b went up in plaintext on the first retry and again on the second: plain uploads are not reused.
            assertEquals(listOf("/tmp/a.jpg", "/tmp/b.jpg", "/tmp/b.jpg", "/tmp/c.jpg"), uploader.sent.toList().let { listOf(it[0], it[1]) + it.drop(2).sorted() })
        }

    @Test
    fun aFileWhoseBarWasResetIsStillReused() =
        runTest {
            val files = files("/tmp/a.jpg", "/tmp/b.jpg")
            val uploader = FakeUploader(failing = setOf("/tmp/b.jpg"))
            files.encrypted(uploader)
            files.get(0).orchestrator.updateState(0.0, UploadingState.Ready)

            uploader.failing = emptySet()
            val retry = files.encrypted(uploader)

            assertTrue(retry.allGood)
            assertEquals(2, uploader.sent.size)
            assertTrue(
                files
                    .get(0)
                    .orchestrator.progressState.value is UploadingState.Finished,
            )
        }
}
