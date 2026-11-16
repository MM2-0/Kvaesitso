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
package com.vitorpamplona.amethyst.commons.cartridges

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.service.http.EncryptedBlobInterceptor
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.SingleRoleBasedHttpClientBuilder
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A cart sent as an encrypted chat attachment (a NIP-17 file message, Marmot or Cordn media) is
 * stored as ciphertext. The chat view caches its key, and the image client's
 * [EncryptedBlobInterceptor] must hand the cart code plaintext: the PNG header for the Play
 * button's probe, even though that probe asks for a byte range the ciphertext can't serve, and the
 * whole file for the player.
 */
class EncryptedCartDownloadTest {
    private val plaintext = cartShapedPng()
    private val cipher = AESGCM()
    private val ciphertext = cipher.encrypt(plaintext)
    private lateinit var server: HttpServer

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            // Honors Range like a real blob server, so a probe whose Range survived would get a
            // slice of ciphertext, which can't be decrypted.
            val range =
                exchange.requestHeaders
                    .getFirst("Range")
                    ?.removePrefix("bytes=")
                    ?.split('-')
            val body =
                if (range != null) {
                    ciphertext.copyOfRange(range[0].toInt(), minOf(range[1].toInt() + 1, ciphertext.size))
                } else {
                    ciphertext
                }
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(if (range != null) 206 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun url(name: String) = "http://127.0.0.1:${server.address.port}/$name"

    private fun clients(keys: EncryptionKeyCache) = SingleRoleBasedHttpClientBuilder(OkHttpClient.Builder().addNetworkInterceptor(EncryptedBlobInterceptor(keys)).build())

    @Test
    fun probeAndPlayerReadTheDecryptedCart() =
        runBlocking {
            val url = url("dm-attachment")
            val keys = EncryptionKeyCache().apply { add(url, cipher, "image/png") }
            val clients = clients(keys)

            assertTrue(CartDetector.hasCartShape(url, dim = null, httpClients = clients))
            assertContentEquals(plaintext, clients.fetchMedia(url, maxBytes = 1 shl 20))
        }

    // A `blossom:` attachment is downloaded from the server it resolves to, and its hash names the
    // ciphertext; the key cached under the URI must still decrypt it.
    @Test
    fun blossomAttachmentIsDecryptedFromTheResolvedServer() =
        runBlocking {
            val blobHash = sha256(ciphertext).toHexKey()
            val keys = EncryptionKeyCache().apply { add("blossom:$blobHash.png", cipher, "image/png") }
            val clients = clients(keys)
            val serverUrl = url("$blobHash.png")

            assertTrue(CartDetector.hasCartShape(serverUrl, dim = null, httpClients = clients))
            assertContentEquals(plaintext, clients.fetchMedia(serverUrl, maxBytes = 1 shl 20))
        }

    @Test
    fun withoutTheKeyTheCiphertextIsNotACart() =
        runBlocking {
            val url = url("unknown-key")
            val clients = clients(EncryptionKeyCache())

            assertFalse(CartDetector.hasCartShape(url, dim = null, httpClients = clients))
            assertEquals(ciphertext.size, clients.fetchMedia(url, maxBytes = 1 shl 20)?.size)
        }

    private fun cartShapedPng(): ByteArray {
        val image = BufferedImage(160, 205, BufferedImage.TYPE_INT_ARGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
