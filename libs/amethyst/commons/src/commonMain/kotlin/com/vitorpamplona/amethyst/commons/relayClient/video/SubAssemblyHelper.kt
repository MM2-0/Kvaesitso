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
package com.vitorpamplona.amethyst.commons.relayClient.video

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
import com.vitorpamplona.amethyst.commons.relayClient.video.nip01Core.filterPictureAndVideoByGeohash
import com.vitorpamplona.amethyst.commons.relayClient.video.nip01Core.filterPictureAndVideoByHashtag
import com.vitorpamplona.amethyst.commons.relayClient.video.nip01Core.filterPictureAndVideoByRelay
import com.vitorpamplona.amethyst.commons.relayClient.video.nip01Core.filterPictureAndVideoGlobal
import com.vitorpamplona.amethyst.commons.relayClient.video.nip65Follows.filterPictureAndVideoByAuthors
import com.vitorpamplona.amethyst.commons.relayClient.video.nip65Follows.filterPictureAndVideoByFollows
import com.vitorpamplona.amethyst.commons.relayClient.video.nip72Communities.filterPictureAndVideoByAllCommunities
import com.vitorpamplona.amethyst.commons.relayClient.video.nip72Communities.filterPictureAndVideoByCommunity
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Maps the top-nav selection to the picture/video feed's REQs. Global and hashtag selections fall
 * back to a week / a month of history when the feed has no floor yet, so a cold start does not
 * pull every relay's whole media history.
 */
fun makePictureAndVideoFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterPictureAndVideoByAllCommunities(feedSettings, since, defaultSince)
        is AllFollowsTopNavPerRelayFilterSet -> filterPictureAndVideoByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterPictureAndVideoByAuthors(feedSettings, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterPictureAndVideoGlobal(feedSettings, since, defaultSince ?: TimeUtils.oneWeekAgo())
        is RelayTopNavPerRelayFilterSet -> filterPictureAndVideoByRelay(feedSettings, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterPictureAndVideoByHashtag(feedSettings, since, defaultSince ?: TimeUtils.oneMonthAgo())
        is LocationTopNavPerRelayFilterSet -> filterPictureAndVideoByGeohash(feedSettings, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterPictureAndVideoByAuthors(feedSettings, since, defaultSince)
        is SingleCommunityTopNavPerRelayFilterSet -> filterPictureAndVideoByCommunity(feedSettings, since, defaultSince)
        else -> emptyList()
    }
