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
package com.vitorpamplona.amethyst.commons.service.uploads.nip96

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.quartz.nip96FileStorage.info.ServerInfo
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A NIP-96 server that processes the upload: a processing URL that never finishes must not hang it. */
class Nip96UploaderProcessingTest {
    private val polls = AtomicInteger()
    private lateinit var server: HttpServer

    /** What the processing URL answers on each poll, by poll number (1-based). */
    private var processing: (poll: Int) -> Pair<Int, String> = { 500 to "" }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun HttpExchange.reply(
        code: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) } else close()
    }

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            exchange.reply(202, """{"status":"processing","processing_url":"${url("/proc")}","percentage":0}""")
        }
        server.createContext("/proc") { exchange ->
            val (code, body) = processing(polls.incrementAndGet())
            exchange.reply(code, body)
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun upload() =
        runBlocking {
            Nip96Uploader(pollIntervalMs = 1).upload(
                openStream = { ByteArray(1000).inputStream() },
                length = 1000,
                contentType = "image/png",
                alt = null,
                sensitiveContent = null,
                server = ServerInfo(apiUrl = url("/api")),
                okHttpClient = { OkHttpClient() },
                onProgress = {},
                httpAuth = { _, _, _ -> null },
            )
        }

    @Test
    fun givesUpOnAProcessingUrlThatKeepsFailing() {
        processing = { 500 to "" }

        assertFailsWith<RuntimeException> { upload() }
        assertEquals(Nip96Uploader.MAX_FAILED_POLLS, polls.get())
    }

    @Test
    fun givesUpOnProcessingThatNeverFinishes() {
        processing = { 200 to """{"status":"processing","percentage":50}""" }

        assertFailsWith<RuntimeException> { upload() }
        assertEquals(Nip96Uploader.MAX_PROCESSING_POLLS, polls.get())
    }

    @Test
    fun ridesOutAFewFailedPolls() {
        processing = { poll ->
            if (poll <= 3) {
                500 to ""
            } else {
                200 to """{"status":"success","percentage":100,"nip94_event":{"tags":[["url","https://example.com/a.png"],["x","abc"]]}}"""
            }
        }

        val result = upload()

        assertEquals("https://example.com/a.png", result.url)
        assertEquals(4, polls.get())
    }
}
