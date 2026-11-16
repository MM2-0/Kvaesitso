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

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UploadOrchestratorClientTest {
    // A strict BlossomClient mock whose uploadOrHeld runs the upload it wraps, as the real one does
    // when the server takes it (or refuses a blob it doesn't hold: the upload's error comes through).
    private fun blossomMock(): BlossomClient =
        mockk<BlossomClient>().also { client ->
            coEvery { client.uploadOrHeld(any(), any(), any(), any(), any()) } coAnswers { arg<suspend () -> BlossomUploadResult>(4).invoke() }
        }

    @BeforeTest
    fun setup() {
        mockkObject(BlossomAuth)
        coEvery {
            BlossomAuth.createUploadAuth(any(), any(), any(), any())
        } returns "Nostr fakeAuthToken"
    }

    @AfterTest
    fun teardown() {
        unmockkObject(BlossomAuth)
    }

    @Test
    fun uploadCallsClientWithCorrectParameters() =
        runTest {
            val mockClient = blossomMock()
            val fileSlot = slot<File>()
            val contentTypeSlot = slot<String>()
            val urlSlot = slot<String>()

            coEvery {
                mockClient.upload(
                    file = capture(fileSlot),
                    contentType = capture(contentTypeSlot),
                    serverBaseUrl = capture(urlSlot),
                    authHeader = any(),
                )
            } returns
                BlossomUploadResult(
                    url = "https://blossom.example.com/abc123.png",
                    sha256 = "abc123",
                    size = 100,
                )

            val orchestrator = UploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            val img = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
            javax.imageio.ImageIO.write(img, "png", file)

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    orchestrator.upload(
                        file = file,
                        alt = "test upload",
                        serverBaseUrl = "https://blossom.example.com",
                        signer = mockSigner,
                        stripExif = false,
                    )

                coVerify(exactly = 1) {
                    mockClient.upload(
                        file = any(),
                        contentType = any(),
                        serverBaseUrl = any(),
                        authHeader = any(),
                    )
                }

                assertEquals("https://blossom.example.com", urlSlot.captured)
                assertEquals("image/png", contentTypeSlot.captured)
                assertNotNull(result.metadata)
                assertEquals("image/png", result.metadata.mimeType)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadPassesSameFileWhenNoStripExif() =
        runTest {
            val mockClient = blossomMock()
            val fileSlot = slot<File>()

            coEvery {
                mockClient.upload(
                    file = capture(fileSlot),
                    contentType = any(),
                    serverBaseUrl = any(),
                    authHeader = any(),
                )
            } returns BlossomUploadResult(url = "https://example.com/hash")

            val orchestrator = UploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".txt")
            file.deleteOnExit()
            file.writeText("content")

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                orchestrator.upload(
                    file = file,
                    alt = null,
                    serverBaseUrl = "https://example.com",
                    signer = mockSigner,
                    stripExif = false,
                )

                assertEquals(file.absolutePath, fileSlot.captured.absolutePath)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadComputesMetadata() =
        runTest {
            val mockClient = blossomMock()

            coEvery {
                mockClient.upload(any<java.io.File>(), any<String>(), any<String>(), any())
            } returns BlossomUploadResult(url = "https://example.com/hash")

            val orchestrator = UploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".txt")
            file.deleteOnExit()
            file.writeText("hello world")

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    orchestrator.upload(
                        file = file,
                        alt = null,
                        serverBaseUrl = "https://example.com",
                        signer = mockSigner,
                        stripExif = false,
                    )

                assertEquals(11L, result.metadata.size)
                assertTrue(result.metadata.sha256.length == 64)
                assertEquals("application/octet-stream", result.metadata.mimeType)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadPassesAuthHeaderToClient() =
        runTest {
            val mockClient = blossomMock()
            val authSlot = slot<String?>()

            coEvery {
                mockClient.upload(
                    file = any(),
                    contentType = any(),
                    serverBaseUrl = any(),
                    authHeader = captureNullable(authSlot),
                )
            } returns BlossomUploadResult(url = "https://example.com/hash")

            val orchestrator = UploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".txt")
            file.deleteOnExit()
            file.writeText("data")

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                orchestrator.upload(
                    file = file,
                    alt = null,
                    serverBaseUrl = "https://example.com",
                    signer = mockSigner,
                    stripExif = false,
                )

                assertEquals("Nostr fakeAuthToken", authSlot.captured)
            } finally {
                file.delete()
            }
        }

    @Test
    fun encryptedUploadFallsBackToTheNextServerWithTheSameCiphertext() =
        runTest {
            // A server that refuses opaque blobs (415) must not fail the upload while
            // another listed server would take it -- and the retry has to send the very
            // bytes the cipher and hash describe.
            val mockClient = blossomMock()
            val servers = mutableListOf<String>()
            val bodies = mutableListOf<ByteArray>()
            coEvery {
                mockClient.upload(bytes = any(), contentType = any(), serverBaseUrl = any(), authHeader = any())
            } answers {
                val server = arg<String>(2)
                servers += server
                bodies += arg<ByteArray>(0)
                if (server.contains("primal")) {
                    throw IllegalStateException("Unsupported Media Type")
                }
                BlossomUploadResult(url = "$server/blob")
            }

            val file = File.createTempFile("test_", ".bin")
            file.deleteOnExit()
            file.writeBytes(ByteArray(64) { it.toByte() })
            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    UploadOrchestrator(mockClient).uploadEncrypted(
                        file = file,
                        cipher = AESGCM(),
                        serverBaseUrl = "https://blossom.primal.net",
                        signer = mockSigner,
                        fallbackServerBaseUrls = listOf("https://BLOSSOM.PRIMAL.NET/", "https://nostr.download"),
                    )

                assertEquals(listOf("https://blossom.primal.net", "https://nostr.download"), servers)
                assertTrue(bodies[0].contentEquals(bodies[1]))
                assertEquals("https://nostr.download/blob", result.blossom.url)
            } finally {
                file.delete()
            }
        }

    @Test
    fun whenEveryServerFailsTheSelectedServersErrorIsThrown() =
        runTest {
            val mockClient = blossomMock()
            coEvery {
                mockClient.upload(file = any(), contentType = any(), serverBaseUrl = any(), authHeader = any())
            } answers { throw IllegalStateException("refused by ${arg<String>(2)}") }

            val file = File.createTempFile("test_", ".txt")
            file.deleteOnExit()
            file.writeText("content")
            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val error =
                    kotlin
                        .runCatching {
                            UploadOrchestrator(mockClient).upload(
                                file = file,
                                alt = null,
                                serverBaseUrl = "https://a.example",
                                signer = mockSigner,
                                stripExif = false,
                                fallbackServerBaseUrls = listOf("https://b.example"),
                            )
                        }.exceptionOrNull()
                assertEquals("refused by https://a.example", error?.message)
            } finally {
                file.delete()
            }
        }

    private fun png(
        size: Int,
        rgb: Int,
    ): ByteArray {
        val img = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (x in 0 until size) for (y in 0 until size) img.setRGB(x, y, rgb)
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    private fun pngFile(bytes: ByteArray): File = File.createTempFile("test_", ".png").also { it.deleteOnExit() }.apply { writeBytes(bytes) }

    @Test
    fun anUploadThatFallsBackSaysWhereItWentAndWhy() =
        runTest {
            val mockClient = blossomMock()
            coEvery {
                mockClient.upload(file = any(), contentType = any(), serverBaseUrl = any(), authHeader = any())
            } answers {
                val server = arg<String>(2)
                if (server.contains("a.example")) throw IllegalStateException("Payment required")
                BlossomUploadResult(url = "$server/blob")
            }
            val file = pngFile(png(2, 0x112233))
            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    UploadOrchestrator(mockClient).upload(
                        file = file,
                        alt = null,
                        serverBaseUrl = "https://a.example",
                        signer = mockSigner,
                        stripExif = false,
                        fallbackServerBaseUrls = listOf("https://b.example"),
                    )
                assertEquals("https://b.example/blob", result.blossom.url)
                assertEquals(listOf<UploadNotice>(UploadNotice.UploadedToAnotherServer("a.example", "b.example", "Payment required")), result.notices)
            } finally {
                file.delete()
            }
        }

    @Test
    fun anUploadTheChosenServerTookSaysNothing() =
        runTest {
            val mockClient = blossomMock()
            coEvery {
                mockClient.upload(file = any(), contentType = any(), serverBaseUrl = any(), authHeader = any())
            } answers { BlossomUploadResult(url = "${arg<String>(2)}/blob") }
            val file = pngFile(png(2, 0x112233))
            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    UploadOrchestrator(mockClient).upload(
                        file = file,
                        alt = null,
                        serverBaseUrl = "https://a.example",
                        signer = mockSigner,
                        stripExif = false,
                        fallbackServerBaseUrls = listOf("https://b.example"),
                    )
                assertEquals(emptyList(), result.notices)
            } finally {
                file.delete()
            }
        }

    @Test
    fun aServerHoldingAnIdenticalImageLinksToItsCopy() =
        runTest {
            // nostr.download refuses a picture it already holds as another file, and names it. The
            // note links to that copy on the server the user picked, described by its own bytes.
            val copy = png(2, 0x445566)
            val copyHash = sha256(copy).toHexKey()
            val mockClient = blossomMock()
            val tried = mutableListOf<String>()
            coEvery {
                mockClient.upload(file = any(), contentType = any(), serverBaseUrl = any(), authHeader = any())
            } answers {
                val server = arg<String>(2)
                tried += server
                if (server.contains("nostr.download")) throw BlossomIdenticalMediaException(server, copyHash, "An identical image already exists on this server.")
                BlossomUploadResult(url = "$server/blob")
            }
            coEvery { mockClient.download("https://nostr.download/$copyHash") } returns copy
            val file = pngFile(png(2, 0x112233))
            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    UploadOrchestrator(mockClient).upload(
                        file = file,
                        alt = null,
                        serverBaseUrl = "https://nostr.download",
                        signer = mockSigner,
                        stripExif = false,
                        fallbackServerBaseUrls = listOf("https://b.example"),
                    )
                assertEquals(listOf("https://nostr.download"), tried)
                assertEquals("https://nostr.download/$copyHash.png", result.blossom.url)
                assertEquals(copyHash, result.metadata.sha256)
                assertEquals(copy.size.toLong(), result.metadata.size)
                // The picture is on the server the user chose: nothing to tell them.
                assertEquals(emptyList(), result.notices)
            } finally {
                file.delete()
            }
        }

    @Test
    fun anIdenticalCopyOfAnotherSizeIsNotUsed() =
        runTest {
            // The server's word is all that makes the copy "identical"; a copy of another size is
            // not this picture, so the upload moves on to the next server and says so.
            val copy = png(3, 0x445566)
            val copyHash = sha256(copy).toHexKey()
            val mockClient = blossomMock()
            coEvery {
                mockClient.upload(file = any(), contentType = any(), serverBaseUrl = any(), authHeader = any())
            } answers {
                val server = arg<String>(2)
                if (server.contains("nostr.download")) throw BlossomIdenticalMediaException(server, copyHash, "identical")
                BlossomUploadResult(url = "$server/blob")
            }
            coEvery { mockClient.download(any()) } returns copy
            val file = pngFile(png(2, 0x112233))
            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    UploadOrchestrator(mockClient).upload(
                        file = file,
                        alt = null,
                        serverBaseUrl = "https://nostr.download",
                        signer = mockSigner,
                        stripExif = false,
                        fallbackServerBaseUrls = listOf("https://b.example"),
                    )
                assertEquals("https://b.example/blob", result.blossom.url)
                val notice = result.notices.single() as UploadNotice.UploadedToAnotherServer
                assertEquals("nostr.download", notice.chosenServer)
                assertEquals("b.example", notice.usedServer)
            } finally {
                file.delete()
            }
        }

    @Test
    fun anIdenticalCopyThatDoesNotHashToItsNameIsNotUsed() =
        runTest {
            val copy = png(2, 0x445566)
            val mockClient = blossomMock()
            coEvery {
                mockClient.upload(file = any(), contentType = any(), serverBaseUrl = any(), authHeader = any())
            } answers {
                val server = arg<String>(2)
                if (server.contains("nostr.download")) throw BlossomIdenticalMediaException(server, "ab".repeat(32), "identical")
                BlossomUploadResult(url = "$server/blob")
            }
            coEvery { mockClient.download(any()) } returns copy
            val file = pngFile(png(2, 0x112233))
            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    UploadOrchestrator(mockClient).upload(
                        file = file,
                        alt = null,
                        serverBaseUrl = "https://nostr.download",
                        signer = mockSigner,
                        stripExif = false,
                        fallbackServerBaseUrls = listOf("https://b.example"),
                    )
                assertEquals("https://b.example/blob", result.blossom.url)
            } finally {
                file.delete()
            }
        }
}
