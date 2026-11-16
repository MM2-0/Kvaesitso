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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Request
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * With "videos via Tor" on, the player pool is built with a Tor-proxied [DynamicCallFactory], but a
 * `blossom:` video resolves to the local Blossom cache on 127.0.0.1 only when the data source opens.
 * Tor refuses loopback addresses, so the local cache must be reached directly or no video plays.
 */
class LocalBlossomCacheTorRoutingTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun manager(proxyPort: Int) =
        DualHttpClientManager(
            userAgent = "test",
            proxyPortProvider = MutableStateFlow(proxyPort),
            isMobileDataProvider = MutableStateFlow(false),
            keyCache = EncryptionKeyCache(),
            scope = scope,
            dns = SurgeDns(),
            shouldBridgeBlossomCache = { true },
            onionCache = OnionLocationCache(),
        )

    /** A port nothing listens on: stands in for a SOCKS proxy that cannot reach the target. */
    private fun deadPort(): Int = ServerSocket(0).use { it.localPort }

    /** Minimal one-shot HTTP server standing in for the local Blossom cache. */
    private fun localCache(body: String): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                // Drain the request headers before answering.
                val reader = socket.getInputStream().bufferedReader()
                do {
                    val line = reader.readLine()
                } while (!line.isNullOrEmpty())
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray(),
                )
            }
        }
        return server
    }

    @Test
    fun proxiedFactoryReachesLoopbackCacheDirectly() {
        val cache = localCache("video-bytes")
        val factory = manager(deadPort()).getDynamicCallFactory(useProxy = true)

        val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"
        val request = Request.Builder().url("http://127.0.0.1:${cache.localPort}/$sha.mp4?xs=https://blossom.example.com").build()

        factory.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("video-bytes", response.body.string())
        }
        cache.close()
    }

    @Test
    fun proxyDecisionUsesTheFinalRequestUrl() {
        assertFalse(DynamicCallFactory.shouldUseProxy(true, "http://127.0.0.1:24242/abc.mp4?xs=https://blossom.primal.net"))
        assertFalse(DynamicCallFactory.shouldUseProxy(true, "http://localhost:24242/abc.mp4"))
        assertTrue(DynamicCallFactory.shouldUseProxy(true, "https://blossom.primal.net/abc.mp4"))
        assertFalse(DynamicCallFactory.shouldUseProxy(false, "https://blossom.primal.net/abc.mp4"))
    }

    @Test
    fun onlyTheDirectClientRewritesToTheLocalCache() {
        val manager = manager(deadPort())
        val direct = manager.getHttpClient(useProxy = false)
        val proxied = manager.getHttpClient(useProxy = true)

        // Both carry one (it strips the media marker), but only the direct client's bridges.
        assertEquals(listOf(true), direct.interceptors.filterIsInstance<LocalBlossomCacheRedirectInterceptor>().map { it.bridges })
        assertEquals(listOf(false), proxied.interceptors.filterIsInstance<LocalBlossomCacheRedirectInterceptor>().map { it.bridges })
    }
}
