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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.collectUntilEose
import com.vitorpamplona.geode.testing.collectUntilEoseMulti
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.geode.testing.publish
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Compatibility suite that drives the in-process relay through the same
 * `NostrClient` + WebSocket abstraction production code uses. These tests
 * are how we validate that:
 *
 *  1. The relay implements NIP-01 correctly (REQ/EVENT/EOSE/CLOSE/COUNT,
 *     replaceable + parameterized-replaceable, filter matching, multi-relay
 *     pools).
 *  2. The bridge between [com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket]
 *     and [com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer] preserves
 *     wire ordering and lifecycle.
 *
 * The same suite should be runnable, conceptually, against any
 * spec-compliant relay (nostr-rs-relay, strfry, khatru, …) — only the
 * `socketBuilder` and the relay URL would change.
 */
class Nip01ComplianceTest : RelayClientTest() {
    private suspend fun preload(vararg events: Event) {
        defaultRelay.preload(*events)
    }

    private fun fakeEvent(
        idSeed: Int,
        kind: Int = 1,
        pubKey: String = SyntheticEvents.hexId(0),
        createdAt: Long = idSeed.toLong(),
        tags: Array<Array<String>> = emptyArray(),
        content: String = "",
    ) = SyntheticEvents.fakeEvent(idSeed, kind, pubKey, createdAt, content, tags)

    // -- Subscriptions -------------------------------------------------------

    /** REQ returns events matching kinds, then EOSE, in order. */
    @Test
    fun reqByKindReturnsMatchesThenEose() =
        runBlocking {
            preload(
                fakeEvent(1, kind = 1),
                fakeEvent(2, kind = 4),
                fakeEvent(3, kind = 1),
            )

            val (events, eose) = client.collectUntilEose(defaultRelayUrl, Filter(kinds = listOf(1)))

            assertEquals(2, events.size)
            assertEquals(setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(3)), events.map { it.id }.toSet())
            assertTrue(eose, "EOSE should fire after stored events")
        }

    /** REQ honours `limit` — newest first, capped to limit. */
    @Test
    fun reqRespectsLimitAndOrdersNewestFirst() =
        runBlocking {
            preload(
                fakeEvent(1, kind = 1, createdAt = 100),
                fakeEvent(2, kind = 1, createdAt = 200),
                fakeEvent(3, kind = 1, createdAt = 300),
                fakeEvent(4, kind = 1, createdAt = 400),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(kinds = listOf(1), limit = 2))

            assertEquals(2, events.size)
            assertEquals(400L, events[0].createdAt)
            assertEquals(300L, events[1].createdAt)
        }

    /** REQ with `authors` filters by pubkey. */
    @Test
    fun reqFiltersByAuthors() =
        runBlocking {
            val alice = SyntheticEvents.hexId(101)
            val bob = SyntheticEvents.hexId(102)
            preload(
                fakeEvent(1, kind = 1, pubKey = alice),
                fakeEvent(2, kind = 1, pubKey = bob),
                fakeEvent(3, kind = 1, pubKey = alice),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(authors = listOf(alice)))

            assertEquals(2, events.size)
            assertTrue(events.all { it.pubKey == alice })
        }

    /** REQ with `ids` returns only the requested events. */
    @Test
    fun reqFiltersByIds() =
        runBlocking {
            preload(fakeEvent(1), fakeEvent(2), fakeEvent(3))

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(ids = listOf(SyntheticEvents.hexId(2))))

            assertEquals(1, events.size)
            assertEquals(SyntheticEvents.hexId(2), events[0].id)
        }

    /** REQ with `since`/`until` filters on createdAt. */
    @Test
    fun reqFiltersBySinceAndUntil() =
        runBlocking {
            preload(
                fakeEvent(1, createdAt = 100),
                fakeEvent(2, createdAt = 200),
                fakeEvent(3, createdAt = 300),
                fakeEvent(4, createdAt = 400),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(since = 150L, until = 350L))

            assertEquals(setOf(200L, 300L), events.map { it.createdAt }.toSet())
        }

    /** REQ with single-letter `#e` tag filter matches events whose tag values intersect. */
    @Test
    fun reqFiltersByETag() =
        runBlocking {
            val target = SyntheticEvents.hexId(999)
            preload(
                fakeEvent(1, tags = arrayOf(arrayOf("e", target))),
                fakeEvent(2, tags = arrayOf(arrayOf("e", SyntheticEvents.hexId(7)))),
                fakeEvent(3, tags = arrayOf(arrayOf("e", target), arrayOf("p", SyntheticEvents.hexId(8)))),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(tags = mapOf("e" to listOf(target))))

            assertEquals(setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(3)), events.map { it.id }.toSet())
        }

    /** REQ with `#p` tag filter — same single-letter machinery as `#e`. */
    @Test
    fun reqFiltersByPTag() =
        runBlocking {
            val targetPubkey = SyntheticEvents.hexId(2222)
            preload(
                fakeEvent(1, tags = arrayOf(arrayOf("p", targetPubkey))),
                fakeEvent(2, tags = arrayOf(arrayOf("p", SyntheticEvents.hexId(3333)))),
                fakeEvent(3, tags = arrayOf(arrayOf("p", targetPubkey), arrayOf("e", SyntheticEvents.hexId(99)))),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(tags = mapOf("p" to listOf(targetPubkey))))

            assertEquals(setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(3)), events.map { it.id }.toSet())
        }

    /** REQ with a generic single-letter tag (e.g. `#t`) for hashtags. */
    @Test
    fun reqFiltersByGenericSingleLetterTag() =
        runBlocking {
            preload(
                fakeEvent(1, tags = arrayOf(arrayOf("t", "nostr"))),
                fakeEvent(2, tags = arrayOf(arrayOf("t", "bitcoin"))),
                fakeEvent(3, tags = arrayOf(arrayOf("t", "nostr"), arrayOf("t", "kotlin"))),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(tags = mapOf("t" to listOf("nostr"))))

            assertEquals(setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(3)), events.map { it.id }.toSet())
        }

    /**
     * Multi-value tag filter — matches events with tag value in the OR
     * set. NIP-01 says values inside a single filter list are OR'd.
     */
    @Test
    fun reqTagFilterValuesAreOred() =
        runBlocking {
            val a = SyntheticEvents.hexId(101)
            val b = SyntheticEvents.hexId(102)
            preload(
                fakeEvent(1, tags = arrayOf(arrayOf("e", a))),
                fakeEvent(2, tags = arrayOf(arrayOf("e", b))),
                fakeEvent(3, tags = arrayOf(arrayOf("e", SyntheticEvents.hexId(999)))),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(tags = mapOf("e" to listOf(a, b))))

            assertEquals(setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(2)), events.map { it.id }.toSet())
        }

    // -- Multi-filter REQ ----------------------------------------------------

    /**
     * NIP-01: filters within a single REQ are OR'd. The relay returns
     * events matching ANY of the filters, deduplicated.
     */
    @Test
    fun reqMultipleFiltersAreOred() =
        runBlocking {
            preload(
                fakeEvent(1, kind = 1),
                fakeEvent(2, kind = 4),
                fakeEvent(3, kind = 7),
            )

            val (events, _) =
                client.collectUntilEoseMulti(
                    defaultRelayUrl,
                    listOf(
                        Filter(kinds = listOf(1)),
                        Filter(kinds = listOf(7)),
                    ),
                )

            assertEquals(
                setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(3)),
                events.map { it.id }.toSet(),
            )
        }

    /**
     * Two subscriptions on the same connection are independent — each
     * gets its own EOSE and its own event stream.
     */
    @Test
    fun multipleSubscriptionsOnOneConnectionAreIndependent() =
        runBlocking {
            preload(
                fakeEvent(1, kind = 1),
                fakeEvent(2, kind = 4),
            )

            val ch1 = Channel<Event>(UNLIMITED)
            val ch2 = Channel<Event>(UNLIMITED)
            val eose1 = Channel<Unit>(UNLIMITED)
            val eose2 = Channel<Unit>(UNLIMITED)

            client.subscribe(
                "sub-A",
                mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(1)))),
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        ch1.trySend(event)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        eose1.trySend(Unit)
                    }
                },
            )
            client.subscribe(
                "sub-B",
                mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(4)))),
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        ch2.trySend(event)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        eose2.trySend(Unit)
                    }
                },
            )

            withTimeout(5000) {
                eose1.receive()
                eose2.receive()
            }

            // sub-A only saw kind=1, sub-B only saw kind=4.
            val a = withTimeoutOrNull(100) { ch1.receive() }
            val b = withTimeoutOrNull(100) { ch2.receive() }
            assertEquals(SyntheticEvents.hexId(1), a?.id)
            assertEquals(SyntheticEvents.hexId(2), b?.id)

            client.unsubscribe("sub-A")
            client.unsubscribe("sub-B")
        }

    // -- Replaceable + addressable ------------------------------------------

    /** Kind 0 is replaceable by `(pubkey, kind)` — newer wins. */
    @Test
    fun replaceableEventsKeepNewestPerPubkey() =
        runBlocking {
            val pubkey = SyntheticEvents.hexId(50)
            preload(
                fakeEvent(1, kind = 0, pubKey = pubkey, createdAt = 100, content = "old"),
                fakeEvent(2, kind = 0, pubKey = pubkey, createdAt = 200, content = "new"),
            )

            val (events, _) = client.collectUntilEose(defaultRelayUrl, Filter(kinds = listOf(0), authors = listOf(pubkey)))

            assertEquals(1, events.size)
            assertEquals("new", events[0].content)
        }

    /**
     * Addressable events (kind 30000-39999, NIP-01 §"Kinds") are replaced
     * by `(pubkey, kind, d)`. Uses a real signed [LongFormContentEvent] because
     * the SQLite store dispatches on the typed `AddressableEvent` subclass
     * to extract the d-tag — synthetic plain `Event`s aren't recognised.
     */
    @Test
    fun parameterizedReplaceableEventsKeepNewestPerDTag() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val v1 = signer.sign(LongFormContentEvent.build("old", "title", dTag = "list-a", createdAt = 100))
            val v2 = signer.sign(LongFormContentEvent.build("new", "title", dTag = "list-a", createdAt = 200))
            val v3 = signer.sign(LongFormContentEvent.build("list-b", "title", dTag = "list-b", createdAt = 100))
            preload(v1, v2, v3)

            val (events, _) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(kinds = listOf(LongFormContentEvent.KIND), authors = listOf(signer.pubKey)),
                )

            assertEquals(2, events.size)
            assertEquals(setOf("new", "list-b"), events.map { it.content }.toSet())
        }

    // -- Live updates --------------------------------------------------------

    /** A subscription receives new matching events that arrive after EOSE. */
    @Test
    fun liveSubscriptionReceivesPostEoseEvents() =
        runBlocking {
            val ch = Channel<Event>(UNLIMITED)
            val gotEose = Channel<Unit>(UNLIMITED)
            client.subscribe(
                "live-1",
                mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(1)))),
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        ch.trySend(event)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gotEose.trySend(Unit)
                    }
                },
            )

            withTimeout(5000) { gotEose.receive() }

            // Inject an event through the wire path (not preload — that bypasses
            // the live broadcast that subscriptions feed off of).
            defaultRelay.publish(fakeEvent(99, kind = 1, content = "live"))

            val received = withTimeout(5000) { ch.receive() }
            assertEquals("live", received.content)
            client.unsubscribe("live-1")
        }

    /** Non-matching live events are not pushed to a subscription. */
    @Test
    fun liveSubscriptionIgnoresNonMatchingEvents() =
        runBlocking {
            val ch = Channel<Event>(UNLIMITED)
            val gotEose = Channel<Unit>(UNLIMITED)
            client.subscribe(
                "live-2",
                mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(1)))),
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        ch.trySend(event)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gotEose.trySend(Unit)
                    }
                },
            )
            withTimeout(5000) { gotEose.receive() }

            defaultRelay.publish(fakeEvent(98, kind = 4, content = "off-topic"))

            val seen = withTimeoutOrNull(500) { ch.receive() }
            assertNull(seen, "kind 4 should not match a kind-1 subscription")
            client.unsubscribe("live-2")
        }

    // -- Ephemeral events (NIP-01: kinds 20000-29999) ----------------------

    /**
     * Ephemeral events MUST be forwarded to active subscriptions whose
     * filters match, even though the relay does not persist them.
     */
    @Test
    fun ephemeralEventForwardedToActiveSubscription() =
        runBlocking {
            val ch = Channel<Event>(UNLIMITED)
            val gotEose = Channel<Unit>(UNLIMITED)
            client.subscribe(
                "eph-1",
                mapOf(defaultRelayUrl to listOf(Filter(kinds = listOf(20_001)))),
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        ch.trySend(event)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gotEose.trySend(Unit)
                    }
                },
            )
            withTimeout(5000) { gotEose.receive() }

            defaultRelay.publish(fakeEvent(70, kind = 20_001, content = "ephemeral-payload"))

            val received = withTimeout(5000) { ch.receive() }
            assertEquals("ephemeral-payload", received.content)
            assertEquals(20_001, received.kind)
            client.unsubscribe("eph-1")
        }

    /**
     * Ephemeral events MUST NOT be persisted. A REQ issued after the
     * event was published returns nothing.
     */
    @Test
    fun ephemeralEventIsNotStoredAndDoesNotShowOnFollowupReq() =
        runBlocking {
            // Publish ephemeral first — no live subscriber listening.
            defaultRelay.publish(fakeEvent(71, kind = 20_002, content = "vanish"))

            // Late subscriber: should see EOSE with no events.
            val (events, eose) = client.collectUntilEose(defaultRelayUrl, Filter(kinds = listOf(20_002)))
            assertTrue(eose, "EOSE must fire for an ephemeral kind even if zero events match")
            assertEquals(0, events.size, "Ephemeral events must not be persisted")
        }

    // -- Multi-relay --------------------------------------------------------

    /** A single client can hold subscriptions against multiple relays simultaneously. */
    @Test
    fun multiRelayPoolReturnsContentFromEachRelay() =
        runBlocking {
            val relayA = RelayUrlNormalizer.normalize("ws://127.0.0.1:7771/")
            val relayB = RelayUrlNormalizer.normalize("ws://127.0.0.1:7772/")
            hub.getOrCreate(relayA).preload(fakeEvent(1, kind = 1, content = "from-a"))
            hub.getOrCreate(relayB).preload(fakeEvent(2, kind = 1, content = "from-b"))

            // Each relay delivers its EVENT/EOSE on its own InProcessWebSocket
            // scope, so these callbacks fire concurrently from two threads. The
            // shared collections MUST be thread-safe — a plain HashMap loses a
            // write when both threads put during a rehash, leaving a relay's
            // entry null and flaking the assertions below.
            val received = ConcurrentHashMap<NormalizedRelayUrl, String>()
            val eosed = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()
            val ch = Channel<Unit>(UNLIMITED)
            client.subscribe(
                "multi-1",
                mapOf(
                    relayA to listOf(Filter(kinds = listOf(1))),
                    relayB to listOf(Filter(kinds = listOf(1))),
                ),
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        received[relay] = event.content
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        eosed += relay
                        if (eosed.size == 2) ch.trySend(Unit)
                    }
                },
            )

            withTimeout(5000) { ch.receive() }
            client.unsubscribe("multi-1")

            assertEquals("from-a", received[relayA])
            assertEquals("from-b", received[relayB])
        }
}
