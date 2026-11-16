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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ways the trust network sync used to end up with the wrong people in or out of the network,
 * each against a real (local, geode) relay. The provider signs a whole run with one
 * `created_at`, like Brainstorm.
 */
class TrustNetworkSyncRegressionTest {
    private val provider = NostrSignerInternal(KeyPair())
    private val random = Random(5)
    private val batchTime = 1_700_000_000L

    private fun hex() = Hex.encode(random.nextBytes(32))

    private suspend fun card(
        subject: String,
        rank: Int,
        at: Long,
    ): Event = provider.sign(at, 30382, arrayOf(arrayOf("d", subject), arrayOf("rank", rank.toString())), "")

    private fun badSignature(event: Event) = Event(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content, "00".repeat(64))

    private suspend fun <T> withRelay(
        events: List<Event>,
        // A relay's max_limit: every REQ page is cut to this many events.
        pageCap: Int? = null,
        block: suspend (RelayEngine, NostrClient, NormalizedRelayUrl) -> T,
    ): T {
        val engine =
            if (pageCap == null) {
                RelayEngine(url = "ws://127.0.0.1:7790/".normalizeRelayUrl())
            } else {
                RelayEngine(url = "ws://127.0.0.1:7790/".normalizeRelayUrl(), policyBuilder = { LimitsPolicy(RelayLimits(defaultLimit = pageCap, maxLimit = pageCap)) })
            }
        engine.store.batchInsert(events)
        val server = KtorRelay(engine, port = 0).start()
        val client = NostrClient(BasicOkHttpWebSocket.Builder { OkHttpClient() })
        try {
            return block(engine, client, server.url.normalizeRelayUrl())
        } finally {
            client.close()
            server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
            engine.close()
        }
    }

    @Test
    fun aReplacementInTheLatestBatchIsSeenByTheUpdate() =
        runBlocking {
            val subjects = List(300) { hex() }
            val batch = subjects.map { card(it, 50, batchTime) }
            val cold = withRelay(batch) { _, client, relay -> client.downloadTrustNetwork(provider.pubKey, relay) }
            assertTrue(cold.complete)

            // The relay keeps one card per subject: the newer card replaces the old one, so the
            // count of cards at or after the cursor does not move. One person is removed (the
            // provider's rank-0 marker), another is re-ranked.
            val later = batchTime + 600
            val relayNow = batch.drop(2) + card(subjects[0], 0, later) + card(subjects[1], 80, later)
            withRelay(relayNow) { _, client, relay ->
                val updated = client.updateTrustNetwork(cold.header, cold.index, cold.ids, relay)
                assertFalse(updated.unchanged, "the update must see the replacements: ${updated.detail}")
                assertTrue(updated.complete)
                assertNull(updated.index.rankOf(subjects[0]), "the removed person is out")
                assertEquals(80, updated.index.rankOf(subjects[1]))
                assertEquals(299, updated.index.size)

                // And the counts are exact again afterwards: nothing new.
                val idle = client.updateTrustNetwork(updated.header, updated.index, updated.ids, relay)
                assertTrue(idle.unchanged, idle.detail)
            }
        }

    @Test
    fun aReplacementThatCannotBeHadKeepsThePersonIn() =
        runBlocking {
            val subjects = List(300) { hex() }
            val v1 = subjects.map { card(it, 50, batchTime) }
            val cold = withRelay(v1) { _, client, relay -> client.downloadTrustNetwork(provider.pubKey, relay) }

            // A recompute re-signs everyone; the relay serves one of the new cards with a bad signature.
            val v2 = subjects.map { card(it, 60, batchTime + 60) }
            val full =
                withRelay(listOf(badSignature(v2[0])) + v2.drop(1)) { _, client, relay ->
                    assertNotNull(client.reconcileTrustNetwork(cold.header, cold.index, cold.ids, relay))
                }
            assertEquals(1, full.invalid)
            assertEquals(50, full.index.rankOf(subjects[0]), "keeps the card it had until a valid one arrives")
            assertEquals(60, full.index.rankOf(subjects[1]))
            assertEquals(300, full.index.size, "nobody drops out: ${full.detail}")

            // Once the relay serves a valid card, the next reconcile takes it and drops the old one.
            val fixed =
                withRelay(v2) { _, client, relay ->
                    assertNotNull(client.reconcileTrustNetwork(full.header, full.index, full.ids, relay))
                }
            assertTrue(fixed.complete)
            assertEquals(60, fixed.index.rankOf(subjects[0]))
            assertEquals(300, fixed.index.size)
            assertEquals(0, fixed.ids.tombstones, "the old cards are dropped, not buried")
        }

    @Test
    fun aDownloadWhileTheProviderIsStillPublishingIsNotTheNetwork() =
        runBlocking {
            // Distinct seconds, so the walk pages normally from the newest card backwards.
            val first = List(3_000) { i -> card(hex(), 50, batchTime + i) }
            // The rest of the run lands after the walk has passed the newest second.
            val rest = List(2_000) { card(hex(), 50, batchTime + 10_000) }
            // And more still while the download fetches what the walk missed.
            val evenMore = List(2_000) { card(hex(), 50, batchTime + 20_000) }
            var published = 0
            val result =
                withRelay(first) { engine, client, relay ->
                    client.downloadTrustNetwork(provider.pubKey, relay) { verified, _ ->
                        if (verified >= 1_000 && published == 0) {
                            published = 1
                            runBlocking { engine.store.batchInsert(rest) }
                        } else if (verified >= 4_000 && published == 1) {
                            published = 2
                            runBlocking { engine.store.batchInsert(evenMore) }
                        }
                    }
                }
            assertEquals(2, published, "the test must publish during the walk and during the fill")
            assertFalse(result.complete, "the last batch is missing: ${result.detail}")
            assertTrue(result.detail!!.contains("still publishing"), result.detail)
        }

    @Test
    fun cardsPublishedDuringTheWalkAreFetchedBeforeItEnds() =
        runBlocking {
            val first = List(3_000) { i -> card(hex(), 50, batchTime + i) }
            // Published above the walk's cursor once it has passed the newest second.
            val rest = List(2_000) { card(hex(), 50, batchTime + 10_000) }
            var published = false
            val result =
                withRelay(first) { engine, client, relay ->
                    client.downloadTrustNetwork(provider.pubKey, relay) { verified, _ ->
                        if (verified >= 1_000 && !published) {
                            published = true
                            runBlocking { engine.store.batchInsert(rest) }
                        }
                    }
                }
            assertTrue(published, "the test must publish during the walk")
            assertTrue(result.complete, result.detail)
            assertEquals(5_000, result.index.size, "the cards the walk missed are fetched by negentropy: ${result.detail}")
        }

    @Test
    fun aDownloadOfAFinishedRunIsComplete() =
        runBlocking {
            val cards = List(3_000) { i -> card(hex(), 50, batchTime + i) }
            val result = withRelay(cards) { _, client, relay -> client.downloadTrustNetwork(provider.pubKey, relay) }
            assertTrue(result.complete, result.detail)
            assertEquals(3_000, result.index.size)
        }

    @Test
    fun aCardReceivedAgainIsOneCard() =
        runBlocking {
            val subject = hex()
            val first = TrustNetworkBuilder(provider.pubKey)
            val c = card(subject, 50, batchTime)
            first.add(c)
            val (index, ids) = first.build()

            // An update re-fetching a card the index already holds (a relay without NIP-77 pages
            // past the cursor again), even more than once.
            val again = TrustNetworkBuilder(provider.pubKey)
            again.addAll(index, ids)
            again.add(c)
            again.add(c)
            val (index2, ids2) = again.build()
            assertEquals(1, index2.size)
            assertEquals(50, index2.rankOf(subject))
            assertEquals(0, ids2.tombstones, "a copy of the card is not an older version of it")
            assertEquals(1, ids2.countSince(batchTime))
        }

    @Test
    fun anIndexFromAnOlderVersionIsRebuilt() {
        val header = TrustNetworkHeader(provider.pubKey, "wss://scores.example.com", syncCursor = batchTime, lastFullCheck = 1, lastUpdate = 2, heldAtCursor = 300, heldAfterCursor = 0, heldTotal = 301, generation = 7)
        val builder = TrustNetworkBuilder(provider.pubKey)
        val current = TrustNetworkCodec.encodeIndex(header, builder.build().first)
        assertEquals(header, TrustNetworkCodec.decodeHeader(current))
        assertEquals(header, assertNotNull(TrustNetworkCodec.decodeIndex(current), "the current version reads back").first)

        // Version 6 keys the index by the whole pubkey; older files keyed it by a prefix, so they
        // are not read (and possibly misread) but refused, and the next update rebuilds the index.
        for (version in 3 until TrustNetworkCodec.VERSION) {
            val older = current.copyOf()
            older[4] = (version shr 8).toByte()
            older[5] = version.toByte()
            assertNull(TrustNetworkCodec.decodeIndex(older), "a version $version file is rebuilt, not read")
        }
    }

    @Test
    fun aDownloadSavesCheckpointsAsItGoes() =
        runBlocking {
            val cards = List(6_000) { i -> card(hex(), 50, batchTime + i) }
            val saved = mutableListOf<Int>()
            val result =
                withRelay(cards) { _, client, relay ->
                    client.downloadTrustNetwork(provider.pubKey, relay, checkpoint = { saved += it.cards }, checkpointEvery = 1_000)
                }
            assertTrue(result.complete)
            // Each holds everything so far; after the first few they come as the download grows by half.
            assertEquals(listOf(1_000, 2_000, 3_000, 4_500), saved)
        }

    @Test
    fun theNegentropyFillAfterPagingKeepsCheckpointing() =
        runBlocking {
            // Paging gets 2,000 cards (the dense second's first page and the older ones), then
            // negentropy fills the other 2,500. The fill counted from 0 against a last checkpoint
            // of 2,000, so it never saved: an interruption there lost everything it fetched.
            val dense = List(3_000) { card(hex(), 50, batchTime) }
            val older = List(1_500) { i -> card(hex(), 50, batchTime - 1 - i) }
            val saved = mutableListOf<Int>()
            val result =
                withRelay(dense + older, pageCap = 500) { _, client, relay ->
                    client.downloadTrustNetwork(provider.pubKey, relay, checkpoint = { saved += it.cards }, checkpointEvery = 1_000)
                }
            assertTrue(result.complete, result.detail)
            assertEquals(4_500, result.index.size)
            assertEquals(listOf(1_000, 2_000, 3_000, 4_500), saved, "the fill checkpoints too")
        }

    @Test
    fun aSecondDenserThanTheRelaysPageIsFetchedByNegentropy() =
        runBlocking {
            // A provider publishing a batch: 1,200 cards in one second, on a relay that pages 500
            // at a time (strfry's default). Paging steps past that second with only its first page
            // and still drains.
            val dense = List(1_200) { card(hex(), 50, batchTime) }
            val older = List(150) { i -> card(hex(), 50, batchTime - 1 - i) }
            val result =
                withRelay(dense + older, pageCap = 500) { _, client, relay ->
                    client.downloadTrustNetwork(provider.pubKey, relay)
                }
            assertTrue(result.complete, result.detail)
            assertEquals(1_350, result.index.size, "every card of the dense second: ${result.detail}")
        }

    @Test
    fun aCutOffDownloadResumesWithOnlyTheRest() =
        runBlocking {
            val subjects = List(3_000) { hex() }
            val cards = subjects.mapIndexed { i, s -> card(s, 50, batchTime + i) }

            // What a download cut off after 1,000 cards had, plus a card the relay has dropped since.
            val dropped = card(hex(), 50, batchTime - 5)
            val partialBuilder = TrustNetworkBuilder(provider.pubKey)
            (cards.takeLast(1_000) + dropped).forEach { partialBuilder.add(it) }
            val (partialIndex, partialIds) = partialBuilder.build()
            val partial = TrustNetworkPartial(TrustNetworkHeader(provider.pubKey, "ws://127.0.0.1:7790/", 0, 0, 0), partialIndex, partialIds)

            val result =
                withRelay(cards) { _, client, relay ->
                    client.downloadTrustNetwork(provider.pubKey, relay, resumeFrom = partial)
                }
            assertTrue(result.complete, result.detail)
            assertTrue(result.detail!!.contains("need 2000"), "only the rest is fetched: ${result.detail}")
            assertEquals(3_000, result.index.size)
            assertNull(result.index.rankOf(dropped.tags.first { it[0] == "d" }[1]), "a card the relay dropped since does not come back")
            assertEquals(batchTime + 2_999, result.header.syncCursor)
        }
}
