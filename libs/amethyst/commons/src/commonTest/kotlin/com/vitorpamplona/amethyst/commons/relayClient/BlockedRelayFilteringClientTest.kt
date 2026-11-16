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
package com.vitorpamplona.amethyst.commons.relayClient

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BlockedRelayFilteringClientTest {
    private val good = NormalizedRelayUrl("wss://good.example/")
    private val alsoGood = NormalizedRelayUrl("wss://also-good.example/")
    private val blocked = NormalizedRelayUrl("wss://blocked.example/")

    private fun event() =
        Event(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1000,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "c".repeat(64),
        )

    /** Records the relay maps/sets that actually reach the underlying client. */
    private class RecordingClient : INostrClient by EmptyNostrClient() {
        var subscribedFilters: Map<NormalizedRelayUrl, List<Filter>>? = null
        var countFilters: Map<NormalizedRelayUrl, List<Filter>>? = null
        var publishedRelays: Set<NormalizedRelayUrl>? = null

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            subscribedFilters = filters
        }

        override fun count(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
        ) {
            countFilters = filters
        }

        override fun publish(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            publishedRelays = relayList
        }

        override fun publishRaw(
            event: Event,
            eventJson: String,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            publishedRelays = relayList
        }
    }

    @Test
    fun subscribeDropsBlockedRelaysKeepsOthers() {
        val inner = RecordingClient()
        val client = BlockedRelayFilteringClient(inner) { setOf(blocked) }

        client.subscribe(
            "sub",
            mapOf(good to listOf(Filter()), blocked to listOf(Filter()), alsoGood to listOf(Filter())),
            null,
        )

        assertEquals(setOf(good, alsoGood), inner.subscribedFilters?.keys)
    }

    @Test
    fun countDropsBlockedRelays() {
        val inner = RecordingClient()
        val client = BlockedRelayFilteringClient(inner) { setOf(blocked) }

        client.count("cnt", mapOf(good to listOf(Filter()), blocked to listOf(Filter())))

        assertEquals(setOf(good), inner.countFilters?.keys)
    }

    @Test
    fun publishDropsBlockedRelays() {
        val inner = RecordingClient()
        val client = BlockedRelayFilteringClient(inner) { setOf(blocked) }

        client.publish(event(), setOf(good, blocked, alsoGood))

        assertEquals(setOf(good, alsoGood), inner.publishedRelays)
    }

    @Test
    fun publishRawDropsBlockedRelays() {
        val inner = RecordingClient()
        val client = BlockedRelayFilteringClient(inner) { setOf(blocked) }
        val event = event()

        client.publishRaw(event, event.toJson(), setOf(good, blocked, alsoGood))

        assertEquals(setOf(good, alsoGood), inner.publishedRelays)
    }

    @Test
    fun emptyBlockListPassesEverythingThrough() {
        val inner = RecordingClient()
        val client = BlockedRelayFilteringClient(inner) { emptySet() }

        val filters = mapOf(good to listOf(Filter()), blocked to listOf(Filter()))
        client.subscribe("sub", filters, null)
        client.publish(event(), setOf(good, blocked))

        assertEquals(setOf(good, blocked), inner.subscribedFilters?.keys)
        assertEquals(setOf(good, blocked), inner.publishedRelays)
    }

    @Test
    fun blockingEveryRelayYieldsEmptyTargets() {
        val inner = RecordingClient()
        val client = BlockedRelayFilteringClient(inner) { setOf(good, blocked) }

        client.subscribe("sub", mapOf(good to listOf(Filter()), blocked to listOf(Filter())), null)
        client.publish(event(), setOf(good, blocked))

        assertEquals(emptySet(), inner.subscribedFilters?.keys)
        assertEquals(emptySet(), inner.publishedRelays)
    }

    @Test
    fun aBlockListThatTouchesNothingHereIsPassedThroughWithoutCopying() {
        val inner = RecordingClient()
        // Non-empty block list, but none of it is aimed at on this call — the common case once the
        // user has blocked anything at all.
        val client = BlockedRelayFilteringClient(inner) { setOf(blocked) }

        val filters = mapOf(good to listOf(Filter()), alsoGood to listOf(Filter()))
        val relays = setOf(good, alsoGood)
        client.subscribe("sub", filters, null)
        client.publish(event(), relays)

        // Identity, not just equality: reproducing an unchanged map/set costs an allocation and a
        // full copy on a path that runs on every REQ and publish.
        assertSame(filters, inner.subscribedFilters)
        assertSame(relays, inner.publishedRelays)
    }

    @Test
    fun blockSetIsReadPerCallSoLaterChangesApply() {
        val inner = RecordingClient()
        var blockedSet = emptySet<NormalizedRelayUrl>()
        val client = BlockedRelayFilteringClient(inner) { blockedSet }

        client.subscribe("sub", mapOf(good to listOf(Filter()), blocked to listOf(Filter())), null)
        assertEquals(setOf(good, blocked), inner.subscribedFilters?.keys)

        // user blocks a relay after the client was built
        blockedSet = setOf(blocked)
        client.subscribe("sub", mapOf(good to listOf(Filter()), blocked to listOf(Filter())), null)
        assertEquals(setOf(good), inner.subscribedFilters?.keys)
    }
}
