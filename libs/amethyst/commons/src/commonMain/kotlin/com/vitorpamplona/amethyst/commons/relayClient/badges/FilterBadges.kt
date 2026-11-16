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
package com.vitorpamplona.amethyst.commons.relayClient.badges

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.scopedTo
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

private const val BADGE_FEED_LIMIT = 100

fun makeBadgesFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllFollowsTopNavPerRelayFilterSet -> filterBadgesByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterBadgesByAuthors(feedSettings, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterBadgesByMutedAuthors(feedSettings, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterBadgesGlobal(feedSettings, since, defaultSince)
        is RelayTopNavPerRelayFilterSet -> filterBadgesByRelay(feedSettings, since, defaultSince)
        else -> emptyList()
    }.scopedTo(feedSettings)

private fun filterBadgesByAuthorsOnRelay(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    since: Long? = null,
): List<RelayBasedFilter> {
    if (authors.isEmpty()) return emptyList()
    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ADD_ONS,
                    kinds = listOf(BadgeDefinitionEvent.KIND),
                    authors = authors.sorted(),
                    limit = BADGE_FEED_LIMIT,
                    since = since,
                ),
        ),
    )
}

private fun filterBadgesByFollows(
    followsSet: AllFollowsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (followsSet.set.isEmpty()) return emptyList()
    return followsSet.set.flatMap {
        val sinceValue = since?.get(it.key)?.time ?: defaultSince
        val authors = it.value.authors
        if (authors == null || authors.isEmpty()) {
            emptyList()
        } else {
            filterBadgesByAuthorsOnRelay(it.key, authors, sinceValue)
        }
    }
}

private fun filterBadgesByAuthors(
    authorSet: AuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()
    return authorSet.set.flatMap {
        filterBadgesByAuthorsOnRelay(it.key, it.value.authors, since?.get(it.key)?.time ?: defaultSince)
    }
}

private fun filterBadgesByMutedAuthors(
    authorSet: MutedAuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()
    return authorSet.set.flatMap {
        filterBadgesByAuthorsOnRelay(it.key, it.value.authors, since?.get(it.key)?.time ?: defaultSince)
    }
}

private fun filterBadgesGlobal(
    relays: GlobalTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (relays.set.isEmpty()) return emptyList()
    return relays.set.map {
        val sinceValue = since?.get(it.key)?.time ?: defaultSince ?: TimeUtils.oneMonthAgo()
        RelayBasedFilter(
            relay = it.key,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ADD_ONS,
                    kinds = listOf(BadgeDefinitionEvent.KIND),
                    limit = BADGE_FEED_LIMIT,
                    since = sinceValue,
                ),
        )
    }
}

/**
 * Loads the badge definitions of the one relay the user picked in the top bar.
 *
 * Same filter as [filterBadgesGlobal] sends to each of its relays, but with no recent-window
 * fallback on the starting point: a relay picked on its own may have nothing in Global's
 * recent window, so a quiet relay's feed would come up empty. The REQ's limit bounds it instead.
 */
private fun filterBadgesByRelay(
    relayFilter: RelayTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    val relayUrl = relayFilter.relayUrl
    val since = since?.get(relayUrl)?.time ?: defaultSince

    return listOf(
        RelayBasedFilter(
            relay = relayUrl,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.ADD_ONS,
                    kinds = listOf(BadgeDefinitionEvent.KIND),
                    limit = BADGE_FEED_LIMIT,
                    since = since,
                ),
        ),
    )
}
