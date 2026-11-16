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
package com.vitorpamplona.amethyst.commons.relayClient.event.loaders

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun potentialRelaysToFindEvent(
    cache: ICacheProvider,
    note: Note,
): Set<NormalizedRelayUrl> {
    val set = mutableSetOf<NormalizedRelayUrl>()

    set.addAll(cache.relayHints.hintsForEvent(note.idHex))

    note.author?.outboxRelays()?.let { set.addAll(it) }

    cache.getAnyChannel(note)?.relays()?.let { set.addAll(it) }

    note.replyTo?.forEach { parentNote ->
        set.addAll(parentNote.relays)

        cache.getAnyChannel(parentNote)?.relays()?.let { set.addAll(it) }

        parentNote.author?.inboxRelays()?.let { set.addAll(it) }
    }

    note.replies.forEach { childNote ->
        set.addAll(childNote.relays)

        cache.getAnyChannel(childNote)?.relays()?.let { set.addAll(it) }

        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    note.reactions.map { reactionType ->
        reactionType.value.forEach { childNote ->
            set.addAll(childNote.relays)
            childNote.author?.outboxRelays()?.let { set.addAll(it) }
        }
    }

    note.boosts.forEach { childNote ->
        set.addAll(childNote.relays)
        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    note.inGatherers?.forEach { parent ->
        // loads from parent's relays, parent's authors relays and cited authors in the parent note.
        // as well as relays from the channel and other fixed places.
        when (parent) {
            is Note -> {
                set.addAll(parent.relays)
                parent.author?.outboxRelays()?.let { set.addAll(it) }
                parent.author?.inboxRelays()?.let { set.addAll(it) }

                val noteEvent = parent.event
                if (noteEvent is PubKeyHintProvider) {
                    noteEvent.linkedPubKeys().forEach { potentialAuthor ->
                        cache.checkGetOrCreateUser(potentialAuthor)?.let { potentialAuthor ->
                            potentialAuthor.outboxRelays()?.let { set.addAll(it) }
                            potentialAuthor.inboxRelays()?.let { set.addAll(it) }
                        }
                    }
                }
            }

            is Channel -> {
                set.addAll(parent.relays())
            }
        }
    }

    return set
}

fun filterMissingEvents(
    cache: ICacheProvider,
    keys: List<EventFinderQueryState>,
): List<RelayBasedFilter> {
    val eventsPerRelay =
        mapOfSet {
            keys.forEach { key ->
                val default = key.account.followPlusAllMineWithSearchRelays()

                if (key.note !is AddressableNote && key.note.event == null) {
                    potentialRelaysToFindEvent(cache, key.note).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }

                    key.account.searchOnlyRelays().forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }
                }

                // loads threading that is event-based
                key.note.replyTo?.forEach { note ->
                    if (note !is AddressableNote && note.event == null) {
                        potentialRelaysToFindEvent(cache, note).ifEmpty { default }.forEach { relayUrl ->
                            add(relayUrl, note.idHex)
                        }
                    }
                }
            }
        }

    return filterMissingEvents(eventsPerRelay)
}

/**
 * One filter per relay carrying every id it might hold, chunked at [MAX_VALUES_PER_FILTER].
 *
 * Split for the same reason as the addressable groups: a relay that clamps `limit` returns only
 * part of a large batch. This path sets no `limit`, so it is exposed only through a relay's
 * `defaultLimit`, but the failure is the same shape and silent either way.
 */
fun filterMissingEvents(missingEventIds: Map<NormalizedRelayUrl, Set<String>>): List<RelayBasedFilter> {
    if (missingEventIds.isEmpty()) return emptyList()

    val filters = mutableListOf<RelayBasedFilter>()

    missingEventIds.forEach { (relay, ids) ->
        if (ids.isEmpty()) return@forEach

        // Sorted so a rebuild that found the same ids produces the same filter and the
        // subscription is not torn down and re-sent for nothing.
        val sorted = ids.toMutableList()
        sorted.sort()

        forEachChunk(sorted) { chunk ->
            filters.add(
                RelayBasedFilter(
                    relay = relay,
                    filter = ExplainedFilter(purpose = SubPurpose.REFERENCED_EVENTS, ids = chunk),
                ),
            )
        }
    }

    return filters
}

/**
 * How many ids or `d` values go in one filter before it is split.
 *
 * Matches the chunk size `OutboxDispatcher` and `FeedMetadataCoordinator` already use for bulk
 * author queries. Note that no relay is known to reject a filter for carrying too many values --
 * neither NIP-11 nor [com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits] has
 * such a cap. The limit clamp above is the observed mechanism; this size is convention.
 */
internal const val MAX_VALUES_PER_FILTER = 100
