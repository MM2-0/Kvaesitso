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
package com.vitorpamplona.geode.interop

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Kotlin counterpart to strfry's `test/syncTest.pl`: stand up two
 * Geode relays on real WebSocket endpoints, give each a partially
 * overlapping corpus, and converge them via NIP-77.
 *
 * The driver mirrors `strfry sync ws://other --dir both`:
 *
 *  1. Read the local relay's snapshot for the negotiated filter.
 *  2. Open NEG-OPEN against the remote with that snapshot.
 *  3. Drive NEG-MSG round trips until the client-side
 *     [com.vitorpamplona.quartz.nip77Negentropy.NegentropySession]
 *     reports completion.
 *  4. `needIds`: REQ them from the remote, insert into the local relay.
 *  5. `haveIds`: fetch from the local relay, publish to the remote.
 *
 * After the round, both relays must hold the union of the original
 * corpora. We assert via REQ on each side; an `id`-filter that returns
 * every event we expect, and nothing more, proves convergence
 * end-to-end through the NIP-77 server pipeline (`NegSessionRegistry`
 * → `NegentropyServerSession` → `IEventStore.snapshotIdsForNegentropy`).
 *
 * Equivalent to strfry's `runSyncTests.pl` "full DB sync" case at
 * small scale. Larger corpora belong in `LoadBenchmark`.
 */
class GeodeVsGeodeNegentropySyncTest {
    private lateinit var relayA: RelayEngine
    private lateinit var relayB: RelayEngine
    private lateinit var serverA: KtorRelay
    private lateinit var serverB: KtorRelay
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient
    private val httpClient = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        // The placeholder URLs are normalised so the relay accepts
        // them; ports come from the autobind below via [server.url].
        relayA = RelayEngine(url = "ws://127.0.0.1:7771/".normalizeRelayUrl())
        relayB = RelayEngine(url = "ws://127.0.0.1:7772/".normalizeRelayUrl())
        serverA = KtorRelay(relayA, host = "127.0.0.1", port = 0).start()
        serverB = KtorRelay(relayB, host = "127.0.0.1", port = 0).start()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> httpClient }, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        serverA.stop()
        serverB.stop()
        relayA.close()
        relayB.close()
    }

    /** Generates [count] signed text notes with monotonic createdAt. */
    private fun makeEvents(
        count: Int,
        seed: Long = 1_700_000_000L,
    ): List<Event> {
        val signer = NostrSignerSync(KeyPair())
        return List(count) { i ->
            signer.sign(TextNoteEvent.build("event-$seed-$i", createdAt = seed + i))
        }
    }

    private val driver by lazy { InteropSyncDriver(httpClient) }

    @Test
    fun bidirectionalSyncConvergesTwoRelays() =
        runBlocking {
            // Universe of 20 events. Relay A holds [0..14], Relay B
            // holds [5..19]. Overlap [5..14], A-only [0..4], B-only
            // [15..19]. After bidirectional sync both must hold [0..19].
            val all = makeEvents(20)
            val aEvents = all.subList(0, 15)
            val bEvents = all.subList(5, 20)
            relayA.preload(aEvents)
            relayB.preload(bEvents)

            val filter = Filter(kinds = listOf(1))
            val urlA = serverA.url.normalizeRelayUrl()
            val urlB = serverB.url.normalizeRelayUrl()

            // B negotiates the symmetric difference with A.
            val diff = driver.negotiate(serverA.url, filter, bEvents)
            assertNull(diff.error, "negotiation must not error: ${diff.error}")
            // From B's perspective: needs A-only, has B-only.
            assertEquals(
                aEvents.subList(0, 5).map { it.id }.toSet(),
                diff.needIds,
                "B should NEED [0..4] from A",
            )
            assertEquals(
                bEvents.subList(10, 15).map { it.id }.toSet(),
                diff.haveIds,
                "B should announce HAVE for [15..19]",
            )

            // Close the loop: fetch needs from A, push haves to A.
            val needFromA =
                client.fetchAll(relay = urlA, filter = Filter(ids = diff.needIds.toList()))
            relayB.preload(needFromA)

            for (id in diff.haveIds) {
                client.publishAndConfirm(bEvents.first { it.id == id }, setOf(urlA))
            }

            // --- Verify convergence ---
            val expected = all.map { it.id }.toSet()
            assertEquals(expected, idsOnRelay(urlA, filter), "Relay A should hold every event")
            assertEquals(expected, idsOnRelay(urlB, filter), "Relay B should hold every event")
        }

    @Test
    fun negentropyConvergesInBoundedRoundsOnSmallCorpus() =
        runBlocking {
            val all = makeEvents(200)
            relayA.preload(all.subList(0, 150))
            val bEvents = all.subList(50, 200)
            relayB.preload(bEvents)

            val res = driver.negotiate(serverA.url, Filter(kinds = listOf(1)), bEvents)
            assertNull(res.error)
            assertEquals(50, res.needIds.size, "B should need [0..49]")
            assertEquals(50, res.haveIds.size, "B should have [150..199]")
            // strfry typically converges these in ≤5 rounds; ≤16 is
            // generous headroom that still catches regressions.
            assertTrue(res.rounds <= 16, "expected ≤16 NEG-MSG rounds, got ${res.rounds}")
        }

    /** Every event id matching [filter] visible on the relay at [url] via REQ. */
    private suspend fun idsOnRelay(
        url: NormalizedRelayUrl,
        filter: Filter,
    ): Set<HexKey> = client.fetchAll(relay = url, filter = filter).map { it.id }.toSet()
}
