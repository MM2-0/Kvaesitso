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
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class FavoriteAlgoFeedFlow(
    val feedAddress: Address,
    val orchestrator: FavoriteAlgoFeedsOrchestrator,
    val outboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedFlowsType {
    private fun resolveRelays(
        outbox: Set<NormalizedRelayUrl>,
        proxy: Set<NormalizedRelayUrl>,
    ): Set<NormalizedRelayUrl> = if (proxy.isNotEmpty()) proxy else outbox

    private fun buildFilter(
        snapshot: com.vitorpamplona.amethyst.commons.model.algoFeeds.FavoriteAlgoFeedsSnapshot,
        contentRelays: Set<NormalizedRelayUrl>,
    ) = FavoriteAlgoFeedTopNavFilter(
        feedAddress = feedAddress,
        acceptedIds = snapshot.ids,
        acceptedAddresses = snapshot.addresses,
        contentRelays = contentRelays,
        listenRelays = snapshot.responseRelays,
        requestId = snapshot.requestId,
    )

    override fun flow(): Flow<IFeedTopNavFilter> =
        combine(orchestrator.observe(feedAddress), outboxRelays, proxyRelays) { snap, outbox, proxy ->
            buildFilter(snap, resolveRelays(outbox, proxy))
        }

    override fun startValue(): FavoriteAlgoFeedTopNavFilter =
        buildFilter(
            snapshot = orchestrator.observe(feedAddress).value,
            contentRelays = resolveRelays(outboxRelays.value, proxyRelays.value),
        )

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) {
        collector.emit(startValue())
    }
}
