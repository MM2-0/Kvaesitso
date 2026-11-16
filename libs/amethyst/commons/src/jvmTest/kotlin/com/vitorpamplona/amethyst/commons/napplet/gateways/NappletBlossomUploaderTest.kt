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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NappletBlossomUploaderTest {
    private val bytes = byteArrayOf(1, 2, 3)
    private val hash = sha256(bytes).toHexKey()

    // A server that answers an upload with [answer] (or throws [refusal]) and holds [held].
    private class Server(
        private val answer: BlossomUploadResult? = null,
        private val refusal: String? = null,
        private val held: Set<String> = emptySet(),
    ) : BlossomClient() {
        var uploads = 0

        override suspend fun upload(
            bytes: ByteArray,
            contentType: String,
            serverBaseUrl: String,
            authHeader: String?,
            onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?,
        ): BlossomUploadResult {
            uploads++
            return answer ?: throw RuntimeException(refusal ?: "refused")
        }

        override suspend fun has(
            hash: HexKey,
            serverBaseUrl: String,
        ) = hash in held
    }

    private fun uploader(vararg servers: Pair<String, Server>) =
        NappletBlossomUploader(
            servers = { servers.map { it.first } },
            auth = { _, _, _ -> "Nostr auth" },
            blossom = { url -> servers.first { it.first == url }.second },
        )

    @Test
    fun aServerThatRefusesADuplicateItAlreadyHoldsStoredIt() =
        runTest {
            val result = uploader("https://a.example" to Server(refusal = "An identical image already exists", held = setOf(hash))).upload(bytes, "image/png", null)
            assertEquals("https://a.example/$hash", result?.url)
            assertEquals(hash, result?.sha256)
        }

    @Test
    fun theNextServerIsTriedWhenOneFailsOrTransformsTheFile() =
        runTest {
            val broken = Server(refusal = "down")
            val transforms = Server(answer = BlossomUploadResult("https://b.example/other", sha256 = "ff".repeat(32)))
            val good = Server(answer = BlossomUploadResult("https://c.example/$hash.png", sha256 = hash, size = 3, type = "image/png"))
            val result = uploader("https://a.example" to broken, "https://b.example" to transforms, "https://c.example" to good).upload(bytes, "image/png", "p.png")
            assertEquals("https://c.example/$hash.png", result?.url)
            assertEquals(1, good.uploads)
        }

    @Test
    fun nothingStoredAnywhereIsAFailure() =
        runTest {
            assertNull(uploader("https://a.example" to Server(refusal = "no")).upload(bytes, "image/png", null))
            assertNull(uploader().upload(bytes, "image/png", null))
        }
}
