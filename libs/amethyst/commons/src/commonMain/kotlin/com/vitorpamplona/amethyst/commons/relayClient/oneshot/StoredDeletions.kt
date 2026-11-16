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

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent

/** Most values one filter carries; relays reject or truncate bigger filters. */
internal const val MAX_FILTER_VALUES = 500

/**
 * The kind:5s in the store that may cover [events]: those naming an event's id (`#e`) and,
 * for addressable events, its address (`#a`), which deletes every version up to the
 * deletion's `created_at`.
 *
 * Relays hand back events the store refused because a deletion covers them, so anything a
 * loader takes from [OneShotRelayAccess.fetch] must be checked against these.
 */
suspend fun OneShotRelayAccess.storedDeletionsFor(events: Collection<Event>): List<DeletionRequestEvent> {
    if (events.isEmpty()) return emptyList()
    val ids = events.mapTo(LinkedHashSet()) { it.id }
    val addresses = events.mapNotNullTo(LinkedHashSet()) { (it as? AddressableEvent)?.addressTag() }
    val kinds = listOf(DeletionRequestEvent.KIND)
    val found = LinkedHashMap<String, DeletionRequestEvent>()
    ids.chunked(MAX_FILTER_VALUES).forEach { chunk ->
        query(Filter(kinds = kinds, tags = mapOf("e" to chunk))).forEach { if (it is DeletionRequestEvent) found[it.id] = it }
    }
    addresses.chunked(MAX_FILTER_VALUES).forEach { chunk ->
        query(Filter(kinds = kinds, tags = mapOf("a" to chunk))).forEach { if (it is DeletionRequestEvent) found[it.id] = it }
    }
    return found.values.toList()
}

/** [events] without those a deletion in the store covers (NIP-09: same author; by id, or by address up to its date). */
suspend fun <T : Event> OneShotRelayAccess.withoutStoredDeletions(events: List<T>): List<T> {
    val deletions = storedDeletionsFor(events)
    if (deletions.isEmpty()) return events
    val index = DeletionIndex()
    // The store only holds verified events.
    deletions.forEach { index.add(it, wasVerified = true) }
    return events.filter { !index.hasBeenDeleted(it) }
}
