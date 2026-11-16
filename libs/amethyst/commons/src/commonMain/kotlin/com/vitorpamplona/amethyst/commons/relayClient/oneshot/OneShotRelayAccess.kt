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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * The port the one-shot loaders in this package run over: a local event store plus
 * "ask these relays these filters until they go quiet". A front end without a live
 * subscription manager — amy, a test, a background job — implements it; the loaders
 * hold the logic (store first, what to ask whom, when to stop).
 *
 * [fetch] must verify what it receives and write it to the store [query] reads, so a
 * second pass over the store sees what the relays returned.
 */
interface OneShotRelayAccess {
    /** Events the local store holds for [filter], newest first, honouring its limit. */
    suspend fun query(filter: Filter): List<Event>

    /**
     * Sends each relay its filters, collects until EOSE or [timeoutMs] of silence, and
     * returns what came back with the relay that served it (already verified and stored).
     */
    suspend fun fetch(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        timeoutMs: Long,
    ): List<Pair<NormalizedRelayUrl, Event>>

    /** Where to look up someone we know nothing about: our own relays plus well-known defaults. */
    suspend fun bootstrapRelays(): Set<NormalizedRelayUrl>

    /** Relays that index profiles (kind:0) and relay lists. */
    suspend fun indexRelays(): Set<NormalizedRelayUrl>
}

/** [pubKey]'s newest NIP-65 relay list in the store. */
suspend fun OneShotRelayAccess.relayListOf(pubKey: HexKey): AdvertisedRelayListEvent? = query(Filter(authors = listOf(pubKey), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1)).firstOrNull() as? AdvertisedRelayListEvent

/** [pubKey]'s newest follow list in the store. */
suspend fun OneShotRelayAccess.contactListOf(pubKey: HexKey): ContactListEvent? = query(Filter(authors = listOf(pubKey), kinds = listOf(ContactListEvent.KIND), limit = 1)).firstOrNull() as? ContactListEvent

/** Fetches [filter] from every relay in [relays]. */
suspend fun OneShotRelayAccess.fetch(
    relays: Collection<NormalizedRelayUrl>,
    filter: Filter,
    timeoutMs: Long,
): List<Pair<NormalizedRelayUrl, Event>> = if (relays.isEmpty()) emptyList() else fetch(relays.associateWith { listOf(filter) }, timeoutMs)
