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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users.index

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropyLocalIndex
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The trust network sync against a real (local, geode) relay: a cold download, updates that
 * must not re-download what is held, and the weekly full check.
 *
 * The provider signs a whole run with one `created_at`, like Brainstorm. The relay first holds
 * only part of that batch, as if the sync ran while the provider was still publishing.
 */
class TrustNetworkSyncTest {
    private val provider = NostrSignerInternal(KeyPair())
    private val random = Random(11)
    private val batchTime = 1_700_000_000L

    private fun hex() = Hex.encode(random.nextBytes(32))

    private suspend fun card(
        subject: String,
        rank: Int,
        at: Long,
    ): Event = provider.sign(at, USER_ASSERTION_KIND, arrayOf(arrayOf("d", subject), arrayOf("rank", rank.toString())), "")

    private suspend fun deletion(
        at: Long,
        vararg subjects: String,
    ): Event = provider.sign(at, 5, subjects.map { arrayOf("a", "$USER_ASSERTION_KIND:${provider.pubKey}:$it") }.toTypedArray(), "")

    @Test
    fun updatesOnlyFetchWhatChanged() =
        runBlocking {
            val subjects = List(300) { hex() }
            val batch = subjects.mapIndexed { i, s -> card(s, 1 + i % 90, batchTime) }

            val engine = RelayEngine(url = "ws://127.0.0.1:7773/".normalizeRelayUrl())
            engine.store.batchInsert(batch.take(200))
            val server = KtorRelay(engine, port = 0).start()
            val client = NostrClient(BasicOkHttpWebSocket.Builder { OkHttpClient() })
            val relay = server.url.normalizeRelayUrl()

            // Later events go through the relay's publish path, like a real provider's: writing to
            // the store directly would bypass the relay's negentropy snapshot invalidation.
            suspend fun publish(events: List<Event>) = events.forEach { assertTrue(client.publishAndConfirm(it, setOf(relay)), "relay refused ${it.id}") }

            try {
                // Cold: the 200 cards published so far.
                val cold = client.downloadTrustNetwork(provider.pubKey, relay)
                assertTrue(cold.complete)
                assertEquals(200, cold.index.size)
                assertEquals(batchTime, cold.header.syncCursor)

                // The rest of the batch lands with the same created_at as the cursor.
                publish(batch.drop(200))
                val caughtUp = client.updateTrustNetwork(cold.header, cold.index, cold.ids, relay)
                assertTrue(caughtUp.complete)
                assertEquals(300, caughtUp.index.size)
                assertEquals(100, caughtUp.received, "only the missing part of the batch is fetched")
                assertEquals(300, caughtUp.header.heldAtCursor, "the whole batch shares the cursor's second")
                assertFalse(client.trustNetworkNews(caughtUp.header, relay).any, "the header alone answers 'anything new?'")

                // Nothing new: the COUNT pre-check ends the update without downloading.
                val idle = client.updateTrustNetwork(caughtUp.header, caughtUp.index, caughtUp.ids, relay)
                assertTrue(idle.unchanged, "the COUNT pre-check ends the update")
                assertEquals(0, idle.received)

                // The provider re-ranks one person, removes another with a rank-0 card (its
                // removal marker) and deletes a third.
                val later = batchTime + 10
                publish(
                    listOf(
                        card(subjects[0], 77, later),
                        card(subjects[1], 0, later),
                        deletion(later, subjects[2]),
                    ),
                )
                val changed = client.updateTrustNetwork(idle.header, idle.index, idle.ids, relay)
                assertTrue(changed.complete)
                assertEquals(77, changed.index.rankOf(subjects[0]), "detail=${changed.detail} received=${changed.received} cursor=${changed.header.syncCursor}")
                assertNull(changed.index.rankOf(subjects[1]))
                assertNull(changed.index.rankOf(subjects[2]))
                assertEquals(298, changed.index.size, "one re-ranked, one tombstoned, one deleted")
                assertEquals(1, changed.ids.tombstones, "the rank-0 card is kept as a tombstone")
                assertEquals(later, changed.header.syncCursor)
                // The update window reaches an hour before the cursor: the earlier batch is in it too.
                assertEquals(299, changed.header.heldAtCursor, "every card held in the update window")
                assertEquals(0, changed.header.heldAfterCursor, "the re-rank and the rank-0 card are at the cursor, none after it")

                // The tombstone keeps the pre-check exact: nothing new again.
                val idleAgain = client.updateTrustNetwork(changed.header, changed.index, changed.ids, relay)
                assertTrue(idleAgain.unchanged)

                // The full check agrees with the relay and fetches nothing (rank-0 cards included).
                val full = assertNotNull(client.reconcileTrustNetwork(idleAgain.header, idleAgain.index, idleAgain.ids, relay))
                assertTrue(full.complete)
                assertEquals(0, full.received, "nothing to fetch, nothing gone: ${full.detail}")
                assertEquals(298, full.index.size)

                // The counts say something changed (a relay without NIP-45 always does), but the
                // reconcile finds nothing: the same index comes back, so nothing is rewritten.
                val noNews = client.updateTrustNetwork(full.header, full.index, full.ids, relay, knownNews = TrustNetworkNews(cards = true))
                assertTrue(noNews.unchanged)
                assertSame(full.index, noNews.index)

                // The cursor moves far past the batch, then the relay drops a batch card (a kind 5
                // signed at the cursor's second, so no "newer than the cursor" count sees it, and
                // the card sits far below the update window). Only the total count notices, and
                // the reconcile drops the card the relay stopped serving: no kind 5 is downloaded.
                val far = batchTime + 10 * TRUST_NETWORK_UPDATE_OVERLAP_SECS
                publish(listOf(card(subjects[3], 50, far)))
                val moved = client.updateTrustNetwork(full.header, full.index, full.ids, relay)
                assertEquals(far, moved.header.syncCursor)
                assertEquals(50, moved.index.rankOf(subjects[3]))
                assertEquals(299, moved.header.heldTotal, "298 entries and the tombstone")

                publish(listOf(deletion(far, subjects[4])))
                assertTrue(client.trustNetworkNews(moved.header, relay).any, "the total count sees a card gone below the window")
                val dropped = client.updateTrustNetwork(moved.header, moved.index, moved.ids, relay)
                assertTrue(dropped.complete)
                assertNull(dropped.index.rankOf(subjects[4]), dropped.detail)
                assertEquals(297, dropped.index.size)
                assertEquals("need 0, gone 1", dropped.detail)
                assertTrue(client.updateTrustNetwork(dropped.header, dropped.index, dropped.ids, relay).unchanged, "and the counts agree again")

                // The ids file round-trips with the tombstone.
                val decoded = assertNotNull(TrustNetworkCodec.decodeIds(TrustNetworkCodec.encodeIds(full.ids)))
                assertEquals(1, decoded.tombstones)
                assertEquals(full.ids.countSince(later), decoded.countSince(later))
                assertNotEquals(0, decoded.countSince(batchTime))
                assertFalse(decoded.entriesSince(later).isEmpty())
            } finally {
                client.close()
                server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
                engine.close()
            }
        }

    @Test
    fun theIdsNegentropyIndexMatchesAListIndex() =
        runBlocking {
            val subjects = List(500) { hex() }
            val builder = TrustNetworkBuilder(provider.pubKey)
            subjects.forEachIndexed { i, s -> builder.add(card(s, if (i % 10 == 0) 0 else 1 + i % 90, batchTime + i % 7)) }
            val ids = builder.build().second
            val packed = ids.negentropyIndex()
            val list = NegentropyLocalIndex.of(ids.entriesSince())
            for (window in listOf(Filter(), Filter(since = batchTime + 3), Filter(until = batchTime + 2), Filter(since = batchTime + 2, until = batchTime + 4), Filter(since = batchTime + 99))) {
                assertEquals(list.count(window), packed.count(window), "count of $window")
                assertEquals(list.entriesFor(window).map { it.id }.toSet(), packed.entriesFor(window).map { it.id }.toSet(), "entries of $window")
            }
        }

    companion object {
        const val USER_ASSERTION_KIND = 30382
    }
}
