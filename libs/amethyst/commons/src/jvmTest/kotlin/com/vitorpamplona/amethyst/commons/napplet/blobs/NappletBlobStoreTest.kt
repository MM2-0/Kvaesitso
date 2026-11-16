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
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletBlobStoreTest {
    private val served = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    private var delayMs = 0L
    private val requests = AtomicInteger()

    // Serves a blob by hash; "chunked" sends it with no Content-Length, as some servers do.
    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                requests.incrementAndGet()
                Thread.sleep(delayMs)
                val name = exchange.requestURI.path.trimStart('/')
                val body = served[name]
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, if (name.startsWith("chunked")) 0 else body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
    private val serverUrl = "http://127.0.0.1:${server.address.port}"
    private val dir = createTempDirectory("blob-store-test").toFile()
    private val cache = NappletBlobCache(dir)

    private fun store(paths: List<PathTag>) = NappletBlobStore(cache, OkHttpClient(), paths, listOf(serverUrl))

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun hashOf(bytes: ByteArray) = sha256(bytes).toHexKey()

    @Test
    fun aMissDownloadsVerifiesAndCaches() =
        runBlocking {
            val page = "<p>hi</p>".encodeToByteArray()
            served[hashOf(page)] = page
            val paths = listOf(PathTag("/index.html", hashOf(page)))

            val store = store(paths)
            val first = store.resolve("/")
            assertIs<StaticSiteResolution.Resolved>(first)
            assertContentEquals(page, first.bytes)
            assertEquals(1, requests.get())

            // The second time comes from the cache, without the network.
            val second = store.resolve("/index.html")
            assertIs<StaticSiteResolution.Resolved>(second)
            assertEquals(NappletBlobStore.CACHE_SERVER, second.server)
            assertEquals(1, requests.get())
        }

    @Test
    fun aWaitingRequestStillGetsTheFileWhenTheFirstGivesUp() =
        runBlocking {
            val page = "<p>shared</p>".encodeToByteArray()
            served[hashOf(page)] = page
            delayMs = 500
            val store = store(listOf(PathTag("/index.html", hashOf(page))))
            val first = launch(Dispatchers.Default) { store.find("/") }
            delay(100)
            val second = async(Dispatchers.Default) { store.find("/") }
            delay(50)
            first.cancel()
            assertContentEquals(page, assertNotNull(second.await()).readBytes())
            assertTrue(cache.has(hashOf(page)), "the download was kept")
            assertEquals(1, requests.get())
        }

    @Test
    fun aSiteCannotSeeWhatAnotherSiteCached() =
        runBlocking {
            // Site Y lists a file of site X. Had the device opened X, a shared cache would answer Y
            // without a request: Y's server would learn this device has seen X.
            val file = "<p>shared lib</p>".encodeToByteArray()
            served[hashOf(file)] = file
            val paths = listOf(PathTag("/index.html", hashOf(file)))

            fun site(partition: String) = NappletBlobStore(cache.forSite(partition), OkHttpClient(), paths, listOf(serverUrl))

            assertNotNull(site(NappletBlobCache.partitionOf("alice", "x", "")).find("/"))
            assertEquals(1, requests.get())
            assertNotNull(site(NappletBlobCache.partitionOf("alice", "y", "")).find("/"))
            assertEquals(2, requests.get(), "another site fetches its own copy")
            assertNotNull(site(NappletBlobCache.partitionOf("bob", "x", "")).find("/"))
            assertEquals(3, requests.get(), "so does the same site for another account")
            assertNotNull(site(NappletBlobCache.partitionOf("alice", "x", "")).find("/"))
            assertEquals(3, requests.get(), "the site itself still gets its cached copy")

            // Trimming covers every site's files.
            cache.trimToSize(0)
            assertNotNull(site(NappletBlobCache.partitionOf("alice", "x", "")).find("/"))
            assertEquals(4, requests.get())
        }

    @Test
    fun aCardThatScrollsAwayStopsItsDownload() =
        runBlocking {
            // A slow server: the request would hold its connection for seconds.
            val page = "<p>slow</p>".encodeToByteArray()
            served[hashOf(page)] = page
            delayMs = 5_000
            val download = launch(Dispatchers.Default) { NappletBlobHttp.downloadCancellable(OkHttpClient(), "$serverUrl/${hashOf(page)}") }
            delay(200)
            val started = System.nanoTime()
            download.cancelAndJoin()
            assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "cancelling ends the call instead of waiting it out")
        }

    @Test
    fun aWriteThatDiedLeavesNoTempFileBehind() {
        val stale = File(dir, "aa.tmp.1").apply { writeBytes(ByteArray(10)) }
        assertTrue(stale.setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000))
        val writing = File(dir, "bb.tmp.2").apply { writeBytes(ByteArray(10)) }
        cache.trimToSize(Long.MAX_VALUE)
        assertFalse(stale.exists(), "an hour-old temp file is cleaned up")
        assertTrue(writing.exists(), "one being written now is left alone")
    }

    @Test
    fun bytesThatDoNotMatchTheirHashAreNeitherServedNorCached() =
        runBlocking {
            val signed = "<p>signed</p>".encodeToByteArray()
            served[hashOf(signed)] = "<p>swapped</p>".encodeToByteArray()
            val paths = listOf(PathTag("/index.html", hashOf(signed)))

            assertIs<StaticSiteResolution.Unresolvable>(store(paths).resolve("/"))
            assertNull(cache.get(hashOf(signed)))
        }

    @Test
    fun aBodyWithNoLengthIsStillCapped() {
        val big = ByteArray((NappletBlobHttp.MAX_BLOB_BYTES + 1).toInt())
        served["chunked-big"] = big
        assertNull(NappletBlobHttp.download(OkHttpClient(), "$serverUrl/chunked-big"))

        val small = "fits".encodeToByteArray()
        served["chunked-small"] = small
        assertContentEquals(small, NappletBlobHttp.download(OkHttpClient(), "$serverUrl/chunked-small"))
    }

    @Test
    fun trimmingDropsTheOldestBlobsFirst() {
        val old = ByteArray(1000) { 1 }
        val new = ByteArray(1000) { 2 }
        cache.put(hashOf(old), old)
        cache.put(hashOf(new), new)
        assertTrue(File(dir, hashOf(old)).setLastModified(1_000))

        cache.trimToSize(1500)
        assertNull(cache.get(hashOf(old)))
        assertContentEquals(new, cache.get(hashOf(new)))
    }

    @Test
    fun theHashComesFromTheUrlsLastSegment() {
        val hash = "a".repeat(64)
        assertEquals(hash, NappletBlobStore.blobHashOf("https://blossom.example/$hash"))
        assertEquals(hash, NappletBlobStore.blobHashOf("https://blossom.example/${hash.uppercase()}.wad"))
    }

    @Test
    fun concurrentMissesShareOneDownload() =
        runBlocking {
            val page = "<p>once</p>".encodeToByteArray()
            served[hashOf(page)] = page
            val store = store(listOf(PathTag("/index.html", hashOf(page))))
            coroutineScope { List(8) { async(Dispatchers.IO) { store.find("/") } }.awaitAll() }.forEach { assertContentEquals(page, it?.readBytes()) }
            assertEquals(1, requests.get(), "eight requests, one download")
        }

    @Test
    fun aCachedBlobStreamsFromItsFile() =
        runBlocking {
            val script = "console.log(1)".encodeToByteArray()
            cache.put(hashOf(script), script)
            val blob = assertNotNull(store(listOf(PathTag("/app.js", hashOf(script)))).find("/app.js"))
            assertEquals(script.size.toLong(), blob.length)
            assertContentEquals(script, blob.open().use { it.readBytes() })
            assertEquals(0, requests.get())
        }

    @Test
    fun usingABlobKeepsItThroughATrim() {
        val old = ByteArray(1000) { 1 }
        val used = ByteArray(1000) { 2 }
        cache.put(hashOf(old), old)
        cache.put(hashOf(used), used)
        assertTrue(File(dir, hashOf(old)).setLastModified(2_000))
        assertTrue(File(dir, hashOf(used)).setLastModified(1_000))
        // Written first, but used just now: the other one goes.
        assertNotNull(cache.file(hashOf(used)))
        cache.trimToSize(1500)
        assertNull(cache.get(hashOf(old)))
        assertContentEquals(used, cache.get(hashOf(used)))
    }
}
