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

import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.model.algoFeeds.FavoriteAlgoFeedsOrchestrator
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.commons.model.serverList.MergedFollowListsState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.FeedDecryptionCaches
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.AllUserFollowsFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allUserFollows.Kind3UserFollowsFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.AroundMeFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.GeohashFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom.CustomFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.AllFavoriteAlgoFeedsFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds.FavoriteAlgoFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.MultiHashtagFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.mine.MineFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.DrssFolderFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.NoteFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayFeedFlow
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class FeedTopNavFilterState(
    val feedFilterListName: MutableStateFlow<TopFilter>,
    val kind3Follows: StateFlow<Kind3FollowListState.Kind3Follows>,
    val allFollows: StateFlow<MergedFollowListsState.AllFollows>,
    val locationFlow: () -> StateFlow<LocationResult>,
    val followsRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val mineRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val relayFeeds: StateFlow<Set<NormalizedRelayUrl>>,
    val caches: FeedDecryptionCaches,
    val signer: NostrSigner,
    val scope: CoroutineScope,
    val favoriteAlgoFeedsOrchestrator: FavoriteAlgoFeedsOrchestrator,
    val favoriteAlgoFeedAddresses: StateFlow<Set<Address>>,
    val interestSetHashtags: StateFlow<Map<String, Set<String>>> = MutableStateFlow(emptyMap()),
    val customFeeds: StateFlow<List<FeedDefinition>> = MutableStateFlow(emptyList()),
) {
    fun loadFlowsFor(listName: TopFilter): IFeedFlowsType =
        when (listName) {
            // TeleportPicker is a UI-only sentinel (intercepted by the spinner to open the
            // map picker); it never reaches here, but fall back to Global for exhaustiveness.
            TopFilter.Global, TopFilter.Selected, TopFilter.TeleportPicker -> {
                GlobalFeedFlow(followsRelays, proxyRelays, relayFeeds)
            }

            TopFilter.AllFollows -> {
                AllFollowsFeedFlow(allFollows, followsRelays, blockedRelays, proxyRelays)
            }

            TopFilter.AllUserFollows -> {
                AllUserFollowsFeedFlow(allFollows, followsRelays, blockedRelays, proxyRelays)
            }

            TopFilter.DefaultFollows -> {
                Kind3UserFollowsFeedFlow(kind3Follows, followsRelays, blockedRelays, proxyRelays)
            }

            TopFilter.AroundMe -> {
                AroundMeFeedFlow(locationFlow(), followsRelays, proxyRelays)
            }

            TopFilter.Mine -> {
                MineFeedFlow(signer.pubKey, mineRelays)
            }

            // A drss.io folder is a tree of lists: its river follows everyone in them.
            is TopFilter.PeopleList if listName.address.kind == DrssListFolderEvent.KIND -> {
                DrssFolderFeedFlow(listName.address, followsRelays, blockedRelays, proxyRelays)
            }

            is TopFilter.Community, is TopFilter.PeopleList, is TopFilter.MuteList -> {
                val addressFilter = listName as TopFilter.AddressableTopFilter
                NoteFeedFlow(
                    LocalCache
                        .getOrCreateAddressableNote(addressFilter.address)
                        .flow()
                        .metadata.stateFlow,
                    signer,
                    followsRelays,
                    blockedRelays,
                    proxyRelays,
                    caches,
                )
            }

            is TopFilter.Geohash -> {
                GeohashFeedFlow(listName.tag, followsRelays, proxyRelays)
            }

            is TopFilter.Hashtag -> {
                HashtagFeedFlow(listName.tag, followsRelays, proxyRelays)
            }

            is TopFilter.InterestSet -> {
                val hashtags = interestSetHashtags.value[listName.address.dTag].orEmpty()
                MultiHashtagFeedFlow(hashtags, followsRelays, proxyRelays)
            }

            is TopFilter.CustomFeed -> {
                CustomFeedFlow(listName.id, customFeeds, followsRelays, blockedRelays, proxyRelays)
            }

            is TopFilter.Relay -> {
                RelayFeedFlow(listName.url.normalizeRelayUrl())
            }

            is TopFilter.FavoriteAlgoFeed -> {
                FavoriteAlgoFeedFlow(
                    feedAddress = listName.address,
                    orchestrator = favoriteAlgoFeedsOrchestrator,
                    outboxRelays = followsRelays,
                    proxyRelays = proxyRelays,
                )
            }

            TopFilter.AllFavoriteAlgoFeeds -> {
                AllFavoriteAlgoFeedsFlow(
                    favoriteAlgoFeedAddresses = favoriteAlgoFeedAddresses,
                    orchestrator = favoriteAlgoFeedsOrchestrator,
                    outboxRelays = followsRelays,
                    proxyRelays = proxyRelays,
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<IFeedTopNavFilter> =
        feedFilterListName
            .transformLatest { listName ->
                emitAll(loadFlowsFor(listName).flow())
            }.onStart {
                loadFlowsFor(feedFilterListName.value).startValue(this)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                loadFlowsFor(feedFilterListName.value).startValue(),
            )
}
