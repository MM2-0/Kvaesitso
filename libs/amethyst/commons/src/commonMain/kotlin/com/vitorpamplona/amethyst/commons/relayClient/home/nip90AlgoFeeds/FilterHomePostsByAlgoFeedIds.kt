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
package com.vitorpamplona.amethyst.commons.relayClient.home.nip90AlgoFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent

/**
 * Builds relay REQ filters for a favorite-DVM home feed.
 *
 * Two distinct subscription kinds with two distinct relay sets:
 *
 * - **Content fetch** — for each of the user's outbox/proxy relays, request the
 *   note IDs and addressable references the DVM curated. Notes typically live on
 *   the user's normal relays, so this is where we fetch them.
 *
 * - **Response listen** — for each relay the DVM advertised (where it received
 *   the kind-5300 request and will publish its 6300/7000 reply), subscribe to
 *   future kind 6300 / 7000 events tagged with the request id. The DVM almost
 *   never publishes responses on the user's outbox, so listening anywhere else
 *   would silently miss them.
 */
fun filterHomePostsByAlgoFeedIds(
    set: FavoriteAlgoFeedTopNavPerRelayFilterSet,
    @Suppress("UNUSED_PARAMETER") since: SincePerRelayMap?,
    @Suppress("UNUSED_PARAMETER") defaultSince: Long?,
): List<RelayBasedFilter> {
    val out = mutableListOf<RelayBasedFilter>()

    set.contentFetches.forEach { (relay, filter) ->
        out += contentFetchFilters(relay, filter)
    }

    if (set.requestIds.isNotEmpty()) {
        val requestIds = set.requestIds.toList()
        set.listenRelays.forEach { relay ->
            out += responseListenFilter(relay, requestIds)
        }
    }

    return out
}

private fun contentFetchFilters(
    relay: NormalizedRelayUrl,
    filter: FavoriteAlgoFeedTopNavPerRelayFilter,
): List<RelayBasedFilter> {
    val out = mutableListOf<RelayBasedFilter>()

    if (filter.ids.isNotEmpty()) {
        out +=
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.HOME_FEED,
                        ids = filter.ids.toList(),
                        limit = filter.ids.size,
                    ),
            )
    }

    if (filter.addresses.isNotEmpty()) {
        out +=
            RelayBasedFilter(
                relay = relay,
                filter =
                    ExplainedFilter(
                        purpose = SubPurpose.HOME_FEED,
                        tags = mapOf("a" to filter.addresses.toList()),
                        limit = filter.addresses.size,
                    ),
            )
    }

    return out
}

private fun responseListenFilter(
    relay: NormalizedRelayUrl,
    requestIds: List<HexKey>,
) = RelayBasedFilter(
    relay = relay,
    filter =
        ExplainedFilter(
            purpose = SubPurpose.HOME_FEED,
            kinds =
                listOf(
                    DvmContentDiscoveryResponseEvent.KIND,
                    DvmStatusEvent.KIND,
                ),
            tags = mapOf("e" to requestIds),
            limit = 10 * requestIds.size,
        ),
)
