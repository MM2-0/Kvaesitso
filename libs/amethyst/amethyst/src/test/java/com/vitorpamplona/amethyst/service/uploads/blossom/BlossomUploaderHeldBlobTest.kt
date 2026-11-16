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
package com.vitorpamplona.amethyst.service.uploads.blossom

import android.content.Context
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** An upload a server refuses, of a blob it already holds under the same hash, is stored. */
class BlossomUploaderHeldBlobTest {
    private val bytes = "hello".toByteArray()
    private val hash = sha256(bytes).toHexKey()
    private val requests = mutableListOf<String>()

    // Refuses every PUT with a 409; answers HEAD for the blob with [holdStatus].
    private fun server(holdStatus: Int): OkHttpClient {
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            requests += "${request.method} ${request.url.encodedPath}"
            val code = if (request.method == "HEAD") holdStatus else 409
            val response =
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("x")
                    .headers(Headers.headersOf("X-Reason", "Blob already exists."))
                    .body("".toResponseBody())
                    .build()
            val call = mockk<Call>()
            every { call.cancel() } just Runs
            every { call.enqueue(any()) } answers { firstArg<Callback>().onResponse(call, response) }
            call
        }
        return client
    }

    private suspend fun upload(
        holdStatus: Int,
        media: Boolean = false,
    ) = BlossomUploader().upload(
        openStream = { bytes.inputStream() },
        hash = hash,
        length = bytes.size.toLong(),
        baseFileName = "hello.txt",
        contentType = "text/plain",
        alt = null,
        sensitiveContent = null,
        serverBaseUrl = "https://cdn.example.com",
        okHttpClient = { server(holdStatus) },
        httpAuth = { _, _, _ -> null },
        context = mockk<Context>(relaxed = true),
        useMediaEndpoint = media,
    )

    @Test
    fun aRefusedUploadOfAHeldBlobIsStored() =
        runTest {
            val result = upload(holdStatus = 200)
            assertEquals("https://cdn.example.com/$hash", result.url)
            assertEquals(hash, result.sha256)
            assertEquals(listOf("PUT /upload", "HEAD /$hash"), requests)
        }

    @Test
    fun aRefusedUploadOfAMissingBlobStillFails() =
        runTest {
            assertTrue(runCatching { upload(holdStatus = 404) }.isFailure)
        }

    @Test
    fun aMediaUploadNeverCountsTheOriginalHash() =
        runTest {
            // /media may transform the file, so a blob under the original hash proves nothing.
            assertTrue(runCatching { upload(holdStatus = 200, media = true) }.isFailure)
            assertTrue("no HEAD for a /media upload", requests.none { it.startsWith("HEAD") })
        }
}
