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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEdition
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEditionsCodec
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserverPublisherTest {
    private val signer = NostrSignerInternal(KeyPair())

    /** Relays and Blossom in memory: the newest published manifest wins, blobs by hash. */
    private inner class FakeSite : ObserverSiteIO {
        var sites = mutableListOf<NamedSiteEvent>()
        val blobs = mutableMapOf<HexKey, ByteArray>()
        var failUploads = false
        var lose: HexKey? = null
        var limits: List<Int?> = listOf(null)
        var tagLimits: List<Int?> = listOf(null)
        var published = 0
        var relaysDown = false
        var servers = listOf("https://blossom.example", "https://second.example")
        val uploadsTo = mutableListOf<List<String>>()
        var inFlight = 0
        var maxInFlight = 0

        override suspend fun latestSite(author: HexKey): SiteRead {
            if (relaysDown) return SiteRead.Unreadable
            return sites.filter { it.pubKey == author }.maxByOrNull { it.createdAt }?.let { SiteRead.Found(it) } ?: SiteRead.None
        }

        override suspend fun servers() = servers

        override suspend fun download(
            servers: List<String>,
            sha256: HexKey,
        ) = if (sha256 == lose) null else blobs[sha256]

        override suspend fun upload(
            servers: List<String>,
            bytes: ByteArray,
            sha256: HexKey,
            contentType: String,
        ): Boolean {
            if (failUploads) return false
            assertEquals(ObserverPublisher.sha256Hex(bytes), sha256)
            uploadsTo.add(servers)
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            yield()
            inFlight--
            blobs[sha256] = bytes
            return true
        }

        override suspend fun maxMessageLengths() = limits

        override suspend fun maxEventTags() = tagLimits

        override suspend fun publish(template: EventTemplate<NamedSiteEvent>): NamedSiteEvent {
            published++
            return signer.sign(template).also { sites.add(it) }
        }
    }

    private var clock = 1_794_355_200L

    private fun publisher(io: ObserverSiteIO) = ObserverPublisher(io, signer.pubKey, { "Vitor" }, { clock })

    private fun page(
        printedAt: Long,
        html: String,
    ): ObserverPage {
        val sha = ObserverPublisher.sha256Hex(html.encodeToByteArray())
        return ObserverPage(ObserverPublisher.entryFor(SavedEdition(day = "local", code = "C$printedAt", printedAt = printedAt, model = "m", posts = 3, quotes = 1, headline = "H$printedAt"), sha), html)
    }

    private fun indexOf(
        io: FakeSite,
        site: NamedSiteEvent,
    ) = ObserverEditionsCodec.decode(io.blobs.getValue(site.paths().first { it.path == ObserverSite.EDITIONS_PATH }.hash).decodeToString())!!

    @Test
    fun firstPublishMakesTheSite() =
        runTest {
            val io = FakeSite()
            val result = assertIs<ObserverPublishResult.Published>(publisher(io).publish(listOf(page(clock, "<p>one</p>"))))
            val site = result.site
            assertEquals(ObserverSite.D_TAG, site.identifier())
            assertEquals("Vitor's Observer", site.title())
            val today = ObserverSite.utcDay(clock)
            assertEquals(site.paths().first { it.path == "/$today.html" }.hash, site.paths().first { it.path == "/index.html" }.hash)
            assertEquals("H$clock", indexOf(io, site).editions.single().headline)
            assertTrue(
                io.blobs
                    .getValue(site.paths().first { it.path == "/archive.html" }.hash)
                    .decodeToString()
                    .contains("Vitor&#39;s Observer"),
            )
        }

    @Test
    fun aNextDayAppendsAndTheSameDayReplaces() =
        runTest {
            val io = FakeSite()
            publisher(io).publish(listOf(page(clock, "<p>one</p>")))
            clock += 86_400
            publisher(io).publish(listOf(page(clock, "<p>two</p>")))
            val replaced = page(clock + 60, "<p>two, again</p>")
            clock += 120
            val site = assertIs<ObserverPublishResult.Published>(publisher(io).publish(listOf(replaced))).site
            val index = indexOf(io, site)
            assertEquals(2, index.editions.size)
            assertEquals(replaced.edition.sha256, index.editions.first().sha256)
            assertEquals(replaced.edition.sha256, site.paths().first { it.path == "/index.html" }.hash)
            // Each manifest is newer than the one it was built from.
            assertTrue(io.sites.zipWithNext().all { (a, b) -> b.createdAt > a.createdAt })
        }

    @Test
    fun aSmallRelayLimitRollsTheOldestOff() =
        runTest {
            val io = FakeSite()
            repeat(30) {
                publisher(io).publish(listOf(page(clock, "<p>day $it</p>")))
                clock += 86_400
            }
            assertEquals(30, indexOf(io, io.sites.last()).editions.size)
            val full = ObserverSite.signedSize(io.sites.last().let { EventTemplate<NamedSiteEvent>(it.createdAt, it.kind, it.tags, it.content) }, signer.pubKey)
            io.limits = listOf(full - 2_000 + ObserverSite.ENVELOPE_BYTES, null)
            val site = assertIs<ObserverPublishResult.Published>(publisher(io).publish(listOf(page(clock, "<p>day 30</p>")))).site
            val kept = indexOf(io, site).editions
            assertTrue(kept.size < 31)
            assertEquals(ObserverSite.utcDay(clock), kept.first().day)
            assertTrue(site.toJson().encodeToByteArray().size <= full - 2_000)
        }

    @Test
    fun aMissingIndexIsRebuiltFromThePaths() =
        runTest {
            val io = FakeSite()
            val first = assertIs<ObserverPublishResult.Published>(publisher(io).publish(listOf(page(clock, "<p>one</p>")))).site
            io.lose = first.paths().first { it.path == ObserverSite.EDITIONS_PATH }.hash
            clock += 86_400
            val site = assertIs<ObserverPublishResult.Published>(publisher(io).publish(listOf(page(clock, "<p>two</p>")))).site
            val editions = indexOf(io, site).editions
            assertEquals(2, editions.size)
            // The day from before the loss survives with what its path carries, without a headline.
            assertNull(editions.last().headline)
        }

    @Test
    fun aFailedUploadPublishesNothing() =
        runTest {
            val io = FakeSite()
            publisher(io).publish(listOf(page(clock, "<p>one</p>")))
            io.failUploads = true
            clock += 86_400
            assertEquals(ObserverPublishResult.Failed(ObserverFailure.UPLOAD), publisher(io).publish(listOf(page(clock, "<p>two</p>"))))
            assertEquals(1, io.published)
        }

    @Test
    fun theReaderSeesTheSiteAndItsPages() =
        runTest {
            val io = FakeSite()
            val sent = page(clock, "<p>one</p>")
            publisher(io).publish(listOf(sent))
            val view = assertNotNull(publisher(io).read())
            assertEquals("<p>one</p>", publisher(io).page(view.editions.single(), view.servers))
        }

    @Test
    fun historyAddsOnlySiteEditionsThisDeviceLacks() {
        val local = listOf(SavedEdition(day = "2026-11-10", code = "A", printedAt = 1, model = "m", sha256 = "a".repeat(64)))
        val site =
            listOf(
                ObserverEdition("2026-11-11", "/2026-11-11.html", "a".repeat(64)),
                ObserverEdition("2026-11-09", "/2026-11-09.html", "b".repeat(64), headline = "From the web"),
                ObserverEdition("2026-11-10", "/2026-11-10.html", "c".repeat(64)),
            )
        val merged = mergeHistory(local, site)
        assertEquals(listOf("2026-11-10", "2026-11-09"), merged.map { it.day })
        assertEquals("From the web", merged.last().headline)
        assertTrue(merged.last().published)
    }

    @Test
    fun anUnreadableSiteIsNeverOverwritten() =
        runTest {
            val io = FakeSite()
            publisher(io).publish(listOf(page(clock, "<p>one</p>")))
            io.relaysDown = true
            clock += 86_400
            assertEquals(ObserverPublishResult.Failed(ObserverFailure.READ), publisher(io).publish(listOf(page(clock, "<p>two</p>"))))
            assertEquals(1, io.published)
            assertTrue(io.uploadsTo.size == 3)
        }

    @Test
    fun noBlossomServerListMeansNothingIsUploaded() =
        runTest {
            val io = FakeSite()
            io.servers = emptyList()
            assertEquals(ObserverPublishResult.Failed(ObserverFailure.NO_SERVERS), publisher(io).publish(listOf(page(clock, "<p>one</p>"))))
            assertTrue(io.uploadsTo.isEmpty())
            assertEquals(0, io.published)
        }

    @Test
    fun everyFileGoesToEveryChosenServerInParallel() =
        runTest {
            val io = FakeSite()
            publisher(io).publish(listOf(page(clock, "<p>one</p>"), page(clock - 86_400, "<p>yesterday</p>")))
            assertTrue(io.uploadsTo.all { it == io.servers })
            assertTrue(io.maxInFlight > 1)
        }

    @Test
    fun theTagBudgetRollsTheOldestOff() =
        runTest {
            val io = FakeSite()
            repeat(20) {
                publisher(io).publish(listOf(page(clock, "<p>day $it</p>")))
                clock += 86_400
            }
            io.tagLimits = listOf(null, 15)
            val site = assertIs<ObserverPublishResult.Published>(publisher(io).publish(listOf(page(clock, "<p>day 20</p>")))).site
            assertTrue(site.tags.size <= 15)
            assertEquals(ObserverSite.utcDay(clock), indexOf(io, site).editions.first().day)
        }

    @Test
    fun aPageIsNamedByTheBytesThatGoOut() =
        runTest {
            val io = FakeSite()
            val honest = page(clock, "<p>one</p>")
            val mislabeled = ObserverPage(honest.edition.copy(sha256 = "f".repeat(64)), honest.html)
            val site = assertIs<ObserverPublishResult.Published>(publisher(io).publish(listOf(mislabeled))).site
            assertEquals(honest.edition.sha256, site.paths().first { it.path == "/index.html" }.hash)
        }

    @Test
    fun theCspGoesFirstInTheHeadOnceAndNeverInsideAComment() {
        val page = "<!doctype html>\n<html lang=\"en\"><!-- <head> --><head title=\"a>b\"><title>x</title></head><body></body></html>"
        val once = withPaperCsp(page)
        assertTrue(once.contains("<head title=\"a>b\">$PAPER_CSP_META<title>"))
        assertTrue(once.contains("<!-- <head> -->"))
        assertEquals(once, withPaperCsp(once))
        assertTrue(withPaperCsp("<!doctype html><p>hi</p>").startsWith("<!doctype html>$PAPER_CSP_META"))
        assertTrue(withPaperCsp("<html><header>x</header></html>").startsWith("<html>$PAPER_CSP_META"))
    }
}
