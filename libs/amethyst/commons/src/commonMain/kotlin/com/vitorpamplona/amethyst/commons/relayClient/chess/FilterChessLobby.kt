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
package com.vitorpamplona.amethyst.commons.relayClient.chess

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
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterProtocol
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The chess lobby's REQs for the list picked in its top bar.
 *
 * Jester puts every message of a game (the start and each move) in kind 30. From chosen authors
 * that is asked for whole, so their games come with their moves. Kind 30 is shared with other
 * protocols (NKBIP-03 citations), so from everyone (Global, one relay) only game starts are asked
 * for, by the start-position reference every open Jester challenge carries; the moves of the
 * games on screen come from [ChessLobbyGamesFilterAssembler]. Jester events carry no hashtags,
 * geohashes or community tags, so those selections load nothing.
 */
fun makeChessFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllFollowsTopNavPerRelayFilterSet -> filterChessByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterChessByAuthors(feedSettings.set.mapValues { it.value.authors }, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterChessByAuthors(feedSettings.set.mapValues { it.value.authors }, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterChessGlobal(feedSettings.set.keys, since, defaultSince ?: TimeUtils.oneWeekAgo())
        // A relay picked on its own may be quiet, so no recent-window floor: the limit bounds it.
        is RelayTopNavPerRelayFilterSet -> filterChessGlobal(setOf(feedSettings.relayUrl), since, defaultSince)
        else -> emptyList()
    }.scopedTo(feedSettings)

private fun chessFilter(
    relay: NormalizedRelayUrl,
    since: Long?,
    authors: List<HexKey>? = null,
) = RelayBasedFilter(
    relay = relay,
    filter =
        ExplainedFilter(
            purpose = SubPurpose.GAMES,
            authors = authors,
            kinds = listOf(JesterEvent.KIND),
            tags = if (authors == null) mapOf("e" to listOf(JesterProtocol.START_POSITION_HASH)) else null,
            limit = 500,
            since = since,
        ),
)

private fun filterChessGlobal(
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> = relays.map { chessFilter(it, since?.get(it)?.time ?: defaultSince) }

private fun filterChessByAuthors(
    authorsPerRelay: Map<NormalizedRelayUrl, Set<HexKey>>,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> =
    authorsPerRelay.mapNotNull { (relay, authors) ->
        if (authors.isEmpty()) null else chessFilter(relay, since?.get(relay)?.time ?: defaultSince, authors.sorted())
    }

private fun filterChessByFollows(
    followsSet: AllFollowsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long?,
): List<RelayBasedFilter> =
    followsSet.set.mapNotNull { (relay, filter) ->
        val authors = filter.authors
        if (authors.isNullOrEmpty()) null else chessFilter(relay, since?.get(relay)?.time ?: defaultSince, authors.sorted())
    }
