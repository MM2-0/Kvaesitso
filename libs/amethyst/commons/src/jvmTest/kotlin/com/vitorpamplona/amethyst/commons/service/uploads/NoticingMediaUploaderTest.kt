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

import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NoticingMediaUploaderTest {
    private fun finished(vararg notices: UploadNotice) =
        UploadingState.Finished(
            UploadOrchestrator.OrchestratorResult.ServerResult(
                fileHeader = FileHeader("image/jpeg", "ab".repeat(32), 10, null, null),
                url = "https://b.example/blob",
                magnet = null,
                uploadedHash = null,
                mimeTypeBeforeEncryption = null,
                hashBeforeEncryption = null,
                notices = notices.toList(),
            ),
        )

    private suspend fun uploadWith(answer: UploadingState.UploadingFinalState): List<UserNotice> {
        val told = mutableListOf<UserNotice>()
        val inner = mockk<MediaUploader>()
        coEvery { inner.upload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns answer
        NoticingMediaUploader(inner) { told += it }.upload(
            UploadOrchestrator(),
            StringMediaUri("content://picture"),
            "image/jpeg",
            null,
            null,
            CompressorQuality.UNCOMPRESSED,
            ServerName("nostr.download", "https://nostr.download"),
            mockk(relaxed = true),
            false,
            true,
            { true },
            false,
            null,
        )
        return told
    }

    @Test
    fun anUploadThatWentElsewhereTellsTheUser() =
        runTest {
            val notice = UploadNotice.UploadedToAnotherServer("nostr.download", "blossom.band", "identical image")
            assertEquals(listOf<UserNotice>(notice), uploadWith(finished(notice)))
        }

    @Test
    fun aPlainUploadAndAFailureTellNothing() =
        runTest {
            assertEquals(emptyList(), uploadWith(finished()))
            assertEquals(emptyList(), uploadWith(UploadingState.Error(UploadError.FAILED_TO_UPLOAD_MEDIA, arrayOf("down"))))
        }
}
