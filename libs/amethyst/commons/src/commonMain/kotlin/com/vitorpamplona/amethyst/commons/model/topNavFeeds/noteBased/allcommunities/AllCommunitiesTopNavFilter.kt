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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.CommunityRelayLoader
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip72ModCommunities.isForAnyCommunity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

@Immutable
class AllCommunitiesTopNavFilter(
    val communities: Set<String>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey): Boolean = true

    override fun match(noteEvent: Event): Boolean = noteEvent.isForAnyCommunity(communities)

    fun convert(map: Map<NormalizedRelayUrl, Set<HexKey>>) =
        AllCommunitiesTopNavPerRelayFilterSet(
            map.mapValues { AllCommunitiesTopNavPerRelayFilter(it.value) },
        )

    override fun toPerRelayFlow(cache: ICacheProvider): Flow<AllCommunitiesTopNavPerRelayFilterSet> {
        val communitiesPerRelay = CommunityRelayLoader.toCommunitiesPerRelayFlow(communities, cache) { it }

        return combine(communitiesPerRelay, blockedRelays) { communitiesPerRelay, blockedRelays ->
            convert(communitiesPerRelay.minus(blockedRelays))
        }
    }

    override fun startValue(cache: ICacheProvider): AllCommunitiesTopNavPerRelayFilterSet {
        val communitiesPerRelay = CommunityRelayLoader.communitiesPerRelaySnapshot(communities, cache) { it }

        return convert(communitiesPerRelay.minus(blockedRelays.value))
    }
}
