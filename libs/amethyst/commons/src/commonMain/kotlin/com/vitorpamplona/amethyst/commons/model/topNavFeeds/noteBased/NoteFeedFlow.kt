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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased

import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.FeedDecryptionCaches
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedFlowsType
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.aroundMe.LocationTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform

class NoteFeedFlow(
    val metadataFlow: StateFlow<NoteState?>,
    val signer: NostrSigner,
    val outboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val caches: FeedDecryptionCaches,
) : IFeedFlowsType {
    fun processByOutbox(
        noteEvent: Event,
        outboxRelays: Set<NormalizedRelayUrl>,
    ): IFeedTopNavFilter =
        when (noteEvent) {
            is FollowSetEvent -> {
                if (noteEvent.dTag() == FollowSetEvent.BLOCK_LIST_D_TAG) {
                    MutedAuthorsByOutboxTopNavFilter(caches.peopleListCache.cachedUserIdSet(noteEvent), blockedRelays)
                } else {
                    AuthorsByOutboxTopNavFilter(caches.peopleListCache.cachedUserIdSet(noteEvent), blockedRelays)
                }
            }

            is MuteListEvent -> {
                MutedAuthorsByOutboxTopNavFilter(caches.muteListCache.cachedUserIdSet(noteEvent), blockedRelays)
            }

            is StarterPackEvent -> {
                AuthorsByOutboxTopNavFilter(noteEvent.followIdSet(), blockedRelays)
            }

            is DrssFeedListEvent -> {
                AuthorsByOutboxTopNavFilter(noteEvent.feedKeys().toSet(), blockedRelays)
            }

            is CommunityListEvent -> {
                AllCommunitiesTopNavFilter(caches.communityListCache.cachedCommunityIdSet(noteEvent), blockedRelays)
            }

            is InterestListEvent -> {
                HashtagTopNavFilter(caches.hashtagCache.cachedHashtags(noteEvent), outboxRelays)
            }

            is GeohashListEvent -> {
                LocationTopNavFilter(caches.geohashCache.cachedGeohashes(noteEvent), outboxRelays)
            }

            is CommunityDefinitionEvent -> {
                SingleCommunityTopNavFilter(
                    community = noteEvent.addressTag(),
                    authors = noteEvent.moderatorKeys().toSet().ifEmpty { null },
                    relays = noteEvent.relayUrls().toSet(),
                    blockedRelays = blockedRelays,
                )
            }

            else -> {
                AuthorsByOutboxTopNavFilter(emptySet(), blockedRelays)
            }
        }

    suspend fun FlowCollector<IFeedTopNavFilter>.processByOutbox(
        noteEvent: Event,
        outboxRelays: Set<NormalizedRelayUrl>,
    ) {
        when (noteEvent) {
            is FollowSetEvent -> {
                if (noteEvent.dTag() == FollowSetEvent.BLOCK_LIST_D_TAG) {
                    emit(MutedAuthorsByOutboxTopNavFilter(caches.peopleListCache.userIdSet(noteEvent), blockedRelays))
                } else {
                    emit(AuthorsByOutboxTopNavFilter(caches.peopleListCache.userIdSet(noteEvent), blockedRelays))
                }
            }

            is MuteListEvent -> {
                emit(MutedAuthorsByOutboxTopNavFilter(caches.muteListCache.mutedUserIdSet(noteEvent), blockedRelays))
            }

            is StarterPackEvent -> {
                emit(AuthorsByOutboxTopNavFilter(noteEvent.followIdSet(), blockedRelays))
            }

            is DrssFeedListEvent -> {
                emit(AuthorsByOutboxTopNavFilter(noteEvent.feedKeys().toSet(), blockedRelays))
            }

            is CommunityListEvent -> {
                emit(AllCommunitiesTopNavFilter(caches.communityListCache.communityIdSet(noteEvent), blockedRelays))
            }

            is InterestListEvent -> {
                emit(HashtagTopNavFilter(caches.hashtagCache.hashtags(noteEvent), outboxRelays))
            }

            is GeohashListEvent -> {
                emit(LocationTopNavFilter(caches.geohashCache.geohashes(noteEvent), outboxRelays))
            }

            is CommunityDefinitionEvent -> {
                emit(
                    SingleCommunityTopNavFilter(
                        community = noteEvent.addressTag(),
                        authors = noteEvent.moderatorKeys().toSet().ifEmpty { null },
                        relays = noteEvent.relayUrls().toSet(),
                        blockedRelays = blockedRelays,
                    ),
                )
            }

            else -> {
                emit(
                    AuthorsByOutboxTopNavFilter(emptySet(), blockedRelays),
                )
            }
        }
    }

    fun processByProxy(
        noteEvent: Event,
        proxyRelays: Set<NormalizedRelayUrl>,
    ): IFeedTopNavFilter =
        when (noteEvent) {
            is FollowSetEvent -> {
                if (noteEvent.dTag() == FollowSetEvent.BLOCK_LIST_D_TAG) {
                    MutedAuthorsByProxyTopNavFilter(caches.peopleListCache.cachedUserIdSet(noteEvent), proxyRelays)
                } else {
                    AuthorsByProxyTopNavFilter(caches.peopleListCache.cachedUserIdSet(noteEvent), proxyRelays)
                }
            }

            is MuteListEvent -> {
                MutedAuthorsByProxyTopNavFilter(caches.muteListCache.cachedUserIdSet(noteEvent), proxyRelays)
            }

            is StarterPackEvent -> {
                AuthorsByProxyTopNavFilter(noteEvent.followIdSet(), proxyRelays)
            }

            is DrssFeedListEvent -> {
                AuthorsByProxyTopNavFilter(noteEvent.feedKeys().toSet(), proxyRelays)
            }

            is CommunityListEvent -> {
                AllCommunitiesTopNavFilter(caches.communityListCache.cachedCommunityIdSet(noteEvent), blockedRelays)
            }

            is InterestListEvent -> {
                HashtagTopNavFilter(caches.hashtagCache.cachedHashtags(noteEvent), proxyRelays)
            }

            is GeohashListEvent -> {
                LocationTopNavFilter(caches.geohashCache.cachedGeohashes(noteEvent), proxyRelays)
            }

            is CommunityDefinitionEvent -> {
                SingleCommunityTopNavFilter(
                    community = noteEvent.addressTag(),
                    authors = noteEvent.moderatorKeys().toSet().ifEmpty { null },
                    relays = noteEvent.relayUrls().toSet(),
                    blockedRelays = blockedRelays,
                )
            }

            else -> {
                AuthorsByProxyTopNavFilter(emptySet(), proxyRelays)
            }
        }

    suspend fun FlowCollector<IFeedTopNavFilter>.processByProxy(
        noteEvent: Event,
        proxyRelays: Set<NormalizedRelayUrl>,
    ) {
        when (noteEvent) {
            is FollowSetEvent -> {
                if (noteEvent.dTag() == FollowSetEvent.BLOCK_LIST_D_TAG) {
                    emit(MutedAuthorsByProxyTopNavFilter(caches.peopleListCache.userIdSet(noteEvent), proxyRelays))
                } else {
                    emit(AuthorsByProxyTopNavFilter(caches.peopleListCache.userIdSet(noteEvent), proxyRelays))
                }
            }

            is MuteListEvent -> {
                emit(MutedAuthorsByProxyTopNavFilter(caches.muteListCache.mutedUserIdSet(noteEvent), proxyRelays))
            }

            is StarterPackEvent -> {
                emit(AuthorsByProxyTopNavFilter(noteEvent.followIdSet(), proxyRelays))
            }

            is DrssFeedListEvent -> {
                emit(AuthorsByProxyTopNavFilter(noteEvent.feedKeys().toSet(), proxyRelays))
            }

            is CommunityListEvent -> {
                emit(AllCommunitiesTopNavFilter(caches.communityListCache.communityIdSet(noteEvent), blockedRelays))
            }

            is InterestListEvent -> {
                emit(HashtagTopNavFilter(caches.hashtagCache.hashtags(noteEvent), proxyRelays))
            }

            is GeohashListEvent -> {
                emit(LocationTopNavFilter(caches.geohashCache.geohashes(noteEvent), proxyRelays))
            }

            is CommunityDefinitionEvent -> {
                emit(
                    SingleCommunityTopNavFilter(
                        community = noteEvent.addressTag(),
                        authors = noteEvent.moderatorKeys().toSet().ifEmpty { null },
                        relays = noteEvent.relayUrls().toSet(),
                        blockedRelays = blockedRelays,
                    ),
                )
            }

            else -> {
                emit(
                    AuthorsByProxyTopNavFilter(emptySet(), proxyRelays),
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun flow(): Flow<IFeedTopNavFilter> =
        combineTransform(metadataFlow, outboxRelays, proxyRelays) { noteState, outboxRelays, proxyRelays ->
            val noteEvent = noteState?.note?.event
            if (noteEvent == null) {
                emit(AuthorsByOutboxTopNavFilter(emptySet(), blockedRelays))
            } else {
                if (proxyRelays.isEmpty()) {
                    processByOutbox(noteEvent, outboxRelays)
                } else {
                    processByProxy(noteEvent, proxyRelays)
                }
            }
        }

    override fun startValue(): IFeedTopNavFilter {
        val noteEvent = metadataFlow.value?.note?.event
        return if (noteEvent == null) {
            AuthorsByOutboxTopNavFilter(emptySet(), blockedRelays)
        } else {
            if (proxyRelays.value.isEmpty()) {
                processByOutbox(noteEvent, outboxRelays.value)
            } else {
                processByProxy(noteEvent, proxyRelays.value)
            }
        }
    }

    override suspend fun startValue(collector: FlowCollector<IFeedTopNavFilter>) {
        val noteEvent = metadataFlow.value?.note?.event
        if (noteEvent == null) {
            collector.emit(AuthorsByOutboxTopNavFilter(emptySet(), blockedRelays))
        } else {
            if (proxyRelays.value.isEmpty()) {
                collector.processByOutbox(noteEvent, outboxRelays.value)
            } else {
                collector.processByProxy(noteEvent, proxyRelays.value)
            }
        }
    }
}
