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
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEditions
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEditionsCodec
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reader's Observer site and the one their Assistant (the schedule's runner) publishes for
 * them: today's paper from either, the history merged with the Assistant's marked, and only that
 * runner's site, tagging this reader, ever read.
 */
class ObserverTwoSitesTest {
    /** 2026-11-12T12:00:00Z. */
    private val noon = 1_794_484_800L
    private val day = 86_400L
    private val utc = ObserverCalendar.of(CronZone.fixed(0))

    private val reader = NostrSignerInternal(KeyPair())
    private val assistant = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    private fun entry(
        printedAt: Long,
        sha: Char,
        headline: String = "H$sha",
    ) = ObserverEdition(
        day = ObserverSite.utcDay(printedAt),
        path = ObserverSite.dayPath(ObserverSite.utcDay(printedAt)),
        sha256 = sha.toString().repeat(64),
        headline = headline,
        code = "C$sha",
        printedAt = printedAt,
        model = "m",
    )

    private fun kept(
        printedAt: Long,
        sha: Char,
    ) = SavedEdition(day = ObserverSite.utcDay(printedAt), code = "L$sha", printedAt = printedAt, model = "m", sha256 = sha.toString().repeat(64))

    @Test
    fun todaysPaperComesFromTheAssistantsSite() {
        val history = mergeHistory(local = listOf(kept(noon - day, 'a')), site = emptyList(), runnerSite = listOf(entry(noon - 3_600, 'b')))
        val today = observerToday(history, noon, utc)
        val paper = assertNotNull(today.paper)
        assertEquals("Hb", paper.headline)
        assertTrue(paper.byRunner)
        assertTrue(paper.published)
        assertEquals(listOf("La"), today.past.map { it.edition.code })
        assertFalse(
            today.past
                .single()
                .edition.byRunner,
        )
    }

    @Test
    fun thePastMergesBothSitesAndMarksTheAssistants() {
        val history =
            mergeHistory(
                local = listOf(kept(noon, 'a')),
                site = listOf(entry(noon - day, 'b'), entry(noon - 3 * day, 'x')),
                runnerSite = listOf(entry(noon - 2 * day, 'c'), entry(noon - 3 * day, 'y'), entry(noon, 'z')),
            )
        // Newest first; a day the reader's own site or this device has stays theirs.
        assertEquals(listOf("La", "Cb", "Cc", "Cx"), history.map { it.code })
        assertEquals(listOf(false, false, true, false), history.map { it.byRunner })
    }

    @Test
    fun theSameEditionOnBothSitesIsShownOnce() {
        val shared = entry(noon - day, 'b')
        val history = mergeHistory(local = emptyList(), site = listOf(shared), runnerSite = listOf(shared.copy(day = "2026-11-10")))
        assertEquals(1, history.size)
        assertFalse(history.single().byRunner)
    }

    @Test
    fun withoutARunnerTheHistoryIsAsBefore() {
        val local = listOf(kept(noon, 'a'))
        val site = listOf(entry(noon - day, 'b'))
        assertEquals(mergeHistory(local, site), mergeHistory(local, site, emptyList()))
        assertTrue(mergeHistory(local, site).none { it.byRunner })
    }

    @Test
    fun anAssistantsEditionKeptHereIsStillTheAssistantsPrint() {
        // Opened once, today's Assistant paper is kept on the device (marked as the runner's).
        val opened = savedFromSite(entry(noon - 3_600, 'b'), byRunner = true)
        val own = kept(noon - day, 'a')
        val sites = observerSitesOf(listOf(opened, own), site = emptyList(), runnerSite = listOf(entry(noon - 3_600, 'b')))
        assertEquals(listOf(noon - day), sites.reader.map { it.printedAt })
        assertTrue(sites.runner.all { it.printedAt == noon - 3_600 })
        val last = assertNotNull(ObserverSite.lastPrinted(sites.reader, sites.runner))
        assertTrue(last.byRunner)
        assertEquals(noon - 3_600, last.printedAt)
    }

    /** Relays and Blossom in memory, for the runner's site read. */
    private class Sites(
        private val readerServers: List<String>,
    ) : ObserverSiteIO {
        val sites = mutableListOf<NamedSiteEvent>()
        val blobs = mutableMapOf<HexKey, ByteArray>()

        override suspend fun latestSite(author: HexKey): SiteRead = sites.filter { it.pubKey == author }.maxByOrNull { it.createdAt }?.let { SiteRead.Found(it) } ?: SiteRead.None

        // A careless relay: answers with every site it holds, whoever signed it.
        override suspend fun latestRunnerSite(
            runner: HexKey,
            reader: HexKey,
        ): SiteRead = sites.maxByOrNull { it.createdAt }?.let { SiteRead.Found(it) } ?: SiteRead.None

        override suspend fun servers() = readerServers

        override suspend fun download(
            servers: List<String>,
            sha256: HexKey,
        ) = blobs[sha256]

        override suspend fun upload(
            servers: List<String>,
            bytes: ByteArray,
            sha256: HexKey,
            contentType: String,
        ) = true

        override suspend fun maxMessageLengths() = listOf<Int?>(null)

        override suspend fun maxEventTags() = listOf<Int?>(null)

        override suspend fun publish(template: EventTemplate<NamedSiteEvent>): NamedSiteEvent = error("not here")

        /** [by]'s Observer site for [forReader], its index uploaded beside it. */
        suspend fun publishAs(
            by: NostrSignerInternal,
            forReader: HexKey?,
            editions: List<ObserverEdition>,
            createdAt: Long,
        ) {
            val index = ObserverEditionsCodec.encode(ObserverEditions(title = "T", editions = editions)).encodeToByteArray()
            val indexSha = ObserverPublisher.sha256Hex(index)
            blobs[indexSha] = index
            sites += by.sign(ObserverSite.build(editions, "0".repeat(64), indexSha, null, readerServers, createdAt, ObserverSite.latestDay(createdAt), forReader))
        }
    }

    @Test
    fun onlyTheNamedRunnersSiteForThisReaderIsRead() =
        runTest {
            val io = Sites(listOf("https://blossom.reader.example"))
            val publisher = ObserverPublisher(io, reader.pubKey, { "Reader" }, { noon })
            assertNull(publisher.readRunner(assistant.pubKey))

            io.publishAs(assistant, reader.pubKey, listOf(entry(noon - 600, 'b', "By the Assistant")), noon - 500)
            val view = assertNotNull(publisher.readRunner(assistant.pubKey))
            assertEquals(listOf("By the Assistant"), view.editions.map { it.headline })
            // Its files are on the reader's own Blossom servers.
            assertTrue("https://blossom.reader.example" in view.servers)

            // Another key that tags the reader is never the runner's site, however new.
            io.publishAs(stranger, reader.pubKey, listOf(entry(noon - 60, 'c', "Spoof")), noon - 50)
            assertNull(publisher.readRunner(assistant.pubKey))
            // And the runner's site for someone else isn't this reader's.
            io.sites.clear()
            io.publishAs(assistant, stranger.pubKey, listOf(entry(noon - 60, 'd')), noon - 40)
            assertNull(publisher.readRunner(assistant.pubKey))
        }
}
