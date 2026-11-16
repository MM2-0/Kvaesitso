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
package com.vitorpamplona.amethyst.commons.relayClient.podcasts

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

fun makePodcastEpisodesFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> = makePodcastFilter(feedSettings, PODCAST_EPISODE_KINDS, since, defaultSince)

fun makePodcastsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> =
    // Two REQs: NIP-F4 shows (kind:10154, no tag constraint) plus Podcasting-2.0 shows
    // (kind:30078, constrained to `#d=["podcast-metadata"]` so the overloaded app-data kind
    // doesn't flood the feed). Both land in the merged PodcastsFeedFilter.
    makePodcastFilter(feedSettings, PODCAST_KINDS, since, defaultSince) +
        makePodcastFilter(feedSettings, PODCASTING20_METADATA_KINDS, since, defaultSince, PODCAST_METADATA_D_FILTER)

private fun makePodcastFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    kinds: List<Int>,
    since: SincePerRelayMap?,
    defaultSince: Long?,
    additionalTags: Map<String, List<String>>? = null,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterPodcastEventsByAllCommunities(feedSettings, kinds, since, defaultSince, additionalTags)
        is AllFollowsTopNavPerRelayFilterSet -> filterPodcastEventsByFollows(feedSettings, kinds, since, defaultSince, additionalTags)
        is AuthorsTopNavPerRelayFilterSet -> filterPodcastEventsByAuthors(feedSettings, kinds, since, defaultSince, additionalTags)
        is GlobalTopNavPerRelayFilterSet -> filterPodcastEventsGlobal(feedSettings, kinds, since, defaultSince, additionalTags)
        is RelayTopNavPerRelayFilterSet -> filterPodcastEventsByRelay(feedSettings, kinds, since, defaultSince, additionalTags)
        is HashtagTopNavPerRelayFilterSet -> filterPodcastEventsByHashtag(feedSettings, kinds, since, defaultSince, additionalTags)
        is LocationTopNavPerRelayFilterSet -> filterPodcastEventsByGeohashes(feedSettings, kinds, since, defaultSince, additionalTags)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterPodcastEventsByMutedAuthors(feedSettings, kinds, since, defaultSince, additionalTags)
        is SingleCommunityTopNavPerRelayFilterSet -> filterPodcastEventsByCommunity(feedSettings, kinds, since, defaultSince, additionalTags)
        else -> emptyList()
    }.scopedTo(feedSettings)
