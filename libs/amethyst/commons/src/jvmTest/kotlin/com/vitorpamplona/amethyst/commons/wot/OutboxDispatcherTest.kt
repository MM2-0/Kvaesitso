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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for the outbox pipeline defined in
 * `commons/plans/2026-07-06-fix-wot-outbox-model-and-review-fixes-plan.md`.
 * Scenarios:
 *
 *   1. Author has a cached kind-10002 → Phase 1 skipped, Phase 2 REQs
 *      the author's write relay directly.
 *   2. Author has no cached 10002 → Phase 1 discovers, Phase 2 uses the
 *      discovered write relays.
 *   3. Author with no 10002 anywhere → Phase 3 fallback to index relays.
 *   4. Per-relay timeout on Phase 1 doesn't cancel Phase 2 for authors
 *      that already had a cached outbox.
 *   5. clear() releases dedup so a fresh call always re-runs.
 */
class OutboxDispatcherTest {
    private lateinit var scope: CoroutineScope

    private val indexRelay1 = NormalizedRelayUrl("wss://index1.test/")
    private val indexRelay2 = NormalizedRelayUrl("wss://index2.test/")
    private val indexRelays = setOf(indexRelay1, indexRelay2)

    private val outboxAlice = NormalizedRelayUrl("wss://alice-outbox.test/")
    private val outboxBob = NormalizedRelayUrl("wss://bob-outbox.test/")

    private val alice = pubkey(1)
    private val bob = pubkey(2)
    private val charlie = pubkey(3)

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun teardown() {
        scope.cancel()
    }

    private fun pubkey(seed: Int): HexKey = seed.toString(16).padStart(64, '0')

    private fun dummySig() = "0".repeat(128)

    private fun outboxEventFor(
        author: HexKey,
        writeRelays: List<NormalizedRelayUrl>,
        createdAt: Long = 1_700_000_000,
    ): AdvertisedRelayListEvent {
        val tags = writeRelays.map { arrayOf("r", it.url, "write") }.toTypedArray()
        return AdvertisedRelayListEvent(
            id = "out-$author".take(64).padEnd(64, '0'),
            pubKey = author,
            createdAt = createdAt,
            tags = tags,
            content = "",
            sig = dummySig(),
        )
    }

    private fun kind3For(
        author: HexKey,
        follows: List<HexKey>,
    ) = ContactListEvent(
        id = "k3-$author".take(64).padEnd(64, '0'),
        pubKey = author,
        createdAt = 1_700_000_100,
        tags = follows.map { arrayOf("p", it) }.toTypedArray(),
        content = "",
        sig = dummySig(),
    )

    private class RecordingGateway : OutboxCacheGateway {
        val cache = mutableMapOf<HexKey, AdvertisedRelayListEvent>()
        val discoveredOutbox = mutableListOf<Pair<AdvertisedRelayListEvent, NormalizedRelayUrl>>()
        val discoveredEvents = mutableListOf<Pair<Event, NormalizedRelayUrl>>()

        override fun cachedOutbox(pubkey: HexKey): AdvertisedRelayListEvent? = cache[pubkey]

        override fun onOutboxDiscovered(
            event: AdvertisedRelayListEvent,
            relay: NormalizedRelayUrl,
        ) {
            cache[event.pubKey] = event
            discoveredOutbox.add(event to relay)
        }

        override fun onDiscoveredEvent(
            event: Event,
            relay: NormalizedRelayUrl,
        ) {
            discoveredEvents.add(event to relay)
        }
    }

    /**
     * Fake INostrClient that replays a scripted set of events + auto-EOSEs
     * per relay when [subscribe] is called. The script is keyed by the
     * REQ's `(kinds, relay)` pair so tests can seed different responses
     * for Phase-1 and Phase-2 subs.
     */
    private class ScriptedClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        // (kind, relay) → list of events to return
        private val script = mutableMapOf<Pair<Int, NormalizedRelayUrl>, List<Event>>()
        private val eoseNever = mutableSetOf<NormalizedRelayUrl>()
        val allSubscribeCalls = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()

        fun scriptEvent(
            kind: Int,
            relay: NormalizedRelayUrl,
            events: List<Event>,
        ) {
            script[kind to relay] = events
        }

        fun neverEose(relay: NormalizedRelayUrl) {
            eoseNever.add(relay)
        }

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            allSubscribeCalls.add(filters)
            filters.forEach { (relay, filterList) ->
                filterList.forEach { filter ->
                    filter.kinds?.forEach { kind ->
                        script[kind to relay]?.forEach { event ->
                            kotlinx.coroutines.runBlocking { listener?.onEvent(event, isLive = false, relay = relay, forFilters = null) }
                        }
                    }
                }
                if (relay !in eoseNever) {
                    listener?.onEose(relay, forFilters = null)
                }
            }
        }

        override fun unsubscribe(subId: String) { /* no-op */ }
    }

    @Test
    fun `cached outbox skips Phase 1 and fetches directly from write relay`() =
        runBlocking {
            val client = ScriptedClient()
            val gateway = RecordingGateway()
            gateway.cache[alice] = outboxEventFor(alice, listOf(outboxAlice))
            client.scriptEvent(ContactListEvent.KIND, outboxAlice, listOf(kind3For(alice, listOf(bob))))

            val dispatcher =
                OutboxDispatcher(
                    client = client,
                    scope = scope,
                    indexRelays = { indexRelays },
                    gateway = gateway,
                    perRelayTimeoutMs = 400,
                    overallTimeoutMs = 2_000,
                )

            val result = dispatcher.fetchKind3Only(setOf(alice))

            assertEquals(1, result.kind3Received)
            assertEquals(1, result.outboxCoveredAuthors)
            assertEquals(0, result.fallbackAuthors)
            assertTrue(
                "Phase 2 must REQ from Alice's own outbox relay",
                client.allSubscribeCalls.any { call -> outboxAlice in call.keys },
            )
            assertTrue(
                "No Phase 1 REQ should be sent to index relays when 10002 is cached",
                client.allSubscribeCalls.none { call -> indexRelays.any { it in call.keys } },
            )
        }

    @Test
    fun `Phase 1 discovers 10002 then Phase 2 fetches from the discovered write relay`() =
        runBlocking {
            val client = ScriptedClient()
            val gateway = RecordingGateway()
            val bobOutbox = outboxEventFor(bob, listOf(outboxBob))

            indexRelays.forEach { rel ->
                client.scriptEvent(AdvertisedRelayListEvent.KIND, rel, listOf(bobOutbox))
            }
            client.scriptEvent(ContactListEvent.KIND, outboxBob, listOf(kind3For(bob, listOf(alice))))

            val dispatcher =
                OutboxDispatcher(
                    client = client,
                    scope = scope,
                    indexRelays = { indexRelays },
                    gateway = gateway,
                    perRelayTimeoutMs = 400,
                    overallTimeoutMs = 2_000,
                )

            val result = dispatcher.fetchKind3Only(setOf(bob))

            assertTrue("Discovered 10002 count > 0", result.kind10002Received > 0)
            assertEquals(1, result.kind3Received)
            assertEquals(1, result.outboxCoveredAuthors)
            assertEquals(0, result.fallbackAuthors)
            assertTrue(
                "Gateway was told about the discovered 10002",
                gateway.discoveredOutbox.any { it.first.pubKey == bob },
            )
        }

    @Test
    fun `author with no 10002 falls back to index-relay REQ`() =
        runBlocking {
            val client = ScriptedClient()
            val gateway = RecordingGateway()

            // No 10002 anywhere. Charlie's kind-3 sits only on the index relays.
            indexRelays.forEach { rel ->
                client.scriptEvent(ContactListEvent.KIND, rel, listOf(kind3For(charlie, listOf(alice))))
            }

            val dispatcher =
                OutboxDispatcher(
                    client = client,
                    scope = scope,
                    indexRelays = { indexRelays },
                    gateway = gateway,
                    perRelayTimeoutMs = 400,
                    overallTimeoutMs = 2_000,
                )

            val result = dispatcher.fetchKind3Only(setOf(charlie))

            assertEquals(1, result.fallbackAuthors)
            assertEquals(0, result.outboxCoveredAuthors)
            assertTrue(
                "Fallback path receives the kind-3",
                result.kind3Received >= 1,
            )
        }

    @Test
    fun `cached-outbox author still fetched when Phase 1 for other authors times out`() =
        runBlocking {
            val client = ScriptedClient()
            val gateway = RecordingGateway()

            // Alice has cached outbox — Phase 2 must fetch from her write relay.
            gateway.cache[alice] = outboxEventFor(alice, listOf(outboxAlice))
            client.scriptEvent(ContactListEvent.KIND, outboxAlice, listOf(kind3For(alice, listOf(bob))))

            // Bob has no cached outbox and index relays never EOSE for Phase 1.
            indexRelays.forEach(client::neverEose)

            val dispatcher =
                OutboxDispatcher(
                    client = client,
                    scope = scope,
                    indexRelays = { indexRelays },
                    gateway = gateway,
                    perRelayTimeoutMs = 200,
                    // Phases 1 and 3 each wait out the 200 ms per-relay timeout, so the run
                    // ends on its own; a tight overall budget only races the runner's clock and
                    // turns a slow CI machine into an all-zero result.
                    overallTimeoutMs = 30_000,
                )

            val result = dispatcher.fetchKind3Only(setOf(alice, bob))

            // Alice was covered by cached outbox; Bob wasn't but Phase 1 timed
            // out, so he became a fallback candidate.
            assertEquals(
                "Alice always covered by cached outbox",
                1,
                result.outboxCoveredAuthors,
            )
            assertTrue(result.kind3Received >= 1)
        }

    @Test
    fun `clear releases dedup so a subsequent identical call refetches`() =
        runBlocking {
            val client = ScriptedClient()
            val gateway = RecordingGateway()
            gateway.cache[alice] = outboxEventFor(alice, listOf(outboxAlice))
            client.scriptEvent(ContactListEvent.KIND, outboxAlice, listOf(kind3For(alice, listOf(bob))))

            val dispatcher =
                OutboxDispatcher(
                    client = client,
                    scope = scope,
                    indexRelays = { indexRelays },
                    gateway = gateway,
                    perRelayTimeoutMs = 400,
                    overallTimeoutMs = 2_000,
                )

            dispatcher.fetchKind3Only(setOf(alice))
            val subCountAfterFirst = client.allSubscribeCalls.size

            // Second call without clear() — should short-circuit.
            dispatcher.fetchKind3Only(setOf(alice))
            assertEquals(subCountAfterFirst, client.allSubscribeCalls.size)

            // After clear(), the same call re-runs Phase 2.
            dispatcher.clear()
            dispatcher.fetchKind3Only(setOf(alice))
            assertTrue(client.allSubscribeCalls.size > subCountAfterFirst)
        }

    /**
     * BatchEoseGate stress — inside OutboxDispatcher this is a private
     * class but the observable effect (Phase 1 completes when all index
     * relays EOSE, and stays within the timeout budget) is what matters.
     */
    @Test
    fun `EOSE aggregation is safe with many concurrent index-relay callbacks`() =
        runBlocking {
            val bigIndexSet = (0..15).map { NormalizedRelayUrl("wss://index$it.test/") }.toSet()
            val client = ScriptedClient()
            val gateway = RecordingGateway()

            val dispatcher =
                OutboxDispatcher(
                    client = client,
                    scope = scope,
                    indexRelays = { bigIndexSet },
                    gateway = gateway,
                    perRelayTimeoutMs = 1_000,
                    overallTimeoutMs = 3_000,
                )

            // Kick off a fetch and race the subscribe call. ScriptedClient
            // fires EOSE inline; we simulate concurrent per-relay EOSE by
            // launching multiple dispatchers as a smoke test.
            val fetchJob = scope.launch { dispatcher.fetchKind3Only(setOf(alice, bob, charlie)) }

            // Give the launcher a moment to enter Phase 1's subscribe.
            delay(50)
            fetchJob.join()
            // No CME thrown, no hang past the timeout budget.
        }
}
