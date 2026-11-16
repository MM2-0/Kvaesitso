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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A card prefetches because it scrolled by: a stranger's manifest must not decide how much it costs. */
class NappletBlobPrefetcherTest {
    private val served = ConcurrentHashMap<String, ByteArray>()
    private val requests = AtomicInteger()

    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                requests.incrementAndGet()
                val body = served[exchange.requestURI.path.trimStart('/')]
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    // No Content-Length: the cap must hold while reading, not from a header.
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
    private val serverUrl = "http://127.0.0.1:${server.address.port}"
    private val dir = createTempDirectory("prefetch-test").toFile()
    private val cache = NappletBlobCache(dir).forSite("site")

    @BeforeTest
    fun setUp() = NappletBlobPrefetcher.resetBudget()

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
        NappletBlobPrefetcher.resetBudget()
    }

    private fun blob(size: Int): PathTag {
        val bytes = Random.nextBytes(size)
        val hash = sha256(bytes).toHexKey()
        served[hash] = bytes
        return PathTag("/f-$hash", hash)
    }

    @Test
    fun aCardWarmsOnlyTheStartOfASite() =
        runBlocking {
            val index = blob(100).let { PathTag("/index.html", it.hash) }
            val paths = List(300) { blob(100) } + index

            NappletBlobPrefetcher.prefetchWith(paths, listOf(serverUrl), cache, OkHttpClient(), "site")

            assertEquals(NappletBlobPrefetcher.MAX_FILES_PER_CARD, requests.get())
            assertTrue(cache.has(index.hash), "the entry document comes first")
        }

    @Test
    fun aCardStopsPastItsByteBudget() =
        runBlocking {
            val paths = List(NappletBlobPrefetcher.MAX_FILES_PER_CARD) { blob(3 * 1024 * 1024) }

            NappletBlobPrefetcher.prefetchWith(paths, listOf(serverUrl), cache, OkHttpClient(), "site")

            val stored = paths.count { cache.has(it.hash) }
            assertTrue(stored * 3L * 1024 * 1024 <= NappletBlobPrefetcher.MAX_CARD_BYTES + 5 * 3L * 1024 * 1024, "stored $stored files of 3 MB")
            assertTrue(stored < paths.size)
        }

    // A server that answers every hash with junk costs as much as one that answers right: what came
    // in counts against the budget whether or not it verified.
    @Test
    fun junkThatFailsItsHashStillSpendsTheBudget() =
        runBlocking {
            val paths =
                List(NappletBlobPrefetcher.MAX_FILES_PER_CARD) {
                    val hash = sha256(Random.nextBytes(32)).toHexKey()
                    served[hash] = Random.nextBytes(3 * 1024 * 1024)
                    PathTag("/f-$hash", hash)
                }

            NappletBlobPrefetcher.prefetchWith(paths, listOf(serverUrl), cache, OkHttpClient(), "site")

            assertTrue(paths.none { cache.has(it.hash) })
            assertTrue(requests.get() * 3L * 1024 * 1024 <= NappletBlobPrefetcher.MAX_CARD_BYTES + 5 * 3L * 1024 * 1024, "fetched ${requests.get()} junk files of 3 MB")
            assertTrue(requests.get() < paths.size, "the budget stopped it: ${requests.get()} of ${paths.size}")
        }

    @Test
    fun aFileTooBigForAPrefetchIsLeftForTheLaunch() =
        runBlocking {
            val big = blob((NappletBlobPrefetcher.MAX_PREFETCH_BLOB_BYTES + 1).toInt())

            NappletBlobPrefetcher.prefetchWith(listOf(big), listOf(serverUrl), cache, OkHttpClient(), "site")

            assertTrue(!cache.has(big.hash))
        }

    @Test
    fun manyCardsShareOneBudget() =
        runBlocking {
            var downloaded = 0L
            repeat(40) { card ->
                val paths = List(4) { blob(1024 * 1024) }
                NappletBlobPrefetcher.prefetchWith(paths, listOf(serverUrl), NappletBlobCache(dir).forSite("site-$card"), OkHttpClient(), "site-$card")
                downloaded += paths.count { NappletBlobCache(dir).forSite("site-$card").has(it.hash) } * 1024L * 1024
            }

            assertTrue(downloaded <= NappletBlobPrefetcher.MAX_WINDOW_BYTES + 5L * 1024 * 1024, "downloaded $downloaded bytes")
        }

    @Test
    fun onlyHttpsServersAndOnlyAFew() {
        val servers = listOf("http://192.168.1.1/admin?x=", "https://a.example", "ftp://b", "https://b.example", "https://c.example", "https://d.example", "https://e.example")

        assertEquals(listOf("https://a.example", "https://b.example", "https://c.example", "https://d.example"), NappletBlobPrefetcher.prefetchServers(servers))
    }

    // A server named in a stranger's note must not make the app reach the user's own network.
    @Test
    fun aPrefetchClientRefusesPrivateAddresses() {
        val page = blob(10)

        assertNull(NappletBlobHttp.download(NappletBlobHttp.client(0, publicOnly = true), "$serverUrl/${page.hash}"))
        assertTrue(NappletBlobHttp.download(NappletBlobHttp.client(0), "$serverUrl/${page.hash}") != null)
    }
}
