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
package com.vitorpamplona.amethyst.commons.followPacks.feed.datasource

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.AllUserFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.AllUserFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows.filterHomePostsByAuthors
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.utils.mapOfSet

class FollowPackFeedFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<FollowPackFeedQueryState>,
) : SingleSubEoseManager<FollowPackFeedQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<FollowPackFeedQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        val authorSets =
            keys.mapNotNull {
                val followPack = it.followPack.event as? StarterPackEvent ?: return@mapNotNull null
                if (it.account.proxyRelayList.flow.value
                        .isEmpty()
                ) {
                    AllUserFollowsByOutboxTopNavFilter(
                        authors = followPack.followIdSet(),
                        defaultRelays = it.account.defaultGlobalRelays.flow,
                        blockedRelays = it.account.blockedRelayList.flow,
                    ).startValue(it.account.cache)
                } else {
                    AllUserFollowsByProxyTopNavFilter(
                        authors = followPack.followIdSet(),
                        proxyRelays = it.account.proxyRelayList.flow.value,
                    ).startValue(it.account.cache)
                }
            }

        // One cursor per relay is shared by every follow pack opened on it: a relay whose set of
        // authors grows is asked for the newcomers' history from scratch.
        val relaySince =
            sinceDroppingGrownRelays(
                mapOfSet {
                    authorSets.forEach { authorSet ->
                        authorSet.set.forEach { (relay, filter) -> filter.authors.forEach { add(relay, it) } }
                    }
                },
                since,
            )

        return authorSets.flatMap { filterHomePostsByAuthors(it, relaySince, null, null) }
    }

    override fun distinct(key: FollowPackFeedQueryState) = key.followPack.idHex
}
