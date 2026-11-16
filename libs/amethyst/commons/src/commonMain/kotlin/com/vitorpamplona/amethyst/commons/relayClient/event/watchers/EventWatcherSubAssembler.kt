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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relays.EOSEAccountFast
import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

class EventWatcherSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<EventFinderQueryState>,
) : SingleSubEoseManager<EventFinderQueryState>(client, allKeys) {
    var lastNotesOnFilter = emptyList<Note>()

    // Keyed by note id, not by the Note: this assembler lives as long as the app, and a Note key
    // held up to a thousand notes (with their replies and reactions) that LocalCache's soft cache
    // would otherwise let go, and missed a note LocalCache had rebuilt for the same id.
    var latestEOSEs: EOSEAccountFast<HexKey> = EOSEAccountFast(1000)

    override fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        lastNotesOnFilter.forEach {
            latestEOSEs.newEose(it.idHex, relay, time)
        }
        super.newEose(relay, time, filters)
    }

    override fun updateFilter(
        keys: List<EventFinderQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        if (keys.isEmpty()) {
            // Nothing is watched: let go of the last batch rather than holding it until the next.
            lastNotesOnFilter = emptyList()
            return null
        }

        lastNotesOnFilter = keys.map { it.note }

        // Attributed only when one account is watching. The notes here are whatever is rendered, not
        // anything an account owns, so with several accounts active no single one is the honest owner —
        // and splitting would re-request the same replies/zaps once per account.
        // Deduped by pubkey, not by `Account`: that class uses identity equality, so two objects for
        // the same logged-in user would look like two accounts and suppress attribution entirely.
        val soleAccountPubKey =
            keys
                .mapTo(mutableSetOf()) { it.account.userFinderPubkeyHex }
                .singleOrNull()

        return groupByRelayPresence(lastNotesOnFilter, latestEOSEs)
            .map { group ->
                if (group.isNotEmpty()) {
                    val addressables = group.filterIsInstance<AddressableNote>()
                    val events = group.mapNotNull { if (it !is AddressableNote) it else null }

                    listOfNotNull(
                        filterRepliesAndReactionsToNotes(events, findMinimumEOSEs(events, latestEOSEs), soleAccountPubKey),
                        filterRepliesAndReactionsToAddresses(addressables, findMinimumEOSEs(addressables, latestEOSEs), soleAccountPubKey),
                        filterNewVersionsOfAddresses(addressables),
                    ).flatten()
                } else {
                    emptyList()
                }
            }.flatten()
    }

    override fun distinct(key: EventFinderQueryState) = key.note

    fun groupByRelayPresence(
        notes: Iterable<Note>,
        eoseCache: EOSEAccountFast<HexKey>,
    ): Collection<List<Note>> =
        notes
            .groupBy { eoseCache.since(it.idHex)?.keys?.hashCode() }
            .values
            .map {
                // important to keep in order otherwise the Relay thinks the filter has changed and we REQ again
                it.sortedBy { it.idHex }
            }

    fun findMinimumEOSEs(
        notes: List<Note>,
        eoseCache: EOSEAccountFast<HexKey>,
    ): SincePerRelayMap {
        val minLatestEOSEs = mutableMapOf<NormalizedRelayUrl, MutableTime>()

        notes.forEach { note ->
            eoseCache.since(note.idHex)?.forEach {
                val minEose = minLatestEOSEs[it.key]
                if (minEose == null) {
                    minLatestEOSEs.put(it.key, it.value.copy())
                } else {
                    minEose.updateIfOlder(it.value.time)
                }
            }
        }

        return minLatestEOSEs
    }
}
