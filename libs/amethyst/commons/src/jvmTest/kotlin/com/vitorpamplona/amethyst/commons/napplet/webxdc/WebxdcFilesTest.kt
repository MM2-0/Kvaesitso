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
package com.vitorpamplona.amethyst.commons.napplet.webxdc

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class WebxdcFilesTest {
    @get:Rule val folder = TemporaryFolder()

    private val client = OkHttpClient()
    private val served = HashMap<String, ByteArray>()
    private var requests = 0
    private val http =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests++
                val body = served[exchange.requestURI.path]
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
    private val base = "http://127.0.0.1:${http.address.port}"

    @After fun stop() = http.stop(0)

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun files(
        url: String,
        hash: String?,
    ) = WebxdcFiles(WebxdcBundles(folder.newFolder()), client, url, hash, emptyList(), WebxdcRuntime.script("npub1me", "Me", isAppSender = false))

    @Test
    fun anAppIsServedFromItsArchiveWithTheMessengersRuntime() =
        runBlocking {
            val archive = zip("index.html" to "<script src=\"webxdc.js\"></script><p>game</p>", "manifest.toml" to "name = \"Game\"", "js/app.js" to "go()")
            served["/game.xdc"] = archive
            val files = files("$base/game.xdc", sha256(archive).toHexKey())

            assertTrue(files.isAvailable("/"))
            assertEquals("<script src=\"webxdc.js\"></script><p>game</p>", files.find("/index.html")?.readBytes()?.decodeToString())
            assertEquals("go()", files.find("/js/app.js")?.readBytes()?.decodeToString())
            assertTrue(files.find("/js/app.js")!!.contentType.contains("javascript"))
            val runtime = files.find("/webxdc.js")!!.readBytes().decodeToString()
            assertTrue(runtime.contains("window.webxdc") && runtime.contains("\"npub1me\""))
            assertNull(files.find("/missing.js"))
            assertNull("no way out of the archive", files.find("/../../etc/passwd"))
            assertNull(files.find("/%2e%2e/%2e%2e/etc/passwd"))
            // One download for every request after the first.
            assertEquals(1, requests)
        }

    @Test
    fun theDownloadReportsItsProgressAndClearsWhenDone() =
        runBlocking {
            // Stored, not deflated, and random: the archive is as large as its contents.
            val noise = Random(7).nextBytes(1_500_000)
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.setMethod(ZipOutputStream.STORED)
                zip.putNextEntry(
                    ZipEntry("index.html").apply {
                        size = noise.size.toLong()
                        crc = CRC32().apply { update(noise) }.value
                    },
                )
                zip.write(noise)
                zip.closeEntry()
            }
            val archive = out.toByteArray()
            served["/big.xdc"] = archive
            val files = files("$base/big.xdc", sha256(archive).toHexKey())

            val seen = mutableListOf<NappletDownloadProgress>()
            val watch = launch(start = CoroutineStart.UNDISPATCHED) { files.download.filterNotNull().collect { seen += it } }
            assertTrue(files.isAvailable("/"))
            watch.cancel()

            assertTrue("the bar moved: $seen", seen.isNotEmpty())
            assertTrue(seen.all { it.total == archive.size.toLong() })
            assertEquals(seen.map { it.bytes }.sorted(), seen.map { it.bytes })
            assertNull("nothing is downloading any more", files.download.value)
        }

    @Test
    fun progressReadsAsSizesWithoutWords() {
        assertEquals("2.4 MB / 5.8 MB", NappletDownloadProgress(2_400_000, 5_825_997).label())
        assertEquals("512 KB", NappletDownloadProgress(512_000, null).label())
        assertEquals(0.5f, NappletDownloadProgress(50, 100).fraction)
        assertNull(NappletDownloadProgress(50, null).fraction)
    }

    @Test
    fun aWrongArchiveIsRefusedAndNothingIsKept() =
        runBlocking {
            served["/game.xdc"] = zip("index.html" to "<p>swapped</p>")
            val files = files("$base/game.xdc", "ab".repeat(32))
            assertFalse(files.isAvailable("/"))
            assertNull(files.find("/index.html"))
        }

    // Every app the user starts can unpack to the archive cap, and on desktop this folder is kept for
    // good: the apps opened longest ago go once they all pass the budget.
    @Test
    fun bundlesOpenedLongestAgoAreLetGoPastTheBudget() =
        runBlocking {
            val root = folder.newFolder()
            val bundles = WebxdcBundles(root, maxBytes = 250_000)
            val apps =
                (1..4).map { n ->
                    val archive = zip("index.html" to "<p>$n</p>", "data.bin" to "x".repeat(100_000))
                    served["/app$n.xdc"] = archive
                    sha256(archive).toHexKey()
                }
            apps.forEachIndexed { i, hash ->
                assertNotNull(bundles.open("$base/app${i + 1}.xdc", hash, emptyList(), client))
                // The file clock is coarse: make each later open count as later.
                File(File(root, hash), WebxdcBundles.COMPLETE).setLastModified(1_000_000L * (i + 1))
            }
            // App 1 is opened again, so app 2 is now the one used longest ago.
            assertNotNull(bundles.open("$base/app1.xdc", apps[0], emptyList(), client))
            val five = zip("index.html" to "<p>5</p>", "data.bin" to "y".repeat(100_000))
            served["/app5.xdc"] = five
            assertNotNull(bundles.open("$base/app5.xdc", sha256(five).toHexKey(), emptyList(), client))

            val kept = root.list()!!.toSet()
            assertTrue("the newest stays: $kept", sha256(five).toHexKey() in kept)
            assertTrue("the one opened again stays: $kept", apps[0] in kept)
            assertFalse("the one opened longest ago goes: $kept", apps[1] in kept)
            assertTrue(root.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= 250_000)
        }

    // A launch still serving an app's files must not lose them because the user started another app.
    @Test
    fun aBundleALaunchStillServesIsNeverEvicted() =
        runBlocking {
            val root = folder.newFolder()
            val bundles = WebxdcBundles(root, maxBytes = 150_000)
            val running = zip("index.html" to "<p>running</p>", "data.bin" to "x".repeat(100_000))
            served["/running.xdc"] = running
            val runningDir = bundles.open("$base/running.xdc", sha256(running).toHexKey(), emptyList(), client, retain = true)!!
            File(runningDir, WebxdcBundles.COMPLETE).setLastModified(1_000_000L)

            val next = zip("index.html" to "<p>next</p>", "data.bin" to "y".repeat(100_000))
            served["/next.xdc"] = next
            assertNotNull(bundles.open("$base/next.xdc", sha256(next).toHexKey(), emptyList(), client))

            assertTrue("the running app keeps its files", File(runningDir, "data.bin").isFile)

            bundles.release(runningDir)
            bundles.trimToSize(150_000)
            assertFalse("once released it can go", runningDir.exists())
        }

    @Test
    fun twoLaunchesOfOneArchiveShareOneDownload() =
        runBlocking {
            // A note's run and a window's, each with its own bundles over the same directory.
            val archive = zip("index.html" to "<p>game</p>")
            served["/game.xdc"] = archive
            val root = folder.newFolder()
            val hash = sha256(archive).toHexKey()
            val opened =
                (1..2)
                    .map { async(Dispatchers.IO) { WebxdcBundles(root).open("$base/game.xdc", hash, emptyList(), client) } }
                    .awaitAll()
            assertTrue(opened.all { it != null && it == opened.first() })
            assertEquals(1, requests)
            // Nothing half-made is left beside it.
            assertEquals(listOf(hash), root.list()!!.toList())
        }

    @Test
    fun aMissIsRememberedUntilRetry() =
        runBlocking {
            served["/game.xdc"] = zip("index.html" to "<p>swapped</p>")
            val files = files("$base/game.xdc", "ab".repeat(32))
            assertFalse(files.isAvailable("/"))
            assertNull(files.find("/index.html"))
            assertNull(files.find("/"))
            assertEquals("a page's every request must not download it again", 1, requests)
        }

    @Test
    fun aClosedLaunchDownloadsNothing() =
        runBlocking {
            served["/game.xdc"] = zip("index.html" to "<p>game</p>")
            val files = files("$base/game.xdc", null)
            files.close()
            assertFalse(files.isAvailable("/"))
            assertEquals(0, requests)
        }

    @Test
    fun anArchiveShippingTheMarkerStillOpens() =
        runBlocking {
            served["/game.xdc"] = zip("index.html" to "<p>game</p>", WebxdcBundles.COMPLETE to "mine")
            val files = files("$base/game.xdc", null)
            assertTrue(files.isAvailable("/"))
            assertNull(files.find("/" + WebxdcBundles.COMPLETE))
        }

    @Test
    fun anArchiveWithoutIndexIsNotAnApp() =
        runBlocking {
            served["/game.xdc"] = zip("readme.txt" to "hi")
            assertFalse(files("$base/game.xdc", null).isAvailable("/"))
        }

    @Test
    fun aZipSlipEntryIsNeverWrittenOutside() {
        val archive = folder.newFile("slip.xdc").apply { writeBytes(zip("index.html" to "ok", "../../escaped.txt" to "pwned", "/abs.txt" to "pwned", "__MACOSX/._index.html" to "junk")) }
        val dir = folder.newFolder("unpacked")
        assertTrue(WebxdcArchive.unpack(archive, dir))
        assertEquals(
            setOf("index.html"),
            dir
                .walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(dir).path }
                .toSet(),
        )
        assertFalse(folder.root.walkTopDown().any { it.name == "escaped.txt" || it.name == "abs.txt" })
        assertNull(WebxdcArchive.entryPath("a/../../b"))
        assertEquals("a/b.js", WebxdcArchive.entryPath("./a//b.js"))
    }

    @Test
    fun theWebxdcPostureServesNothingOffOriginAndLocksItsPages() =
        runBlocking {
            val archive = zip("index.html" to "<p>game</p>")
            served["/game.xdc"] = archive
            val origin = NappletWebContract.appOrigin("nabc")
            val server = NappletSiteServer(files("$base/game.xdc", sha256(archive).toHexKey()), "<html><head></head><body>__APP_BOOTSTRAP__</body></html>".encodeToByteArray(), "", origin, HostProfile.WEBXDC, emptyList())

            val page = server.serve("GET", "$origin/index.html", acceptsHtml = true)
            assertNotNull(page)
            assertEquals(NappletWebContract.WEBXDC_CSP, page!!.headers["Content-Security-Policy"])
            val html = page.readBody().decodeToString()
            assertTrue("WebRTC goes before the app", html.indexOf("RTCPeerConnection") in 0 until html.indexOf("<p>game</p>"))
            assertFalse("no napplet runtime", html.contains("__nappletDomains"))
            // The network is never the app's: another host is a 404 here, not the browser's to fetch.
            assertEquals(404, server.serve("GET", "https://example.com/track.js", acceptsHtml = false)?.status)
            val shell = server.serve("GET", NappletWebContract.SHELL_URL, acceptsHtml = true)!!.readBody().decodeToString()
            assertTrue(shell.contains("iframe.src = '$origin/index.html'"))
        }
}
