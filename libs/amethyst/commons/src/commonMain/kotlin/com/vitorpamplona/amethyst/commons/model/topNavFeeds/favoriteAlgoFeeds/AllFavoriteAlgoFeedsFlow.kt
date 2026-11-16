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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds

import com.vitorpamplona.amethyst.commons.model.algoFeeds.FavoriteAlgoFeedsOrchestrator
import com.vitorpamplona.amethyst.commons.model.algoFeeds.FavoriteAlgoFeedsSnapshot
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.AllFavoriteAlgoFeedsTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Feed flow that merges snapshots from every currently-favorited DVM into a
 * single [AllFavoriteAlgoFeedsTopNavFilter]. Re-wires subscriptions whenever the
 * favorite set changes.
 */
class AllFavoriteAlgoFeedsFlow(
    val favoriteAlgoFeedAddresses: StateFlow<Set<Address>>,
    val orchestrator: FavoriteAlgoFeedsOrchestrator,
    val outboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedFlowsType {
    private fun resolveContentRelays(
        outbox: Set<NormalizedRelayUrl>,
        proxy: Set<NormalizedRelayUrl>,
    ): Set<NormalizedRelayUrl> = if (proxy.isNotEmpty()) proxy else outbox

    private fun merge(
        snapshots: List<FavoriteAlgoFeedsSnapshot>,
        contentRelays: Set<NormalizedRelayUrl>,
    ): AllFavoriteAlgoFeedsTopNavFilter {
        val ids = mutableSetOf<String>()
        val addresses = mutableSetOf<String>()
        val listen = mutableSetOf<NormalizedRelayUrl>()
        val requestIds = mutableSetOf<String>()
        snapshots.forEach { snap ->
            ids += snap.ids
            addresses += snap.addresses
            listen += snap.responseRelays
            snap.requestId?.let { requestIds += it }
        }
        return AllFavoriteAlgoFeedsTopNavFilter(
            acceptedIds = ids,
            acceptedAddresses = addresses,
            contentRelays = contentRelays,
            listenRelays = listen,
            requestIds = requestIds,
        )
    }

    private fun emptyFilter(contentRelays: Set<NormalizedRelayUrl>): AllFavoriteAlgoFeedsTopNavFilter =
        AllFavoriteAlgoFeedsTopNavFilter(
            acceptedIds = emptySet(),
            acceptedAddresses = emptySet(),
            contentRelays = contentRelays,
            listenRelays = emptySet(),
            requestIds = emptySet(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun flow(): Flow<IFeedTopNavFilter> =
        favoriteAlgoFeedAddresses.flatMapLatest { addresses ->
            if (addresses.isEmpty()) {
                combine(outboxRelays, proxyRelays) { outbox, proxy ->
                    emptyFilter(resolveContentRelays(outbox, proxy))
                }
            } else {
                val snapshotFlows: List<Flow<FavoriteAlgoFeedsSnapshot>> = addresses.map { orchestrator.observe(it) }
                combine(snapshotFlows) { it.toList() }
                    .let { merged ->
                        combine(merged, outboxRelays, proxyRelays) { snaps, outbox, proxy ->
                            merge(snaps, resolveContentRelays(outbox, proxy))
                        }
                    }
            }
        }

    override fun startValue(): AllFavoriteAlgoFeedsTopNavFilter {
        val contentRelays = resolveContentRelays(outboxRelays.value, proxyRelays.value)
        val addresses = favoriteAlgoFeedAddresses.value
        return if (addresses.isEmpty()) {
            emptyFilter(contentRelays)
        } else {
            merge(addresses.map { orchestrator.observe(it).value }, contentRelays)
        }
    }

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) {
        collector.emit(startValue())
    }
}
