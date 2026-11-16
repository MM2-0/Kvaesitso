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

import com.vitorpamplona.amethyst.commons.relays.dialableWriteRelays
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/** A parsed `hex` / `note1` / `nevent1` / `naddr1` (optionally `nostr:`-prefixed) event reference. */
class EventRef(
    val input: String,
    val filter: Filter,
    val hints: Set<NormalizedRelayUrl>,
    val author: HexKey?,
) {
    companion object {
        /** Parses [input]; anything that does not name an event throws [IllegalArgumentException]. */
        fun parse(input: String): EventRef {
            val code = input.trim().removePrefix("nostr:")
            if (code.length == 64 && code.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                return EventRef(input, Filter(ids = listOf(code.lowercase()), limit = 1), emptySet(), null)
            }
            return when (val entity = Nip19Parser.uriToRoute(code)?.entity) {
                is NNote -> EventRef(input, Filter(ids = listOf(entity.hex), limit = 1), emptySet(), null)
                is NEvent -> EventRef(input, Filter(ids = listOf(entity.hex), limit = 1), entity.relay.toSet(), entity.author)
                is NAddress ->
                    EventRef(
                        input,
                        Filter(
                            kinds = listOf(entity.kind),
                            authors = listOf(entity.author),
                            // A replaceable kind (0, 3, 10000-19999) has no `d` tag: its naddr carries an
                            // empty one, and `#d: [""]` would match nothing. Same rule as FilterMissingAddressables.
                            tags = if (entity.kind < 25000 && entity.dTag.isBlank()) null else mapOf("d" to listOf(entity.dTag)),
                            limit = 1,
                        ),
                        entity.relay.toSet(),
                        entity.author,
                    )
                else -> throw IllegalArgumentException("not an event reference: '$input' (expects a 64-hex id, note1…, nevent1… or naddr1…)")
            }
        }
    }
}

/** An event located through the store or relays, with the relays that served it. */
class LocatedEvent(
    val event: Event,
    val seenOn: Set<NormalizedRelayUrl>,
    /** `cache` when the store answered, `relays` otherwise. */
    val source: String,
)

/**
 * Outbox-model event lookup over a [OneShotRelayAccess]: the store first, then the
 * reference's hint relays, its author's NIP-65 outbox and the bootstrap set.
 */
object EventLocator {
    /**
     * Store-first lookup of [ref], skipping the store when [refresh] is set. Returns the
     * newest match — an `naddr` names a replaceable slot — or null when nobody has it or a
     * deletion in the store covers it. [LocatedEvent.seenOn] is where it came from: the
     * reference's hints on a store hit (the best guess there is), else the relays that served it.
     */
    suspend fun locate(
        access: OneShotRelayAccess,
        ref: EventRef,
        refresh: Boolean,
        timeoutMs: Long,
    ): LocatedEvent? {
        if (!refresh) {
            access.query(ref.filter).maxByOrNull { it.createdAt }?.let {
                return LocatedEvent(it, ref.hints, "cache")
            }
        }

        val relays = ref.hints + (ref.author?.let { authorOutboxRelays(access, it, timeoutMs) } ?: emptySet()) + access.bootstrapRelays()
        val received = access.fetch(relays, ref.filter, timeoutMs).filter { (_, event) -> ref.filter.match(event) }
        val live = access.withoutStoredDeletions(received.map { it.second }.distinctBy { it.id }).mapTo(HashSet()) { it.id }
        val matches = received.filter { it.second.id in live }
        val newest = matches.maxByOrNull { it.second.createdAt }?.second ?: return null
        val seenOn = matches.filter { it.second.id == newest.id }.mapTo(mutableSetOf()) { it.first }
        return LocatedEvent(newest, seenOn, "relays")
    }

    /** [author]'s NIP-65 write relays, fetching their kind:10002 from the bootstrap set on a store miss. */
    suspend fun authorOutboxRelays(
        access: OneShotRelayAccess,
        author: HexKey,
        timeoutMs: Long,
    ): Set<NormalizedRelayUrl> {
        val list =
            access.relayListOf(author) ?: run {
                val filter = Filter(authors = listOf(author), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1)
                access.fetch(access.bootstrapRelays(), filter, timeoutMs)
                access.relayListOf(author)
            }
        return list?.dialableWriteRelays()?.toSet() ?: emptySet()
    }
}
