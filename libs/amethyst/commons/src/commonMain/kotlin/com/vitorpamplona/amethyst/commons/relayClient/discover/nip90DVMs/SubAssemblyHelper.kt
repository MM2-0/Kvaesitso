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
package com.vitorpamplona.amethyst.commons.relayClient.discover.nip90DVMs

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
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.scopedTo
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

fun makeContentDVMsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllCommunitiesTopNavPerRelayFilterSet -> filterContentDVMsByAllCommunities(feedSettings, since, defaultSince)
        is AllFollowsTopNavPerRelayFilterSet -> filterContentDVMsByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterContentDVMsByAuthors(feedSettings, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterContentDVMsGlobal(feedSettings, since, defaultSince)
        is RelayTopNavPerRelayFilterSet -> filterContentDVMsByRelay(feedSettings, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterContentDVMsByHashtag(feedSettings, since, defaultSince)
        is LocationTopNavPerRelayFilterSet -> filterContentDVMsByGeohash(feedSettings, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterContentDVMsByAuthors(feedSettings, since, defaultSince)
        is SingleCommunityTopNavPerRelayFilterSet -> filterContentDVMsByCommunity(feedSettings, since, defaultSince)
        else -> emptyList()
    }.let { contentDvmFilters ->
        plusHeartbeatFilter(feedSettings, contentDvmFilters)
    }.scopedTo(feedSettings)

/**
 * The 31990 announcements say what a DVM advertises; kind-11998 heartbeats say whether it is
 * still alive (amethyst/plans/2026-09-10-dvm-heartbeat-liveness.md). Ask on the selection's own
 * relays — NOT the 31990 filters' relays, which skip relays whose per-relay slice is empty and
 * whole selections (the Relay variant) that dispatch no 31990 filters at all; there the cached
 * DVM list would lose its beat stream and the staleness timer would drop every DVM within ~7
 * minutes. Rolling window instead of the announcement cursor: beats expire (NIP-40) every 5
 * minutes, so a stored `since` would miss beats on re-opened tabs.
 */
private fun plusHeartbeatFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    contentDvmFilters: List<RelayBasedFilter>,
): List<RelayBasedFilter> {
    val relays = feedSettings.relays()
    if (relays.isEmpty()) return contentDvmFilters
    val heartbeatFilter =
        ExplainedFilter(
            purpose = SubPurpose.DISCOVER_FEED,
            kinds = listOf(DvmHeartbeatEvent.KIND),
            limit = 100,
            since = TimeUtils.now() - DvmHeartbeatEvent.MAX_AGE_SECONDS,
        )
    return contentDvmFilters +
        relays.map { relay ->
            RelayBasedFilter(relay = relay, filter = heartbeatFilter)
        }
}
