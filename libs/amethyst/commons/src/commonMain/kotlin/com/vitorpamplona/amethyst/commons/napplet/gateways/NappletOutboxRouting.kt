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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.napplet.OutboxRelayPlan
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * NIP-65 relay choice for NAP-OUTBOX, apart from any account so it can be tested: which relays to
 * read an author from (their write relays) or deliver to them (their read relays), learning the
 * relay lists it lacks through [fetchRelayLists], and checking the relay hints a napplet passes.
 *
 * Bounded, since a napplet chooses the authors: at most [MAX_AUTHORS] authors, [RELAYS_PER_AUTHOR]
 * relays each, [MAX_RELAYS] in all.
 */
class NappletOutboxRouting(
    /** An author's relays from NIP-65 as cached: write relays when [write], read relays otherwise; null when unknown. */
    private val relayListOf: (author: HexKey, write: Boolean) -> List<NormalizedRelayUrl>?,
    /** Learns the relay lists of [authors] (kind 10002), so [relayListOf] knows them after. */
    private val fetchRelayLists: suspend (authors: List<HexKey>) -> Unit,
    /** Where to read when no author narrows it: the user's own relays. */
    private val fallback: () -> Set<NormalizedRelayUrl>,
    /** The logged-in user, whose own relays come from [userRelays] rather than a cached list. */
    private val user: HexKey? = null,
    /**
     * The user's own relays, as the app itself reads ([write]) and delivers to them: all of them,
     * private and local ones included, so a game finds its saves where the app wrote them.
     */
    private val userRelays: (write: Boolean) -> Set<NormalizedRelayUrl> = { emptySet() },
) {
    class Route(
        val relays: Set<NormalizedRelayUrl>,
        val source: OutboxRelayPlan.Source,
        /** Authors whose relay list could not be found. */
        val missing: List<HexKey>,
    ) {
        fun plan() = OutboxRelayPlan(relays.map { it.url }, source, missing)
    }

    /**
     * The relays to read [authors] from ([write], their outboxes) or to deliver to them (their
     * inboxes); the [user] gets all of their own. An author without a known list is fetched once;
     * still missing, it is reported, as is one that isn't a key, past [MAX_AUTHORS], or left without
     * a relay by [MAX_RELAYS]: a read is then incomplete, and a delivery failed. Each author gets one
     * relay before any gets a second. With no relay found for anyone, reads fall back to the user's
     * relays; deliveries never do, since a recipient who never said where they read can't be
     * reached by guessing.
     */
    suspend fun route(
        authors: List<HexKey>,
        write: Boolean,
    ): Route {
        val (keys, invalid) = authors.distinct().partition(::isKey)
        val wanted = keys.take(MAX_AUTHORS)
        val dropped = invalid + keys.drop(MAX_AUTHORS)
        if (wanted.isEmpty()) return Route(if (write) fallback() else emptySet(), OutboxRelayPlan.Source.FALLBACK, dropped)

        val unknown = wanted.filter { it != user && relayListOf(it, write) == null }
        if (unknown.isNotEmpty()) fetchRelayLists(unknown)

        val relays = LinkedHashSet<NormalizedRelayUrl>()
        val missing = dropped.toMutableList()
        // The user's own relays first, so the limit never cuts them.
        if (user != null && user in wanted) {
            val own = userRelays(write)
            if (own.isEmpty()) missing += user else relays += own
        }
        val lists =
            wanted.mapNotNull { author ->
                if (author == user) return@mapNotNull null
                val list = relayListOf(author, write)?.take(RELAYS_PER_AUTHOR)
                if (list.isNullOrEmpty()) {
                    missing += author
                    null
                } else {
                    author to list
                }
            }
        // Everyone's first relay, then everyone's next, until the limit.
        val reached = HashSet<HexKey>()
        for (round in 0 until RELAYS_PER_AUTHOR) {
            for ((author, list) in lists) {
                val relay = list.getOrNull(round) ?: continue
                if (relay in relays || (relays.size < MAX_RELAYS && relays.add(relay))) reached += author
            }
        }
        lists.forEach { (author, _) -> if (author !in reached) missing += author }
        return when {
            relays.isNotEmpty() -> Route(relays, OutboxRelayPlan.Source.NIP65, missing)
            write -> Route(fallback(), OutboxRelayPlan.Source.FALLBACK, missing)
            else -> Route(emptySet(), OutboxRelayPlan.Source.FALLBACK, missing)
        }
    }

    private fun isKey(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        const val MAX_AUTHORS = 100
        const val RELAYS_PER_AUTHOR = 3
        const val MAX_RELAYS = 30
        const val MAX_HINTS = 8

        /**
         * The relay [hints] a napplet passed that the shell may dial: valid relay URLs, never on the
         * machine's own or a private network, a few at most.
         */
        fun checkedHints(hints: List<String>): Set<NormalizedRelayUrl> =
            hints
                .asSequence()
                .filterNot { RelayUrlNormalizer.isLocalHost(it) }
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .filterNot { RelayUrlNormalizer.isLocalHost(it.url) }
                .distinct()
                .take(MAX_HINTS)
                .toSet()
    }
}
