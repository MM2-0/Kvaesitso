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
package com.vitorpamplona.amethyst.commons.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.audio.MediaTrackProbeCache
import com.vitorpamplona.amethyst.commons.audio.MediaTracks
import com.vitorpamplona.amethyst.commons.audio.probeMediaTracks
import com.vitorpamplona.amethyst.commons.service.http.SingleRoleBasedHttpClientBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `HEAD` size probe against a real local server: what counts as a size, what is cached, and
 * how the MP4 track probe's own range request feeds the same cache.
 */
class MediaSizeProbeTest {
    private lateinit var server: HttpServer
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    private val clients = SingleRoleBasedHttpClientBuilder(OkHttpClient())

    @BeforeTest
    fun start() {
        MediaSizeCache.clear()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}/$path"

    /** Serves [path], recording "METHOD path" for every request it receives. */
    private fun serve(
        path: String,
        handler: (HttpExchange) -> Unit,
    ) {
        server.createContext("/$path") { exchange ->
            requests.add("${exchange.requestMethod} /$path")
            exchange.use(handler)
        }
    }

    /** A `HEAD` answer with [code] and the given headers, and no body. */
    private fun headAnswer(
        path: String,
        code: Int,
        vararg headers: Pair<String, String>,
    ) = serve(path) { exchange ->
        headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        exchange.sendResponseHeaders(code, -1)
    }

    private fun requestsTo(path: String) = requests.count { it.endsWith(" /$path") }

    @Test
    fun lengthFromHead() =
        runBlocking {
            headAnswer("photo.jpg", 200, "Content-Length" to "245000")
            assertEquals(245_000L, clients.probeMediaSize(url("photo.jpg"), "image/jpeg", MediaSizeRole.IMAGE))
            assertEquals(listOf("HEAD /photo.jpg"), requests.toList())
        }

    @Test
    fun errorPageIsAMiss() =
        runBlocking {
            headAnswer("denied.jpg", 403, "Content-Length" to "5615")
            assertNull(clients.probeMediaSize(url("denied.jpg"), null, MediaSizeRole.IMAGE))
            assertTrue(MediaSizeCache.isAnswered(url("denied.jpg")))
        }

    @Test
    fun noLengthIsAMiss() =
        runBlocking {
            serve("nolength.jpg") { it.sendResponseHeaders(200, 0) }
            assertNull(clients.probeMediaSize(url("nolength.jpg"), null, MediaSizeRole.IMAGE))
        }

    @Test
    fun zeroLengthIsAMiss() =
        runBlocking {
            headAnswer("zero.jpg", 200, "Content-Length" to "0")
            assertNull(clients.probeMediaSize(url("zero.jpg"), null, MediaSizeRole.IMAGE))
        }

    @Test
    fun encodedIsAMiss() =
        runBlocking {
            headAnswer("gz.jpg", 200, "Content-Length" to "900", "Content-Encoding" to "gzip")
            assertNull(clients.probeMediaSize(url("gz.jpg"), null, MediaSizeRole.IMAGE))
        }

    @Test
    fun lengthAboveIntRange() =
        runBlocking {
            headAnswer("long.mp4", 200, "Content-Length" to "5000000000")
            assertEquals(5_000_000_000L, clients.probeMediaSize(url("long.mp4"), "video/mp4", MediaSizeRole.VIDEO))
        }

    @Test
    fun redirectIsFollowed() =
        runBlocking {
            headAnswer("moved.jpg", 302, "Location" to url("cdn.jpg"))
            headAnswer("cdn.jpg", 200, "Content-Length" to "777")
            assertEquals(777L, clients.probeMediaSize(url("moved.jpg"), null, MediaSizeRole.IMAGE))
        }

    @Test
    fun cachedUrlMakesNoRequest() =
        runBlocking {
            headAnswer("twice.jpg", 200, "Content-Length" to "1234")
            clients.probeMediaSize(url("twice.jpg"), null, MediaSizeRole.IMAGE)
            assertEquals(1234L, clients.probeMediaSize(url("twice.jpg"), null, MediaSizeRole.IMAGE))
            assertEquals(1, requestsTo("twice.jpg"))
        }

    @Test
    fun nonHttpMakesNoRequest() =
        runBlocking {
            val uri = "blossom:b21c6e2a4d38f2abac617ac6643aba919f271b37c1c539b5643c17753716c506.jpg"
            assertNull(clients.probeMediaSize(uri, null, MediaSizeRole.IMAGE))
            assertFalse(MediaSizeCache.isAnswered(uri))
        }

    @Test
    fun hlsMakesNoRequest() =
        runBlocking {
            headAnswer("live.m3u8", 200, "Content-Length" to "500")
            assertNull(clients.probeMediaSize(url("live.m3u8"), null, MediaSizeRole.VIDEO))
            assertEquals(0, requestsTo("live.m3u8"))
        }

    @Test
    fun cancelledProbeCachesNothing() =
        runBlocking {
            val arrived = CountDownLatch(1)
            serve("slow.jpg") { exchange ->
                arrived.countDown()
                Thread.sleep(2_000)
                exchange.responseHeaders.add("Content-Length", "100")
                exchange.sendResponseHeaders(200, -1)
            }
            val job = launch(Dispatchers.IO) { clients.probeMediaSize(url("slow.jpg"), null, MediaSizeRole.IMAGE) }
            // Cancel only once the request is really in flight, so the test can't pass without one.
            assertTrue(withContext(Dispatchers.IO) { arrived.await(5, TimeUnit.SECONDS) })
            job.cancelAndJoin()
            assertFalse(MediaSizeCache.isAnswered(url("slow.jpg")))
        }

    @Test
    fun networkFailureIsNotCached() =
        runBlocking {
            // Nothing listens on a stopped server's port: the request fails, which is not the host
            // declining to say. Recording a miss would hide the size until the app restarts.
            val dead = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
            val url = "http://127.0.0.1:${dead.address.port}/offline.jpg"
            dead.stop(0)
            assertNull(clients.probeMediaSize(url, null, MediaSizeRole.IMAGE))
            assertFalse(MediaSizeCache.isAnswered(url))
        }

    @Test
    fun resolveDoesNotWaitWhenNoTrackProbeRuns() =
        runBlocking {
            // A gallery tile's `.mp4`: nothing will read its tracks, so the HEAD must not sit out the full wait.
            headAnswer("tile.mp4", 200, "Content-Length" to "3300000")
            val started = System.nanoTime()
            assertEquals(3_300_000L, clients.resolveMediaSize(url("tile.mp4"), "video/mp4", MediaSizeRole.VIDEO))
            assertTrue(System.nanoTime() - started < 1_000_000_000L)
        }

    @Test
    fun resolveWaitsForRunningTrackProbe() =
        runBlocking {
            serve("feed.mp4") { exchange ->
                Thread.sleep(500)
                val range =
                    exchange.requestHeaders
                        .getFirst("Range")!!
                        .removePrefix("bytes=")
                        .split('-')
                val length = range[1].toInt() - range[0].toInt() + 1
                exchange.responseHeaders.add("Content-Range", "bytes ${range[0]}-${range[1]}/12400000")
                exchange.sendResponseHeaders(206, length.toLong())
                exchange.responseBody.write(ByteArray(length))
            }
            val probe = launch(Dispatchers.IO) { clients.probeMediaTracks(url("feed.mp4"), "video/mp4") }
            assertEquals(12_400_000L, clients.resolveMediaSize(url("feed.mp4"), "video/mp4", MediaSizeRole.VIDEO))
            probe.join()
            assertTrue(requests.none { it.startsWith("HEAD") })
        }

    @Test
    fun trackProbeRecordsContentRange() =
        runBlocking {
            serve("track.mp4") { exchange ->
                val range =
                    exchange.requestHeaders
                        .getFirst("Range")!!
                        .removePrefix("bytes=")
                        .split('-')
                val length = range[1].toInt() - range[0].toInt() + 1
                exchange.responseHeaders.add("Content-Range", "bytes ${range[0]}-${range[1]}/12400000")
                exchange.sendResponseHeaders(206, length.toLong())
                exchange.responseBody.write(ByteArray(length))
            }
            clients.probeMediaTracks(url("track.mp4"), "video/mp4")
            assertEquals(12_400_000L, MediaSizeCache.get(url("track.mp4")))
        }

    @Test
    fun resolveWaitsThenFallsBackToHead() =
        runBlocking {
            headAnswer("gallery.mp4", 200, "Content-Length" to "8800000")
            assertEquals(8_800_000L, clients.resolveMediaSize(url("gallery.mp4"), "video/mp4", MediaSizeRole.VIDEO, trackProbeWaitMs = 100))
            assertEquals(1, requestsTo("gallery.mp4"))
        }

    @Test
    fun resolveUsesTrackProbeSize() =
        runBlocking {
            MediaTrackProbeCache.put(url("probed.mp4"), MediaTracks.HAS_VIDEO)
            MediaSizeCache.put(url("probed.mp4"), 999L)
            assertEquals(999L, clients.resolveMediaSize(url("probed.mp4"), "video/mp4", MediaSizeRole.VIDEO))
            assertEquals(0, requestsTo("probed.mp4"))
        }

    @Test
    fun resolveSkipsWaitForNonMp4() =
        runBlocking {
            headAnswer("still.jpg", 200, "Content-Length" to "4200")
            val started = System.nanoTime()
            assertEquals(4_200L, clients.resolveMediaSize(url("still.jpg"), "image/jpeg", MediaSizeRole.IMAGE, trackProbeWaitMs = 10_000))
            assertTrue(System.nanoTime() - started < 5_000_000_000L)
        }
}
