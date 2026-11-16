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
package com.vitorpamplona.amethyst.commons.relayClient.oneshot

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

fun relay(name: String): NormalizedRelayUrl = RelayUrlNormalizer.normalizeOrNull("wss://$name.example.com")!!

/**
 * An in-memory [OneShotRelayAccess]: a store, plus relays that each hold a set of events.
 * [fetch] writes what it returns into the store, as the real port must, and records
 * every request so tests can assert who was asked what.
 */
class FakeRelayAccess(
    private val bootstrap: Set<NormalizedRelayUrl> = setOf(relay("bootstrap")),
    private val index: Set<NormalizedRelayUrl> = setOf(relay("index")),
) : OneShotRelayAccess {
    val store = mutableListOf<Event>()
    val relays = mutableMapOf<NormalizedRelayUrl, MutableList<Event>>()
    val requests = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()

    fun storeAll(vararg events: Event) = events.forEach { if (store.none { s -> s.id == it.id }) store += it }

    fun publish(
        on: NormalizedRelayUrl,
        vararg events: Event,
    ) {
        relays.getOrPut(on) { mutableListOf() } += events
    }

    override suspend fun query(filter: Filter): List<Event> {
        val matches = store.filter { filter.match(it) }.sortedByDescending { it.createdAt }
        return filter.limit?.let { matches.take(it) } ?: matches
    }

    override suspend fun fetch(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        timeoutMs: Long,
    ): List<Pair<NormalizedRelayUrl, Event>> {
        requests += filters
        return filters
            .flatMap { (url, list) ->
                // Like a relay: each filter answers its newest matches, up to its limit.
                list
                    .flatMap { filter ->
                        val matches = relays[url].orEmpty().filter { filter.match(it) }.sortedByDescending { it.createdAt }
                        filter.limit?.let { matches.take(it) } ?: matches
                    }.distinctBy { it.id }
                    .map { url to it }
            }.also { received -> received.forEach { storeAll(it.second) } }
    }

    override suspend fun bootstrapRelays() = bootstrap

    override suspend fun indexRelays() = index
}
