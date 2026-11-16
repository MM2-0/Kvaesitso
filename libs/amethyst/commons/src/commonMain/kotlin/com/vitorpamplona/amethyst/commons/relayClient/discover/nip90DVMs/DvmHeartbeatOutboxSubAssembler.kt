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
package com.vitorpamplona.amethyst.commons.relayClient.discover.nip90DVMs

import com.vitorpamplona.amethyst.commons.relayClient.discover.DiscoveryQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedSubAssembler
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** How many DVM outbox relays the fetcher may open at once; coverage-ranked, so the top relays carry most authors. */
private const val MAX_OUTBOX_RELAYS = 12

/**
 * Batches the announcement authors per DVM outbox relay so the freshness gate sees the beats the
 * DVMs actually publish. DVMs send heartbeats to their own write relays, which need not overlap
 * the user's discovery relays — the global heartbeat REQ (issued on the selection's relays) alone
 * leaves alive DVMs invisible. Their beats exist on the outbox; this fetcher brings them into the
 * same cache slots the gate reads.
 *
 * Authors with no known outbox are skipped, not blocked: the global REQ is their fallback.
 * Relays are coverage-ranked and capped at [maxRelays] so browsing Discover cannot open dozens
 * of sockets; deterministic tie-breaking keeps the chosen set stable across re-issues.
 */
fun dvmHeartbeatOutboxFilters(
    announcements: List<AppDefinitionEvent>,
    outboxRelaysFor: (HexKey) -> Collection<NormalizedRelayUrl>,
    now: Long,
    maxRelays: Int = MAX_OUTBOX_RELAYS,
): List<RelayBasedFilter> {
    if (announcements.isEmpty()) return emptyList()

    val authorsByRelay = mutableMapOf<NormalizedRelayUrl, MutableList<HexKey>>()
    announcements.forEach { app ->
        outboxRelaysFor(app.pubKey).forEach { relay ->
            authorsByRelay.getOrPut(relay) { mutableListOf() }.add(app.pubKey)
        }
    }
    if (authorsByRelay.isEmpty()) return emptyList()

    val since = now - DvmHeartbeatEvent.MAX_AGE_SECONDS
    return authorsByRelay
        .entries
        .sortedWith(
            compareByDescending<MutableMap.MutableEntry<NormalizedRelayUrl, MutableList<HexKey>>> { it.value.size }
                .thenBy { it.key.url },
        ).take(maxRelays)
        .map { (relay, authors) ->
            val covered = authors.distinct().sorted()
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.DISCOVER_FEED,
                        kinds = listOf(DvmHeartbeatEvent.KIND),
                        authors = covered,
                        limit = covered.size,
                        since = since,
                    ),
            )
        }
}

/**
 * Cache-backed inputs the outbox fetcher needs, provided by the front end (the cache query and
 * relay-hint surface are platform caches, not commons).
 *
 * [announcements] MUST be the ungated announcement set (every cached content-discovery DVM). The
 * gated feed list would turn any transient staleness into a permanent drop: a DVM leaves the
 * gated list the moment its beat ages out, the fetcher would stop covering it, and no beat would
 * ever arrive to bring it back.
 *
 * [outboxRelaysFor] resolves where a DVM publishes its beats — NIP-65 outbox relays plus any
 * relay hints for the author (the same mix the event finder uses).
 *
 * [changes] drive re-issues: when the cached announcement set or the outbox data moves, the
 * batches are recomputed.
 */
class DvmHeartbeatSources(
    val announcements: () -> List<AppDefinitionEvent>,
    val outboxRelaysFor: (HexKey) -> Collection<NormalizedRelayUrl>,
    val changes: List<Flow<*>>,
)

/**
 * The discovery-side subscription that runs [dvmHeartbeatOutboxFilters] for the cached
 * announcement set, alive while the Discover screen is composed (it joins the same assembler
 * group and lifecycle as the other discovery sub-assemblers).
 *
 * No floor collectors ([floors] is empty) — the announcement set and outbox data ([changes]) are
 * the drivers, not note timestamps.
 */
class DiscoveryDvmHeartbeatSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<DiscoveryQueryState>,
    private val sources: DvmHeartbeatSources,
) : TopNavFeedSubAssembler<DiscoveryQueryState>(client, allKeys) {
    override fun updateFilter(
        key: DiscoveryQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> =
        dvmHeartbeatOutboxFilters(
            sources.announcements(),
            outboxRelaysFor = sources.outboxRelaysFor,
            now = TimeUtils.now(),
        )

    override fun floors(key: DiscoveryQueryState): List<StateFlow<Long?>> = emptyList()

    override fun extraInvalidators(key: DiscoveryQueryState): List<Flow<*>> = sources.changes
}
