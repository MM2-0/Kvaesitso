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
package com.vitorpamplona.amethyst.commons.relayClient.nests

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.scopedTo
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun makeNestsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    val rooms =
        when (feedSettings) {
            is AllCommunitiesTopNavPerRelayFilterSet -> filterNestsByAllCommunities(feedSettings, since, defaultSince)
            is AllFollowsTopNavPerRelayFilterSet -> filterNestsByFollows(feedSettings, since, defaultSince)
            is AuthorsTopNavPerRelayFilterSet -> filterNestsByAuthors(feedSettings, since, defaultSince)
            is GlobalTopNavPerRelayFilterSet -> filterNestsGlobal(feedSettings, since, defaultSince)
            is RelayTopNavPerRelayFilterSet -> filterNestsByRelay(feedSettings, since, defaultSince)
            is HashtagTopNavPerRelayFilterSet -> filterNestsByHashtag(feedSettings, since, defaultSince)
            is LocationTopNavPerRelayFilterSet -> filterNestsByGeohash(feedSettings, since, defaultSince)
            is MutedAuthorsTopNavPerRelayFilterSet -> filterNestsByAuthors(feedSettings, since, defaultSince)
            is SingleCommunityTopNavPerRelayFilterSet -> filterNestsByCommunity(feedSettings, since, defaultSince)
            else -> return emptyList()
        }.scopedTo(feedSettings)
    if (rooms.isEmpty()) return rooms

    // Add a single presence probe per relay we're already querying for
    // rooms — feeds NestsFeedFilter's freshness gate without forcing
    // the per-card subscription to color the live badge. Already
    // included by [filterNestsGlobal]; for other top-filters we splice
    // it in here so every selection benefits.
    if (feedSettings is GlobalTopNavPerRelayFilterSet) return rooms
    val presenceByRelay =
        rooms
            .map { it.relay }
            .toSet()
            .map { filterNestsPresence(it) }
    return rooms + presenceByRelay
}
