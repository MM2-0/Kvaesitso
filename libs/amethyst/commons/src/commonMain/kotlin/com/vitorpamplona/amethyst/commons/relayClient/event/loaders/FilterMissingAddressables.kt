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
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun potentialRelaysToFindAddress(
    cache: ICacheProvider,
    note: AddressableNote,
): Set<NormalizedRelayUrl> {
    val set = mutableSetOf<NormalizedRelayUrl>()

    cache.getOrCreateUser(note.address.pubKeyHex)?.outboxRelays()?.let {
        set.addAll(it)
    }

    set.addAll(cache.relayHints.hintsForAddress(note.idHex))

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

    return set
}

fun filterMissingAddressables(
    cache: ICacheProvider,
    keys: List<EventFinderQueryState>,
): List<RelayBasedFilter> {
    val addressesPerRelay =
        mapOfSet {
            keys.forEach { key ->
                val default = key.account.followPlusAllMineWithSearchRelays()
                if (key.note is AddressableNote && key.note.event == null) {
                    potentialRelaysToFindAddress(cache, key.note).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, key.note.address)
                    }
                }

                // loads threading that is event-based
                key.note.replyTo?.forEach { note ->
                    if (note is AddressableNote && note.event == null) {
                        potentialRelaysToFindAddress(cache, note).ifEmpty { default }.forEach { relayUrl ->
                            add(relayUrl, note.address)
                        }
                    }
                }
            }
        }

    return filterMissingAddressables(addressesPerRelay)
}

/**
 * Addressables are looked up by (kind, author, `d`), and a screen usually wants many that share
 * the first two -- every section of a publication, for instance, is one author's one kind. Asking
 * per address turned a 240-section book into 240 filters in a single REQ; grouping puts every `d`
 * in one filter's `#d` list, which is the same query in one line instead of 240.
 *
 * The `limit` is the number of coordinates asked for, not 1: these are replaceable, so a relay
 * holds exactly one event per coordinate and that is the most this filter can return.
 *
 * That `limit` is why the group is chunked at [MAX_VALUES_PER_FILTER]. Relays clamp a filter's
 * limit down to their own `maxLimit` (`LimitsPolicy.applyLimits`), so one filter asking for 240
 * coordinates with `limit = 240` comes back holding only `maxLimit` of them, and the rest go
 * missing with no error to notice. Chunks keep each `limit` small enough to survive the clamp.
 *
 * Written as one pass into nested maps rather than `partition`/`groupBy`/`distinct`/`sorted`
 * chains: this runs on every filter rebuild, several times a second, and each of those operators
 * is another intermediate list. `groupBy { kind to pubKeyHex }` alone allocated a `Pair` and a
 * boxed `Int` per address. No de-duplication is needed either -- the input is a `Set<Address>` and
 * `Address` is a data class, so two entries in one group cannot share a `d`.
 */
fun filterMissingAddressables(missingAddressables: Map<NormalizedRelayUrl, Set<Address>>): List<RelayBasedFilter> = filterAddressesPerRelay(missingAddressables)

/**
 * The filters that ask each relay for the latest version of its addresses, grouped as
 * [filterMissingAddressables] documents. [since] gives an address's lower bound, by kind, author
 * and `d` (the new-versions watcher: just past the version the cache holds); each filter takes the
 * lowest of its addresses', and none at all when one of them has none. Null asks for whatever the
 * relay holds.
 */
fun filterAddressesPerRelay(
    addressesPerRelay: Map<NormalizedRelayUrl, Set<Address>>,
    since: ((kind: Int, author: String, dTag: String) -> Long?)? = null,
): List<RelayBasedFilter> {
    if (addressesPerRelay.isEmpty()) return emptyList()

    val filters = mutableListOf<RelayBasedFilter>()

    addressesPerRelay.forEach { (relay, addresses) ->
        if (addresses.isEmpty()) return@forEach

        // kind -> author -> the `d`s wanted from that author
        var byAuthor: MutableMap<Int, MutableMap<String, MutableList<String>>>? = null
        // A replaceable below 25000 with no `d` is addressed by kind and author alone, so it
        // cannot join a `#d` group. Rare, so the map is only built if one turns up.
        var plain: MutableMap<Int, MutableList<String>>? = null

        addresses.forEach { address ->
            if (address.kind < 25000 && address.dTag.isBlank()) {
                (plain ?: HashMap<Int, MutableList<String>>().also { plain = it })
                    .getOrPut(address.kind) { mutableListOf() }
                    .add(address.pubKeyHex)
            } else {
                (byAuthor ?: HashMap<Int, MutableMap<String, MutableList<String>>>().also { byAuthor = it })
                    .getOrPut(address.kind) { HashMap() }
                    .getOrPut(address.pubKeyHex) { mutableListOf() }
                    .add(address.dTag)
            }
        }

        byAuthor?.forEach { (kind, authors) ->
            val kinds = listOf(kind)
            authors.forEach { (author, dTags) ->
                val authorList = listOf(author)
                dTags.sort()
                forEachChunk(dTags) { chunk ->
                    filters.add(
                        RelayBasedFilter(
                            relay = relay,
                            filter =
                                ExplainedFilter(
                                    purpose = SubPurpose.REFERENCED_EVENTS,
                                    kinds = kinds,
                                    tags = mapOf("d" to chunk),
                                    authors = authorList,
                                    limit = chunk.size,
                                    since = since?.let { lowest(chunk) { d -> it(kind, author, d) } },
                                ),
                        ),
                    )
                }
            }
        }

        plain?.forEach { (kind, pubkeys) ->
            val kinds = listOf(kind)
            pubkeys.sort()
            forEachChunk(pubkeys) { chunk ->
                filters.add(
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            ExplainedFilter(
                                purpose = SubPurpose.REFERENCED_EVENTS,
                                kinds = kinds,
                                authors = chunk,
                                limit = chunk.size,
                                since = since?.let { lowest(chunk) { pubkey -> it(kind, pubkey, "") } },
                            ),
                    ),
                )
            }
        }
    }

    return filters
}

/** The lowest of [values]' bounds; null as soon as one has none. */
private inline fun lowest(
    values: List<String>,
    bound: (String) -> Long?,
): Long? {
    var min = Long.MAX_VALUE
    for (value in values) {
        val b = bound(value) ?: return null
        if (b < min) min = b
    }
    return if (values.isEmpty()) null else min
}

/**
 * Hands [block] each [MAX_VALUES_PER_FILTER]-sized slice of [values], passing the list itself when
 * it already fits -- which is nearly always. `chunked` would allocate an outer list plus a copy
 * even for the single-chunk case.
 */
internal inline fun forEachChunk(
    values: List<String>,
    block: (List<String>) -> Unit,
) {
    if (values.size <= MAX_VALUES_PER_FILTER) {
        block(values)
        return
    }

    var from = 0
    while (from < values.size) {
        val to = minOf(from + MAX_VALUES_PER_FILTER, values.size)
        block(values.subList(from, to))
        from = to
    }
}
