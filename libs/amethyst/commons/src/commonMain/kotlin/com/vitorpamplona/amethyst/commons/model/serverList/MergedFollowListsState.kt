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
package com.vitorpamplona.amethyst.commons.model.serverList

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.FollowSetsState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.StarterPacksState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.geohashLists.GeohashListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestLists.InterestListState
import com.vitorpamplona.amethyst.commons.model.nip72Communities.CommunityListState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip72ModCommunities.follow.tags.CommunityTag
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn

@Suppress("UNCHECKED_CAST")
class MergedFollowListsState(
    val kind3List: Kind3FollowListState,
    val followSets: FollowSetsState,
    val starterPacks: StarterPacksState,
    val interestList: InterestListState,
    val geohashList: GeohashListState,
    val communityList: CommunityListState,
    val scope: CoroutineScope,
) {
    /**
     This contains a big OR of everything the user wants to see in the a single feed.
     */
    @Immutable
    class AllFollows(
        val authors: Set<String> = emptySet(),
        val hashtags: Set<String> = emptySet(),
        val geotags: Set<String> = emptySet(),
        val communities: Set<String> = emptySet(),
    ) {
        val geotagScopes: Set<String> = geotags.mapTo(mutableSetOf()) { GeohashId.toScope(it) }
        val hashtagScopes: Set<String> = hashtags.mapTo(mutableSetOf()) { HashtagId.toScope(it) }
    }

    fun mergeLists(
        kind3: Kind3FollowListState.Kind3Follows,
        peopleListProfiles: Set<String>,
        followListProfiles: Set<String>,
        hashtags: Set<String>,
        geohashes: Set<String>,
        community: Set<CommunityTag>,
    ): AllFollows =
        AllFollows(
            authors = kind3.authors + peopleListProfiles + followListProfiles,
            hashtags = hashtags,
            geotags = geohashes,
            communities = community.mapTo(mutableSetOf()) { it.address.toValue() },
        )

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val flow: StateFlow<AllFollows> =
        combine(
            listOf(
                kind3List.flow,
                followSets.allGoodPeopleListProfiles,
                starterPacks.allPeopleListProfiles,
                interestList.flow,
                geohashList.flow,
                communityList.flow,
            ),
        ) { args ->
            mergeLists(
                args[0] as Kind3FollowListState.Kind3Follows,
                args[1] as Set<HexKey>,
                args[2] as Set<HexKey>,
                args[3] as Set<String>,
                args[4] as Set<String>,
                args[5] as Set<CommunityTag>,
            )
        }.onStart {
            emit(
                mergeLists(
                    kind3List.flow.value,
                    followSets.allGoodPeopleListProfiles.value,
                    starterPacks.allPeopleListProfiles.value,
                    interestList.flow.value,
                    geohashList.flow.value,
                    communityList.flow.value,
                ),
            )
        }.sample(200)
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                mergeLists(
                    kind3List.flow.value,
                    followSets.allGoodPeopleListProfiles.value,
                    starterPacks.allPeopleListProfiles.value,
                    interestList.flow.value,
                    geohashList.flow.value,
                    communityList.flow.value,
                ),
            )
}
