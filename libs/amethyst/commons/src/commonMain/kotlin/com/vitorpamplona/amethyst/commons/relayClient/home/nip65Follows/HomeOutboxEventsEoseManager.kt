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
package com.vitorpamplona.amethyst.commons.relayClient.home.nip65Follows

import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom.CustomFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.home.HomeQueryState
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByCustomFeed
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByGeohashes
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByGlobal
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByHashtags
import com.vitorpamplona.amethyst.commons.relayClient.home.nip01Core.filterHomePostsByRelay
import com.vitorpamplona.amethyst.commons.relayClient.home.nip72Communities.filterHomePostsByAllCommunities
import com.vitorpamplona.amethyst.commons.relayClient.home.nip72Communities.filterHomePostsByCommunity
import com.vitorpamplona.amethyst.commons.relayClient.home.nip90AlgoFeeds.filterHomePostsByAlgoFeedIds
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.scopedTo
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedSubAssembler
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop

/**
 * The home feed's REQ builder: dispatches the top-nav selection to the home filters, bounded by the
 * new-threads and replies floors, and drops the kinds the user turned off in Settings › Home.
 */
class HomeOutboxEventsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<HomeQueryState>,
) : TopNavFeedSubAssembler<HomeQueryState>(client, allKeys) {
    override fun updateFilter(
        key: HomeQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val feedSettings = key.followsPerRelay.value
        val newThreadSince = key.newThreads.lastNoteCreatedAtIfFilled()
        val repliesSince = key.replies.lastNoteCreatedAtIfFilled()

        val base =
            when (feedSettings) {
                is AllCommunitiesTopNavPerRelayFilterSet -> filterHomePostsByAllCommunities(feedSettings, since, newThreadSince)
                is AllFollowsTopNavPerRelayFilterSet -> filterHomePostsByAllFollows(feedSettings, since, newThreadSince, repliesSince)
                is AuthorsTopNavPerRelayFilterSet -> filterHomePostsByAuthors(feedSettings, since, newThreadSince, repliesSince)
                is GlobalTopNavPerRelayFilterSet -> filterHomePostsByGlobal(feedSettings, since, newThreadSince, repliesSince)
                is HashtagTopNavPerRelayFilterSet -> filterHomePostsByHashtags(feedSettings, since, newThreadSince)
                is LocationTopNavPerRelayFilterSet -> filterHomePostsByGeohashes(feedSettings, since, newThreadSince)
                is MutedAuthorsTopNavPerRelayFilterSet -> filterHomePostsByAuthors(feedSettings, since, newThreadSince, repliesSince)
                is RelayTopNavPerRelayFilterSet -> filterHomePostsByRelay(feedSettings, since, newThreadSince, repliesSince)
                is SingleCommunityTopNavPerRelayFilterSet -> filterHomePostsByCommunity(feedSettings, since, newThreadSince)
                is FavoriteAlgoFeedTopNavPerRelayFilterSet -> filterHomePostsByAlgoFeedIds(feedSettings, since, newThreadSince)
                is CustomFeedTopNavPerRelayFilterSet -> filterHomePostsByCustomFeed(feedSettings, since, newThreadSince)
                else -> emptyList()
            }.scopedTo(feedSettings)

        // Drop the kinds the user turned off in Settings › Home from every home relay filter, so a
        // disabled group is never downloaded regardless of which top-nav strategy built the filters.
        return base.removeDisabledHomeKinds(HomeFeedType.disabledKinds(key.enabledHomeFeedTypes.value))
    }

    // Re-arm the home subscriptions when a content-type toggle flips, so a disabled group leaves the
    // live REQ and a re-enabled one comes back without a restart.
    override fun extraInvalidators(key: HomeQueryState): List<Flow<*>> = listOf(key.enabledHomeFeedTypes.drop(1))
}

private fun List<RelayBasedFilter>.removeDisabledHomeKinds(disabled: Set<Int>): List<RelayBasedFilter> {
    if (disabled.isEmpty()) return this
    return mapNotNull { relayFilter ->
        val kinds = relayFilter.filter.kinds ?: return@mapNotNull relayFilter
        val kept = kinds.filterNot { it in disabled }
        when {
            kept.size == kinds.size -> relayFilter
            kept.isEmpty() -> null
            else -> RelayBasedFilter(relayFilter.relay, relayFilter.filter.copy(kinds = kept))
        }
    }
}
