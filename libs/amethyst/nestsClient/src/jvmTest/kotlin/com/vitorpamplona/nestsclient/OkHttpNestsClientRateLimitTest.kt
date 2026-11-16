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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Backoff math + retry-loop coverage for [OkHttpNestsClient]. The
 * production case is the nostrnests `moq-auth` sidecar's 20/min/IP
 * limiter — when the all-tests-together interop run blasts more than
 * 20 mintToken calls in <60 s, every test class after the 20th hit
 * cascades on `429`. The retry-with-backoff added here lets the
 * client wait out the 60 s window instead of bubbling up a hard
 * failure.
 */
class OkHttpNestsClientRateLimitTest {
    @Test
    fun computes_backoff_from_retry_after_seconds_header() {
        // Plain integer = delta-seconds per RFC 7231 §7.1.3.
        assertEquals(2_000L, computeRateLimitBackoffMs("2", attempt = 0))
        assertEquals(2_000L, computeRateLimitBackoffMs(" 2 ", attempt = 0))
        // Negative → 0 (don't sleep backwards).
        assertEquals(0L, computeRateLimitBackoffMs("-5", attempt = 0))
    }

    @Test
    fun computes_backoff_from_retry_after_http_date_header() {
        // 30 s from `now`. RFC_1123 format = the HTTP-date wire format.
        // 1_700_000_000_000 ms = 2023-11-14T22:13:20Z (Tuesday).
        val now = 1_700_000_000_000L
        val target = "Tue, 14 Nov 2023 22:13:50 GMT"
        assertEquals(
            30_000L,
            computeRateLimitBackoffMs(target, attempt = 0, nowMs = now),
        )
    }

    @Test
    fun falls_back_to_exponential_backoff_when_header_absent() {
        assertEquals(1_000L, computeRateLimitBackoffMs(null, attempt = 0))
        assertEquals(2_000L, computeRateLimitBackoffMs(null, attempt = 1))
        assertEquals(4_000L, computeRateLimitBackoffMs(null, attempt = 2))
        assertEquals(8_000L, computeRateLimitBackoffMs(null, attempt = 3))
        assertEquals(16_000L, computeRateLimitBackoffMs(null, attempt = 4))
        // Capped — further attempts stay at MAX_BACKOFF_MS.
        assertEquals(16_000L, computeRateLimitBackoffMs(null, attempt = 5))
        assertEquals(16_000L, computeRateLimitBackoffMs(null, attempt = 10))
    }

    @Test
    fun unparseable_retry_after_falls_back_to_exponential_backoff() {
        // Garbage header → ignore + use exponential.
        assertEquals(1_000L, computeRateLimitBackoffMs("garbage", attempt = 0))
        assertEquals(2_000L, computeRateLimitBackoffMs("garbage", attempt = 1))
    }

    @Test
    fun mint_token_retries_through_429_and_returns_token() =
        runBlocking {
            val server = TinyHttpServer()
            // First 2 responses = 429 with Retry-After: 0, 3rd = 200 with token.
            server.enqueue(rateLimited(retryAfterSeconds = 0))
            server.enqueue(rateLimited(retryAfterSeconds = 0))
            server.enqueue(success(token = "T1"))

            try {
                val client = OkHttpNestsClient(httpClient = { OkHttpClient() })
                val token =
                    client.mintToken(
                        room = roomConfig(server.baseUrl),
                        publish = false,
                        signer = NostrSignerInternal(KeyPair()),
                    )
                assertEquals("T1", token)
                assertEquals(3, server.requestCount(), "expected 2 retries before the 200")
            } finally {
                server.close()
            }
        }

    @Test
    fun mint_token_gives_up_after_max_rate_limit_retries() =
        runBlocking {
            val server = TinyHttpServer()
            // 6 = MAX_RATE_LIMIT_RETRIES + 1 → expect retries to exhaust on
            // the (MAX_RATE_LIMIT_RETRIES+1)-th 429 and surface a NestsException.
            repeat(MAX_RATE_LIMIT_RETRIES + 2) { server.enqueue(rateLimited(retryAfterSeconds = 0)) }

            try {
                val client = OkHttpNestsClient(httpClient = { OkHttpClient() })
                val ex =
                    assertFailsWith<NestsException> {
                        client.mintToken(
                            room = roomConfig(server.baseUrl),
                            publish = false,
                            signer = NostrSignerInternal(KeyPair()),
                        )
                    }
                assertEquals(429, ex.status)
                assertEquals(
                    MAX_RATE_LIMIT_RETRIES + 1,
                    server.requestCount(),
                    "expected one initial attempt + MAX_RATE_LIMIT_RETRIES retries",
                )
            } finally {
                server.close()
            }
        }

    @Test
    fun non_429_4xx_is_not_retried() =
        runBlocking {
            val server = TinyHttpServer()
            server.enqueue(StubResponse(401, "Unauthorized", body = "{\"error\":\"bad sig\"}"))

            try {
                val client = OkHttpNestsClient(httpClient = { OkHttpClient() })
                val ex =
                    assertFailsWith<NestsException> {
                        client.mintToken(
                            room = roomConfig(server.baseUrl),
                            publish = false,
                            signer = NostrSignerInternal(KeyPair()),
                        )
                    }
                assertEquals(401, ex.status)
                assertEquals(1, server.requestCount(), "401 must not trigger retry")
                assertTrue(ex.message?.contains("bad sig") == true)
            } finally {
                server.close()
            }
        }

    private fun roomConfig(baseUrl: String): NestsRoomConfig =
        NestsRoomConfig(
            authBaseUrl = baseUrl,
            endpoint = "https://example.invalid:4443/",
            hostPubkey = "0".repeat(64),
            roomId = "ratelimit-${System.nanoTime()}",
        )

    private fun rateLimited(retryAfterSeconds: Int): StubResponse =
        StubResponse(
            status = 429,
            statusText = "Too Many Requests",
            body = "{\"error\":\"Too many requests, try again later\"}",
            headers = mapOf("Retry-After" to retryAfterSeconds.toString()),
        )

    private fun success(token: String): StubResponse =
        StubResponse(
            status = 200,
            statusText = "OK",
            body = "{\"token\":\"$token\",\"url\":\"https://example.invalid:4443/\"}",
        )
}

/**
 * Single canned HTTP response — the server pops one of these per
 * incoming connection.
 */
private data class StubResponse(
    val status: Int,
    val statusText: String,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Tiny single-threaded HTTP/1.1 server backed by [ServerSocket]. Pops
 * one [StubResponse] per request from the FIFO queue. Drains the
 * request body so OkHttp's pool sees a clean keepalive frame; closes
 * the connection after each response so the next request reads from
 * a clean Socket. Sufficient for unit testing OkHttp's retry path
 * without pulling MockWebServer in as a dep.
 */
private class TinyHttpServer : AutoCloseable {
    private val socket = ServerSocket(0)
    private val responses = ArrayDeque<StubResponse>()
    private val lock = ReentrantLock()
    private val seen = AtomicInteger(0)
    private val running =
        java.util.concurrent.atomic
            .AtomicBoolean(true)
    private val acceptor =
        thread(name = "TinyHttpServer-acceptor", isDaemon = true) {
            while (running.get()) {
                val client =
                    try {
                        socket.accept()
                    } catch (_: IOException) {
                        return@thread
                    }
                handle(client)
            }
        }

    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

    fun enqueue(response: StubResponse) {
        lock.withLock { responses.addLast(response) }
    }

    fun requestCount(): Int = seen.get()

    private fun handle(client: Socket) {
        client.use { sock ->
            val input = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1))
            // Request line.
            input.readLine() ?: return
            // Drain headers, capture Content-Length.
            var contentLength = 0
            while (true) {
                val line = input.readLine() ?: return
                if (line.isEmpty()) break
                val parts = line.split(":", limit = 2)
                if (parts.size == 2 && parts[0].equals("Content-Length", ignoreCase = true)) {
                    contentLength = parts[1].trim().toIntOrNull() ?: 0
                }
            }
            // Drain body so the request is fully consumed before we reply.
            if (contentLength > 0) {
                val drained = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(drained, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
            }
            seen.incrementAndGet()

            val response =
                lock.withLock { responses.removeFirstOrNull() }
                    ?: StubResponse(500, "Internal Server Error", "no response queued")

            writeResponse(sock.getOutputStream(), response)
        }
    }

    private fun writeResponse(
        out: OutputStream,
        response: StubResponse,
    ) {
        val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
        val headerLines =
            buildString {
                append("HTTP/1.1 ")
                    .append(response.status)
                    .append(' ')
                    .append(response.statusText)
                    .append("\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ").append(bodyBytes.size).append("\r\n")
                append("Connection: close\r\n")
                response.headers.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                append("\r\n")
            }
        out.write(headerLines.toByteArray(Charsets.ISO_8859_1))
        out.write(bodyBytes)
        out.flush()
    }

    override fun close() {
        running.set(false)
        runCatching { socket.close() }
        runCatching { acceptor.join(1_000) }
    }
}
