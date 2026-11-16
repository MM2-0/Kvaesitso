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
package com.vitorpamplona.amethyst.commons.relayClient.polls.results

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/** One open results screen. Two screens on the same poll share the subscription underneath. */
@Stable
class PollResponsesQueryState(
    val pollId: HexKey,
    override val account: IAccount,
) : AccountScopedQuery

/**
 * The votes behind the poll results screen.
 *
 * The screen already gets the poll's own engagement through the shared event watcher; this adds the
 * one thing that watcher cannot give it — a page of votes big enough to be worth calling "every
 * voter", drawn from the poll's declared relays. Structured like every other current-screen data
 * source ([com.vitorpamplona.amethyst.commons.relayClient.thread.ThreadFilterAssembler]
 * is the closest sibling) so it is lifecycle-aware, deduplicated across screens, and EOSE-tracked
 * without any of that being written twice.
 */
@Stable
class PollResponsesFilterAssembler(
    cache: ICacheProvider,
    client: INostrClient,
) : ComposeSubscriptionManager<PollResponsesQueryState>() {
    val group = listOf(PollResponsesSubAssembler(cache, client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}

class PollResponsesSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<PollResponsesQueryState>,
) : PerUniqueIdEoseManager<PollResponsesQueryState, HexKey>(client, allKeys) {
    override fun updateFilter(
        key: PollResponsesQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        // The poll event carries the relays to ask; on a deep link it may still be in flight, in
        // which case there is nothing to ask yet and the filter re-assembles when it lands.
        val poll = cache.getNoteIfExists(key.pollId) ?: return null

        return filterPollResponses(poll, since)
    }

    override fun id(key: PollResponsesQueryState) = key.pollId
}
