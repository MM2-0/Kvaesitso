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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.listFolder.UnrecognizedKind30101Event
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressAppDataEvent
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressEvent
import com.vitorpamplona.quartz.drss.readWriteLists.DrssReadWriteListsEvent
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.board.UnrecognizedKind30301Event
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.BuildVerificationEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.UnrecognizedKind38385Event
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.UnrecognizedKind30054Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The carded shapes of the shared kinds 30301, 30302, 38385 and 30054 reach the addressable cache, where
 * their cards read them; the other apps' shapes on the same kinds do not, so nothing draws them.
 * A Kanban card is also filed under its board's replies, which is where the board card counts it.
 */
class SharedKindCardsConsumeTest {
    private val owner = "aa".repeat(32)
    private val sig = "22".repeat(64)

    private fun event(
        idByte: String,
        kind: Int,
        createdAt: Long,
        vararg tags: Array<String>,
        content: String = "",
    ): Event = EventFactory.create(idByte.repeat(32), owner, createdAt, kind, arrayOf(*tags), content, sig)

    private fun EventCache.stored(event: Event) = getAddressableNoteIfExists((event as AddressableEvent).address())?.event

    @Test
    fun storesDrssDataTheReaderWritesToo() {
        val cache = EventCache()
        // drss saves articles into NIP-51 curation sets, and keeps its read and write folder in a
        // NIP-78 preference.
        val saved = event("0a", 30004, 1_700_000_000L, arrayOf("d", "drss-podcasts"), arrayOf("title", "podcasts"), arrayOf("a", "30023:${"bb".repeat(32)}:episode-1"))
        val preference = event("0b", 30078, 1_700_000_000L, arrayOf("d", "read-write-lists"), arrayOf("a", "30101:$owner:drss"))
        // drss's listening progress, a NIP-78 record since it left kind 30054.
        val progress =
            event(
                "0c",
                30078,
                1_700_000_000L,
                arrayOf("d", "drss-listen-progress:episode-1"),
                arrayOf("a", "30023:${"bb".repeat(32)}:episode-1"),
                arrayOf("L", "drss"),
                arrayOf("l", "listen-progress", "drss"),
                arrayOf("position", "733"),
                arrayOf("duration", "2744"),
            )

        assertIs<ArticleCurationSetEvent>(saved)
        assertIs<DrssReadWriteListsEvent>(preference)
        assertIs<DrssListenProgressAppDataEvent>(progress)
        listOf(saved, preference, progress).forEach {
            assertTrue(cache.justConsume(it, null, true), it.toJson())
            assertSame(it, cache.stored(it))
        }
    }

    @Test
    fun consumesBoardsVerificationsAndMostroTerms() {
        val cache = EventCache()

        val board = event("01", 30301, 1_700_000_000L, arrayOf("d", "roadmap"), arrayOf("title", "Roadmap"))
        val verdict = event("02", 30301, 1_700_000_000L, arrayOf("d", "w:1:android:ff"), arrayOf("i", "com.example.wallet"), arrayOf("status", "reproducible"))
        val mostro = event("03", 38385, 1_700_000_000L, arrayOf("d", owner), arrayOf("z", "info"), arrayOf("mostro_version", "0.12.8"))

        assertIs<KanbanBoardEvent>(board)
        assertIs<BuildVerificationEvent>(verdict)
        assertIs<MostroInfoEvent>(mostro)

        listOf(board, verdict, mostro).forEach {
            assertTrue(cache.justConsume(it, null, true), it.toJson())
            assertSame(it, cache.stored(it))
        }
    }

    @Test
    fun leavesOtherAppsShapesOutOfTheCache() {
        val cache = EventCache()

        val planner = event("04", 30301, 1_700_000_000L, arrayOf("d", "task-1"), arrayOf("b", "ff".repeat(32)), arrayOf("col", "day"), content = "ciphertext")
        val paygress = event("05", 38385, 1_700_000_000L, arrayOf("d", "paygress:revocation:v1:1"))

        assertIs<UnrecognizedKind30301Event>(planner)
        assertIs<UnrecognizedKind38385Event>(paygress)

        assertFalse(cache.justConsume(planner, null, true))
        assertFalse(cache.justConsume(paygress, null, true))
        assertNull(cache.stored(planner))
        assertNull(cache.stored(paygress))
    }

    @Test
    fun storesPodcastEpisodesAndDrssProgressAtTheirAddresses() {
        val cache = EventCache()

        val episode = event("08", 30054, 1_700_000_000L, arrayOf("d", "ep-1"), arrayOf("title", "Episode 1"), arrayOf("audio", "https://example.com/ep-1.mp3", "audio/mpeg"))
        val progress =
            event(
                "09",
                30054,
                1_700_000_000L,
                arrayOf("d", "981a23b01dc9f1a65527b005b2e8af0a6e38a5c6af460692ab7b0c2cac692160"),
                arrayOf("a", "30023:${"bb".repeat(32)}:981a23b01dc9f1a65527b005b2e8af0a6e38a5c6af460692ab7b0c2cac692160"),
                arrayOf("position", "733"),
                arrayOf("duration", "2744"),
            )

        assertIs<Podcasting20EpisodeEvent>(episode)
        assertIs<DrssListenProgressEvent>(progress)

        assertTrue(cache.justConsume(episode, null, true))
        assertSame(episode, cache.stored(episode))

        // Stored, so a quote finds it; the episode feeds pick `is PodcastEpisode` and never draw it.
        assertTrue(cache.justConsume(progress, null, true))
        assertSame(progress, cache.stored(progress))
    }

    @Test
    fun aNonEpisodeVersionStillSupersedesAnEpisodeAtItsAddress() {
        val cache = EventCache()

        val episode = event("0c", 30054, 1_700_000_000L, arrayOf("d", "ep-2"), arrayOf("title", "Episode 2"), arrayOf("audio", "https://example.com/ep-2.mp3"))
        val blanked = event("0d", 30054, 1_700_000_100L, arrayOf("d", "ep-2"))
        assertIs<UnrecognizedKind30054Event>(blanked)

        assertTrue(cache.justConsume(episode, null, true))
        assertTrue(cache.justConsume(blanked, null, true))
        assertSame(blanked, cache.stored(blanked))
    }

    @Test
    fun storesDrssListsAndTheOtherAppsOnTheirKindsAsTheyWereWhileUntyped() {
        val cache = EventCache()

        val folder = event("0a", 30101, 1_700_000_000L, arrayOf("d", "drss"), arrayOf("title", "DRSS lists"), arrayOf("a", "30000:$owner:drss-feeds", "wss://relay.nostr.io"))
        val round = event("0b", 30101, 1_700_000_000L, arrayOf("d", "rodada-002"), arrayOf("title", "Rodada 2"), arrayOf("projeto", "30100:${"cc".repeat(32)}:x"))

        assertIs<DrssListFolderEvent>(folder)
        assertIs<UnrecognizedKind30101Event>(round)

        listOf(folder, round).forEach {
            assertTrue(cache.justConsume(it, null, true), it.toJson())
            assertSame(it, cache.stored(it))
        }
    }

    @Test
    fun aKanbanCardIsFiledUnderItsBoardAndMovesWhenEdited() {
        val cache = EventCache()

        val board = event("01", 30301, 1_700_000_000L, arrayOf("d", "roadmap"), arrayOf("title", "Roadmap"))
        val other = event("02", 30301, 1_700_000_000L, arrayOf("d", "other"), arrayOf("title", "Other"))
        val card = event("06", 30302, 1_700_000_100L, arrayOf("d", "fix"), arrayOf("title", "Fix it"), arrayOf("a", "30301:$owner:roadmap"), arrayOf("s", "todo"))
        assertIs<KanbanCardEvent>(card)

        cache.justConsume(board, null, true)
        cache.justConsume(other, null, true)
        assertTrue(cache.justConsume(card, null, true))

        val boardNote = cache.getAddressableNoteIfExists((board as AddressableEvent).address())!!
        val otherNote = cache.getAddressableNoteIfExists((other as AddressableEvent).address())!!
        val cardNote = cache.getAddressableNoteIfExists(card.address())!!

        assertEquals(listOf(cardNote), boardNote.replies)

        // The card is moved to another board by republishing it: it leaves the first board's replies.
        val moved = event("07", 30302, 1_700_000_200L, arrayOf("d", "fix"), arrayOf("title", "Fix it"), arrayOf("a", "30301:$owner:other"))
        assertTrue(cache.justConsume(moved, null, true))

        assertEquals(emptyList(), boardNote.replies)
        assertEquals(listOf(cardNote), otherNote.replies)
    }
}
