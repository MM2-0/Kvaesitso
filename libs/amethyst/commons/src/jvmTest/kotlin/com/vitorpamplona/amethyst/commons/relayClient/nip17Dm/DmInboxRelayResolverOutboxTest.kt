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
package com.vitorpamplona.amethyst.commons.relayClient.nip17Dm

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the NIP-65 outbox path of [DmInboxRelayResolver.resolve]: when the
 * curated indexers do NOT carry the recipient's kind:10050 but DO know their
 * kind:10002, the resolver must read the 10050 straight from the recipient's
 * declared write (outbox) relays.
 */
class DmInboxRelayResolverOutboxTest {
    private val peer: HexKey = "a".repeat(64)

    private val indexer = NormalizedRelayUrl("wss://indexer.test/")
    private val peerOutbox = NormalizedRelayUrl("wss://peer-write.test/")
    private val peerDmInbox = NormalizedRelayUrl("wss://peer-dm.test/")

    private fun dummySig() = "0".repeat(128)

    private fun outbox10002(write: List<NormalizedRelayUrl>) =
        AdvertisedRelayListEvent(
            id = "b".repeat(64),
            pubKey = peer,
            createdAt = 1_700_000_000,
            tags = write.map { arrayOf("r", it.url, "write") }.toTypedArray(),
            content = "",
            sig = dummySig(),
        )

    private fun dm10050(inbox: List<NormalizedRelayUrl>) =
        DmRelayListEvent(
            id = "c".repeat(64),
            pubKey = peer,
            createdAt = 1_700_000_050,
            tags = DmRelayListEvent.createTagArray(inbox),
            content = "",
            sig = dummySig(),
        )

    /**
     * Replays scripted events per (kind, relay) and auto-EOSEs on subscribe —
     * enough for [fetchAll] which awaits one EOSE per relay.
     */
    private class ScriptedClient(
        delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        private val script = mutableMapOf<Pair<Int, NormalizedRelayUrl>, List<Event>>()
        val queriedRelays = mutableSetOf<NormalizedRelayUrl>()

        fun scriptEvent(
            kind: Int,
            relay: NormalizedRelayUrl,
            events: List<Event>,
        ) {
            script[kind to relay] = events
        }

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            filters.forEach { (relay, filterList) ->
                queriedRelays.add(relay)
                filterList.forEach { filter ->
                    filter.kinds?.forEach { kind ->
                        script[kind to relay]?.forEach { event ->
                            kotlinx.coroutines.runBlocking { listener?.onEvent(event, isLive = false, relay = relay, forFilters = null) }
                        }
                    }
                }
                listener?.onEose(relay, forFilters = null)
            }
        }

        override fun unsubscribe(subId: String) { /* no-op */ }
    }

    @Test
    fun `reads kind 10050 from the recipient write relays when indexers only have their 10002`() =
        runBlocking {
            val client = ScriptedClient()
            // Indexer knows WHERE the peer writes (10002) but not their 10050.
            client.scriptEvent(AdvertisedRelayListEvent.KIND, indexer, listOf(outbox10002(listOf(peerOutbox))))
            // The peer's own write relay serves their 10050.
            client.scriptEvent(DmRelayListEvent.KIND, peerOutbox, listOf(dm10050(listOf(peerDmInbox))))

            val resolver =
                DmInboxRelayResolver(
                    unauthenticatedClient = client,
                    indexerRelays = setOf(indexer),
                    localLookup = { null },
                    outboxLookup = { null },
                )

            val result = resolver.resolve(peer)

            assertEquals(listOf(peerDmInbox), result)
            assertTrue("must read 10050 from the peer's own write relay", peerOutbox in client.queriedRelays)
        }

    @Test
    fun `uses a cached outbox as a phase-1 seed`() =
        runBlocking {
            val client = ScriptedClient()
            // No 10002 on the indexer; the write relay is only known from cache.
            client.scriptEvent(DmRelayListEvent.KIND, peerOutbox, listOf(dm10050(listOf(peerDmInbox))))

            val resolver =
                DmInboxRelayResolver(
                    unauthenticatedClient = client,
                    indexerRelays = setOf(indexer),
                    localLookup = { null },
                    outboxLookup = { listOf(peerOutbox) },
                )

            val result = resolver.resolve(peer)

            assertEquals(listOf(peerDmInbox), result)
            assertTrue("cached outbox relay must be queried in phase 1", peerOutbox in client.queriedRelays)
        }
}
