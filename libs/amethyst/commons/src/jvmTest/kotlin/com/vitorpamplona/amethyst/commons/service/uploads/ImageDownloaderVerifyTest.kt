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
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The download that checks a fresh upload: it waits for a server that is not serving the file yet,
 * but a server that stalls or a user who cancels must not hold the upload open.
 */
class ImageDownloaderVerifyTest {
    private val blob = ByteArray(200_000) { (it * 31).toByte() }
    private val requests = AtomicInteger()
    private val release = CountDownLatch(1)
    private lateinit var server: HttpServer

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            val count = requests.incrementAndGet()
            if (exchange.requestMethod == "HEAD") {
                when (exchange.requestURI.path) {
                    "/blob", "/later" -> {
                        if (exchange.requestURI.path == "/later" && count <= 2) {
                            exchange.sendResponseHeaders(404, -1)
                        } else {
                            exchange.responseHeaders.add("Content-Type", "video/mp4")
                            exchange.responseHeaders.add("Content-Length", blob.size.toString())
                            exchange.sendResponseHeaders(200, -1)
                        }
                    }

                    "/no-head" -> {
                        exchange.sendResponseHeaders(405, -1)
                    }

                    else -> {
                        exchange.sendResponseHeaders(404, -1)
                    }
                }
                exchange.close()
                return@createContext
            }
            when (exchange.requestURI.path) {
                "/blob" -> {
                    exchange.responseHeaders.add("Content-Type", "video/mp4")
                    exchange.sendResponseHeaders(200, blob.size.toLong())
                    exchange.responseBody.use { it.write(blob) }
                }

                // Answers 404 twice, as a server that has not published the upload yet.
                "/later" -> {
                    if (count <= 2) {
                        exchange.sendResponseHeaders(404, -1)
                        exchange.close()
                    } else {
                        exchange.sendResponseHeaders(200, blob.size.toLong())
                        exchange.responseBody.use { it.write(blob) }
                    }
                }

                "/moved" -> {
                    exchange.responseHeaders.add("Location", url("/blob"))
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                }

                "/bad-request" -> {
                    exchange.sendResponseHeaders(400, -1)
                    exchange.close()
                }

                // Sends the headers and a first chunk, then nothing until the test ends.
                "/stall" -> {
                    exchange.sendResponseHeaders(200, blob.size.toLong())
                    exchange.responseBody.write(blob, 0, 1000)
                    exchange.responseBody.flush()
                    release.await(30, TimeUnit.SECONDS)
                    exchange.close()
                }
            }
        }
        server.start()
    }

    @AfterTest
    fun stop() {
        release.countDown()
        server.stop(0)
    }

    private val client = OkHttpClient()

    @Test
    fun hashesWhatTheServerServes() {
        val verification = runBlocking { ImageDownloader().waitAndVerifyStream(url("/blob")) { client } }

        assertEquals(sha256(blob).toHexKey(), verification?.hash)
        assertEquals(blob.size.toLong(), verification?.size)
        assertEquals("video/mp4", verification?.contentType)
    }

    @Test
    fun waitsForAServerThatIsNotServingTheFileYet() {
        val verification = runBlocking { ImageDownloader(maxAttempts = 5, retryDelayMs = 10).waitAndVerifyStream(url("/later")) { client } }

        assertEquals(sha256(blob).toHexKey(), verification?.hash)
        assertEquals(3, requests.get())
    }

    @Test
    fun followsRedirects() {
        val verification = runBlocking { ImageDownloader().waitAndVerifyStream(url("/moved")) { client } }

        assertEquals(sha256(blob).toHexKey(), verification?.hash)
    }

    @Test
    fun aClientErrorIsNotARedirect() {
        val verification = runBlocking { ImageDownloader(maxAttempts = 3, retryDelayMs = 10).waitAndVerifyStream(url("/bad-request")) { client } }

        assertNull(verification)
        assertEquals(3, requests.get())
    }

    @Test
    fun aStalledDownloadEndsAtTheReadTimeout() {
        val impatient = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        val started = System.nanoTime()

        val verification = runBlocking { ImageDownloader(maxAttempts = 2, retryDelayMs = 10).waitAndVerifyStream(url("/stall")) { impatient } }

        assertNull(verification)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5_000)
    }

    @Test
    fun aHeadRequestConfirmsTheFileIsServedWithoutDownloadingIt() {
        val check = runBlocking { ImageDownloader().waitUntilServed(url("/blob")) { client } }

        val served = assertIs<ImageDownloader.ServedCheck.Served>(check)
        assertEquals(blob.size.toLong(), served.size)
        assertEquals("video/mp4", served.contentType)
        assertEquals(1, requests.get())
    }

    @Test
    fun aHeadRequestWaitsForAServerThatIsNotServingTheFileYet() {
        val check = runBlocking { ImageDownloader(maxAttempts = 5, retryDelayMs = 10).waitUntilServed(url("/later")) { client } }

        assertIs<ImageDownloader.ServedCheck.Served>(check)
        assertEquals(3, requests.get())
    }

    @Test
    fun aServerWithoutHeadIsReportedSoTheCallerCanDownloadInstead() {
        val check = runBlocking { ImageDownloader(maxAttempts = 5, retryDelayMs = 10).waitUntilServed(url("/no-head")) { client } }

        assertEquals(ImageDownloader.ServedCheck.HeadNotSupported, check)
        assertEquals(1, requests.get())
    }

    @Test
    fun aFileThatIsNeverServedIsNull() {
        val check = runBlocking { ImageDownloader(maxAttempts = 2, retryDelayMs = 10).waitUntilServed(url("/missing")) { client } }

        assertNull(check)
    }

    @Test
    fun cancellingStopsABlockedRead() {
        val patient = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

        runBlocking {
            val download =
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    ImageDownloader().waitAndVerifyStream(url("/stall")) { patient }
                }
            while (requests.get() == 0) Thread.sleep(10)
            Thread.sleep(200)

            // Without closing the socket, the read would sit there for the whole 60 s timeout.
            withTimeout(5_000) {
                download.cancel()
                download.join()
            }
            assertTrue(download.isCancelled)
        }
    }
}
