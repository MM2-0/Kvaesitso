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

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OkHttp writes a request body again when it retries. These drive the real OkHttp retry paths
 * against a socket server: a body that reused one stream failed them with "Stream Closed".
 */
class ReopenableStreamBodyTest {
    private val size = 1_000_000
    private val file = File.createTempFile("upload", ".bin").apply { writeBytes(ByteArray(size) { it.toByte() }) }
    private val server = ServerSocket(0)
    private val base = "http://127.0.0.1:${server.localPort}"
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()

    @AfterTest
    fun tearDown() {
        server.close()
        if (!file.delete() && file.exists()) file.deleteOnExit()
    }

    /** Reads one request and returns how many body bytes arrived. */
    private fun readRequest(socket: Socket): Long {
        val input = socket.getInputStream()
        val headers = StringBuilder()
        while (!headers.endsWith("\r\n\r\n")) {
            val c = input.read()
            if (c < 0) return -1
            headers.append(c.toChar())
        }
        val length =
            Regex("Content-Length: (\\d+)", RegexOption.IGNORE_CASE)
                .find(headers)
                ?.groupValues
                ?.get(1)
                ?.toLong() ?: 0
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (total < length) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun Socket.respond(status: String) {
        getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Length: 2\r\n\r\n{}".toByteArray())
            flush()
        }
    }

    private fun upload(onOpen: () -> Unit): Int {
        val body =
            ReopenableStreamBody("application/octet-stream".toMediaType(), file.length()) {
                onOpen()
                file.inputStream()
            }
        val request =
            Request
                .Builder()
                .url("$base/upload")
                .put(body)
                .build()
        return client.newCall(request).execute().use { it.code }
    }

    @Test
    fun retriesOnAStalePooledConnection() {
        val retryReceived = AtomicLong()
        thread {
            // A keep-alive connection the server drops while idle, as a NAT or load balancer does.
            server.accept().use { socket ->
                readRequest(socket)
                socket.respond("200 OK")
                Thread.sleep(200)
                socket.setSoLinger(true, 0)
            }
            server.accept().use { socket ->
                retryReceived.set(readRequest(socket))
                socket.respond("200 OK")
            }
        }
        client.newCall(Request.Builder().url("$base/").build()).execute().use { it.body.string() }
        Thread.sleep(500)

        var opens = 0
        assertEquals(200, upload { opens++ })
        assertEquals(2, opens)
        assertEquals(size.toLong(), retryReceived.get())
    }

    @Test
    fun retriesAfterA408() {
        val retryReceived = AtomicLong()
        thread {
            server.accept().use { socket ->
                readRequest(socket)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 408 Request Timeout\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    flush()
                }
                retryReceived.set(readRequest(socket))
                socket.respond("200 OK")
            }
        }

        var opens = 0
        assertEquals(200, upload { opens++ })
        assertEquals(2, opens)
        assertEquals(size.toLong(), retryReceived.get())
    }

    @Test
    fun progressRestartsOnEachAttempt() {
        thread {
            server.accept().use { socket ->
                readRequest(socket)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 408 Request Timeout\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    flush()
                }
                readRequest(socket)
                socket.respond("200 OK")
            }
        }
        val reports = mutableListOf<Long>()
        val body = ReopenableStreamBody(null, file.length(), { written, _ -> reports.add(written) }) { file.inputStream() }
        client
            .newCall(
                Request
                    .Builder()
                    .url("$base/upload")
                    .put(body)
                    .build(),
            ).execute()
            .use { assertEquals(200, it.code) }

        val restarts = reports.zipWithNext().count { (a, b) -> b < a }
        assertEquals(1, restarts)
        assertEquals(size.toLong(), reports.last())
    }
}
