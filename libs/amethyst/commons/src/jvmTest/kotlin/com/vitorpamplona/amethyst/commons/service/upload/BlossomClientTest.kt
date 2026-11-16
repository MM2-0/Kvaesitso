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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlossomClientTest {
    /**
     * BlossomClient calls Call.executeAsync(), which drives [Call.enqueue] — not [Call.execute].
     * Bridge the execute() stub set up above onto the async path so the response setup still applies.
     */
    private fun Call.answerAsyncFromExecuteStub() {
        every { cancel() } just Runs
        every { enqueue(any()) } answers { firstArg<Callback>().onResponse(this@answerAsyncFromExecuteStub, execute()) }
    }

    private fun mockOkHttp(
        responseCode: Int,
        body: String = "",
        headers: Headers = Headers.headersOf(),
    ): OkHttpClient {
        val requestSlot = slot<Request>()
        val mockCall = mockk<Call>()
        val mockClient = mockk<OkHttpClient>()

        every { mockClient.newCall(capture(requestSlot)) } returns mockCall
        every { mockCall.execute() } returns
            Response
                .Builder()
                .request(Request.Builder().url("https://example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message(if (responseCode == 200) "OK" else "Error")
                .headers(headers)
                .body(body.toResponseBody())
                .build()
        mockCall.answerAsyncFromExecuteStub()

        return mockClient
    }

    /** An OkHttpClient whose every call is answered by [handler], keyed off the request. */
    private fun dispatchingOkHttp(handler: (Request) -> Response): OkHttpClient {
        val mockClient = mockk<OkHttpClient>()
        every { mockClient.newCall(any()) } answers {
            val request = firstArg<Request>()
            val call = mockk<Call>()
            every { call.execute() } returns handler(request)
            call.answerAsyncFromExecuteStub()
            call
        }
        return mockClient
    }

    private fun response(
        request: Request,
        code: Int,
        body: ResponseBody = "".toResponseBody(),
        headers: Headers = Headers.headersOf(),
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .headers(headers)
            .body(body)
            .build()

    // A server that refuses every upload with a 409, and answers HEAD for the blob with [holdStatus].
    private fun refusingServer(holdStatus: Int) =
        dispatchingOkHttp { request ->
            when (request.method) {
                "PUT" -> response(request, 409, headers = Headers.headersOf("X-Reason", "Blob already exists."))
                "HEAD" -> response(request, holdStatus)
                else -> response(request, 404)
            }
        }

    @Test
    fun anUploadRefusedForABlobTheServerHoldsIsStored() =
        runTest {
            val bytes = "hi".toByteArray()
            val hash = sha256(bytes).toHexKey()
            val client = BlossomClient(refusingServer(holdStatus = 200))
            val result = client.uploadOrHeld(hash, bytes.size.toLong(), "text/plain", "https://cdn.example.com") { client.upload(bytes, "text/plain", "https://cdn.example.com", "Nostr x") }
            assertEquals("https://cdn.example.com/$hash", result.url)
            assertEquals(hash, result.sha256)
            assertEquals(2L, result.size)
        }

    @Test
    fun anUploadRefusedForABlobTheServerLacksStillFails() =
        runTest {
            val bytes = "hi".toByteArray()
            val hash = sha256(bytes).toHexKey()
            val client = BlossomClient(refusingServer(holdStatus = 404))
            val error =
                assertFailsWith<RuntimeException> {
                    client.uploadOrHeld(hash, bytes.size.toLong(), "text/plain", "https://cdn.example.com") { client.upload(bytes, "text/plain", "https://cdn.example.com", "Nostr x") }
                }
            assertTrue(error.message!!.contains("Blob already exists"), "the server's own reason is kept")
        }

    @Test
    fun mirrorThrowsUnsupportedOnMissingEndpoint() =
        runTest {
            // 404/405/501 on PUT /mirror means the endpoint is absent, not a rejected mirror.
            for (code in listOf(404, 405, 501)) {
                val client = BlossomClient(mockOkHttp(code))
                assertFailsWith<BlossomMirrorUnsupportedException> {
                    client.mirror(
                        sourceUrl = "https://source.example.com/abc",
                        serverBaseUrl = "https://target.example.com",
                        authHeader = "Nostr abc",
                    )
                }
            }
        }

    @Test
    fun mirrorRejectionStaysPlainRuntimeException() =
        runTest {
            // A mirror the server understood but refused must NOT be read as "no /mirror".
            val client = BlossomClient(mockOkHttp(413, "", Headers.headersOf("X-Reason", "too big")))
            val ex =
                assertFailsWith<RuntimeException> {
                    client.mirror("https://source.example.com/abc", "https://target.example.com", null)
                }
            assertFalse(ex is BlossomMirrorUnsupportedException)
            assertTrue(ex.message!!.contains("too big"))
        }

    @Test
    fun mirrorOrUploadFallsBackToUploadWhenUnsupported() =
        runTest {
            val bytes = byteArrayOf(9, 8, 7, 6, 5)
            val expectedHash = sha256(bytes).toHexKey()
            val descriptor = """{"url":"https://target.example.com/$expectedHash","sha256":"$expectedHash","size":${bytes.size}}"""
            var uploadedTo: String? = null

            val client =
                BlossomClient(
                    dispatchingOkHttp { req ->
                        when {
                            req.method == "PUT" && req.url.encodedPath.endsWith("/mirror") -> response(req, 404)
                            req.method == "GET" -> response(req, 200, bytes.toResponseBody("application/octet-stream".toMediaType()))
                            req.method == "PUT" && req.url.encodedPath.endsWith("/upload") -> {
                                uploadedTo = req.url.toString()
                                response(req, 200, descriptor.toResponseBody())
                            }
                            else -> response(req, 500)
                        }
                    },
                )

            val result =
                client.mirrorOrUpload(
                    sourceUrl = "https://source.example.com/$expectedHash",
                    expectedHash = expectedHash,
                    contentType = "image/png",
                    serverBaseUrl = "https://target.example.com",
                    authHeader = "Nostr abc",
                )

            assertEquals("https://target.example.com/upload", uploadedTo)
            assertEquals(expectedHash, result.sha256)
        }

    @Test
    fun mirrorOrUploadRejectsHashMismatchAndDoesNotUpload() =
        runTest {
            val servedBytes = byteArrayOf(1, 2, 3)
            // Ask for a DIFFERENT blob than the source will serve — a substituted download.
            val expectedHash = sha256(byteArrayOf(4, 5, 6)).toHexKey()
            var uploaded = false

            val client =
                BlossomClient(
                    dispatchingOkHttp { req ->
                        when {
                            req.method == "PUT" && req.url.encodedPath.endsWith("/mirror") -> response(req, 405)
                            req.method == "GET" -> response(req, 200, servedBytes.toResponseBody("application/octet-stream".toMediaType()))
                            req.method == "PUT" && req.url.encodedPath.endsWith("/upload") -> {
                                uploaded = true
                                response(req, 200, "{}".toResponseBody())
                            }
                            else -> response(req, 500)
                        }
                    },
                )

            assertFailsWith<RuntimeException> {
                client.mirrorOrUpload(
                    sourceUrl = "https://source.example.com/blob",
                    expectedHash = expectedHash,
                    contentType = "image/png",
                    serverBaseUrl = "https://target.example.com",
                    authHeader = null,
                )
            }
            assertFalse(uploaded)
        }

    @Test
    fun mirrorOrUploadUsesMirrorWhenSupported() =
        runTest {
            val descriptor = """{"url":"https://target.example.com/abc","sha256":"abc","size":3}"""
            var uploadCalled = false

            val client =
                BlossomClient(
                    dispatchingOkHttp { req ->
                        when {
                            req.method == "PUT" && req.url.encodedPath.endsWith("/mirror") -> response(req, 201, descriptor.toResponseBody())
                            req.method == "PUT" && req.url.encodedPath.endsWith("/upload") -> {
                                uploadCalled = true
                                response(req, 200, descriptor.toResponseBody())
                            }
                            else -> response(req, 500)
                        }
                    },
                )

            val result =
                client.mirrorOrUpload(
                    sourceUrl = "https://source.example.com/abc",
                    expectedHash = "abc",
                    contentType = "image/png",
                    serverBaseUrl = "https://target.example.com",
                    authHeader = "Nostr abc",
                )

            assertFalse(uploadCalled)
            assertEquals("abc", result.sha256)
        }

    @Test
    fun uploadSuccessReturnsResult() =
        runTest {
            val json =
                """{"url":"https://blossom.example.com/abc123.png","sha256":"abc123","size":1024}"""
            val client = BlossomClient(mockOkHttp(200, json))

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1, 2, 3))

            try {
                val result =
                    client.upload(
                        file = file,
                        contentType = "image/png",
                        serverBaseUrl = "https://blossom.example.com",
                        authHeader = "Nostr abc",
                    )

                assertEquals("https://blossom.example.com/abc123.png", result.url)
                assertEquals("abc123", result.sha256)
                assertEquals(1024L, result.size)
            } finally {
                file.delete()
            }
        }

    @Test
    fun aRefusalNamingAnIdenticalImageSaysWhichBlobItHolds() =
        runTest {
            // nostr.download's answer to an image it finds identical to one it holds (measured
            // 2026-11-02): 409, the blob's hash in X-Identical-Media, and an explanation.
            val held = "fcc2aec695955038879372628905052cccf074142d61cc015a51027023400719"
            val headers =
                Headers.headersOf(
                    "X-Identical-Media",
                    held,
                    "X-Reason",
                    "An identical image already exists on this server. Use the hash above to mirror it to other servers.",
                )
            val client = BlossomClient(mockOkHttp(409, "", headers))

            val file = File.createTempFile("test_", ".jpg")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1, 2, 3))

            try {
                val ex =
                    assertFailsWith<BlossomIdenticalMediaException> {
                        client.upload(file = file, contentType = "image/jpeg", serverBaseUrl = "https://nostr.download", authHeader = null)
                    }
                assertEquals(held, ex.existingHash)
                assertEquals("https://nostr.download", ex.server)
                assertTrue(ex.reason!!.startsWith("An identical image"))
            } finally {
                file.delete()
            }
        }

    @Test
    fun onlyA409WithAHashIsAnIdenticalImageRefusal() {
        val hash = "fcc2aec695955038879372628905052cccf074142d61cc015a51027023400719"
        assertEquals(hash, BlossomIdenticalMediaException.existingHashOf(409, " ${hash.uppercase()} "))
        assertEquals(null, BlossomIdenticalMediaException.existingHashOf(400, hash))
        assertEquals(null, BlossomIdenticalMediaException.existingHashOf(409, null))
        assertEquals(null, BlossomIdenticalMediaException.existingHashOf(409, "not-a-hash"))
        assertEquals(null, BlossomIdenticalMediaException.existingHashOf(409, hash.drop(1)))
    }

    @Test
    fun uploadFailureThrowsException() =
        runTest {
            val headers = Headers.headersOf("X-Reason", "File too large")
            val client = BlossomClient(mockOkHttp(413, "", headers))

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1, 2, 3))

            try {
                val ex =
                    assertFailsWith<RuntimeException> {
                        client.upload(
                            file = file,
                            contentType = "image/png",
                            serverBaseUrl = "https://blossom.example.com",
                            authHeader = null,
                        )
                    }
                assertTrue(ex.message!!.contains("File too large"))
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadFailureUsesStatusCodeWhenNoXReason() =
        runTest {
            val client = BlossomClient(mockOkHttp(500))

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1))

            try {
                val ex =
                    assertFailsWith<RuntimeException> {
                        client.upload(
                            file = file,
                            contentType = "image/png",
                            serverBaseUrl = "https://blossom.example.com",
                            authHeader = null,
                        )
                    }
                assertTrue(ex.message!!.contains("500"))
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadSendsAuthorizationHeader() =
        runTest {
            val requestSlot = slot<Request>()
            val mockCall = mockk<Call>()
            val mockClient = mockk<OkHttpClient>()

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.execute() } returns
                Response
                    .Builder()
                    .request(Request.Builder().url("https://example.com").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"url":"https://example.com/hash"}""".toResponseBody())
                    .build()
            mockCall.answerAsyncFromExecuteStub()

            val client = BlossomClient(mockClient)
            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1))

            try {
                client.upload(
                    file = file,
                    contentType = "image/png",
                    serverBaseUrl = "https://blossom.example.com",
                    authHeader = "Nostr base64token",
                )

                val sentRequest = requestSlot.captured
                assertEquals("Nostr base64token", sentRequest.header("Authorization"))
                assertEquals("https://blossom.example.com/upload", sentRequest.url.toString())
                assertEquals("PUT", sentRequest.method)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadUrlStripsTrailingSlash() =
        runTest {
            val requestSlot = slot<Request>()
            val mockCall = mockk<Call>()
            val mockClient = mockk<OkHttpClient>()

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.execute() } returns
                Response
                    .Builder()
                    .request(Request.Builder().url("https://example.com").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"url":"https://example.com/hash"}""".toResponseBody())
                    .build()
            mockCall.answerAsyncFromExecuteStub()

            val client = BlossomClient(mockClient)
            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1))

            try {
                client.upload(
                    file = file,
                    contentType = "image/png",
                    serverBaseUrl = "https://blossom.example.com/",
                    authHeader = null,
                )

                // Should not have double slash
                assertEquals("https://blossom.example.com/upload", requestSlot.captured.url.toString())
            } finally {
                file.delete()
            }
        }
}
