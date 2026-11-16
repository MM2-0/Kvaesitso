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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.mapOfSet

/**
 * Asks a repository's relays for what decides each of its items' state ([RepositoryItemEssentialKinds],
 * by the items' ids), as gitworkshop does for every item it lists. Without it, the statuses, labels
 * and cover notes that carry no `a` reached an item only when its thread was opened, and the
 * Open / Closed counts were taken over whatever happened to be loaded.
 */
class RepositoryItemEssentialsSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<RepositoryQueryState>,
) : SingleSubEoseManager<RepositoryQueryState>(client, allKeys) {
    // The items whose history each relay has already sent: their ids were in a filter the relay
    // answered with EOSE. Those are asked only for what is newer than the relay's cursor; a newly
    // listed item is asked for its whole history in a filter of its own. Dropping the cursor for
    // every id instead, as a grown key set usually does, re-downloaded every listed item's
    // statuses, labels and cover notes each time a batch of items arrived.
    private val lock = KmpLock()
    private val coveredPerRelay = HashMap<NormalizedRelayUrl, Set<HexKey>>()

    override fun updateFilter(
        keys: List<RepositoryQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        val idsPerRelay =
            mapOfSet {
                keys.forEach { key ->
                    val event = key.repository.event as? GitRepositoryEvent ?: return@forEach
                    val ids = key.currentItemIds.value
                    if (ids.isEmpty()) return@forEach
                    repositoryRelays(cache, key.repository, event).forEach { relay -> ids.forEach { add(relay, it) } }
                }
            }

        return idsPerRelay.flatMap { (relay, ids) ->
            val covered = lock.withLock { coveredPerRelay[relay] }.orEmpty()
            val (known, fresh) = ids.partition { it in covered }
            val cursor = since?.get(relay)?.time
            filterRepositoryItemEssentials(relay, known, cursor) + filterRepositoryItemEssentials(relay, fresh, null)
        }
    }

    override fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        // Every item id in the filters this EOSE answers now has its history on this relay.
        val answered = filters.orEmpty().flatMapTo(HashSet()) { filter -> filter.tags?.get("e").orEmpty() + filter.tags?.get("E").orEmpty() }
        if (answered.isNotEmpty()) {
            lock.withLock { coveredPerRelay[relay] = coveredPerRelay[relay].orEmpty() + answered }
        }
        super.newEose(relay, time, filters)
    }

    override fun distinct(key: RepositoryQueryState) = key.repository.idHex
}
