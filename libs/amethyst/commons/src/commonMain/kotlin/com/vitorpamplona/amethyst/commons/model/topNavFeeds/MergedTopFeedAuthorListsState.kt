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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class MergedTopFeedAuthorListsState(
    val homeNavFilter: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val videoNavFilter: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val discoveryNavFilter: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val notificationNavFilter: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val scope: CoroutineScope,
) {
    fun authorList(navFilter: IFeedTopNavPerRelayFilterSet): Map<NormalizedRelayUrl, Set<HexKey>?> =
        when (navFilter) {
            is AllCommunitiesTopNavPerRelayFilterSet -> emptyMap()
            is AllFollowsTopNavPerRelayFilterSet -> navFilter.set.mapValues { it.value.authors }
            is AuthorsTopNavPerRelayFilterSet -> navFilter.set.mapValues { it.value.authors }
            is GlobalTopNavPerRelayFilterSet -> emptyMap()
            is HashtagTopNavPerRelayFilterSet -> emptyMap()
            is LocationTopNavPerRelayFilterSet -> emptyMap()
            is MutedAuthorsTopNavPerRelayFilterSet -> navFilter.set.mapValues { it.value.authors }
            is SingleCommunityTopNavPerRelayFilterSet -> navFilter.set.mapValues { it.value.authors }
            else -> emptyMap()
        }

    fun mergeLists(
        homeNavFilter: IFeedTopNavPerRelayFilterSet,
        videoNavFilter: IFeedTopNavPerRelayFilterSet,
        discoveryNavFilter: IFeedTopNavPerRelayFilterSet,
        notificationNavFilter: IFeedTopNavPerRelayFilterSet,
    ): Map<NormalizedRelayUrl, Set<HexKey>> =
        mapOfSet {
            authorList(homeNavFilter).forEach { (relay, authors) ->
                authors?.let { add(relay, authors) }
            }

            authorList(videoNavFilter).forEach { (relay, authors) ->
                authors?.let { add(relay, authors) }
            }

            authorList(discoveryNavFilter).forEach { (relay, authors) ->
                authors?.let { add(relay, authors) }
            }

            authorList(notificationNavFilter).forEach { (relay, authors) ->
                authors?.let { add(relay, authors) }
            }
        }

    val flow: StateFlow<Map<NormalizedRelayUrl, Set<HexKey>>> =
        combine(
            homeNavFilter,
            videoNavFilter,
            discoveryNavFilter,
            notificationNavFilter,
            ::mergeLists,
        ).onStart {
            emit(
                mergeLists(
                    homeNavFilter.value,
                    videoNavFilter.value,
                    discoveryNavFilter.value,
                    notificationNavFilter.value,
                ),
            )
            // `kotlinx.coroutines.IO` must stay imported: `Dispatchers.IO` is an internal member in
            // common, and the public form on Kotlin/Native is that extension. Drop the import and this
            // still builds on JVM/Android, failing only the iOS targets.
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyMap(),
            )
}
