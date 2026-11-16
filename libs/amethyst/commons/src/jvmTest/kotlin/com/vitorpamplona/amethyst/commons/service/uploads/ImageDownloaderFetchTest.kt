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

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A pasted link is fetched once through the app's client: no retries, and no oversized bodies. */
class ImageDownloaderFetchTest {
    private val image = ByteArray(4096) { it.toByte() }
    private val requests = AtomicInteger()
    private lateinit var server: HttpServer
    private val client = OkHttpClient()

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            if (exchange.requestURI.path == "/cat.png") {
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, image.size.toLong())
                exchange.responseBody.use { it.write(image) }
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun returnsTheBodyAndItsType() {
        val blob = runBlocking { ImageDownloader().fetch(url("/cat.png"), client) }

        assertContentEquals(image, blob?.bytes)
        assertEquals("image/png", blob?.contentType)
    }

    @Test
    fun aMissingFileFailsAtOnce() {
        val blob = runBlocking { ImageDownloader().fetch(url("/missing.png"), client) }

        assertNull(blob)
        assertEquals(1, requests.get())
    }

    @Test
    fun aFileOverTheLimitIsRefused() {
        assertNull(runBlocking { ImageDownloader().fetch(url("/cat.png"), client, maxBytes = 1000) })
    }
}
