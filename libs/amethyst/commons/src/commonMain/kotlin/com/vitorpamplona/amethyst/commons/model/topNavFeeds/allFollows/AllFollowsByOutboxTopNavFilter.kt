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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.CommunityRelayLoader
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.OutboxRelayLoader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.geohash.isTaggedGeoHashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHashes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForAnyCommunity
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * This is a big OR filter on all fields.
 */
@Immutable
class AllFollowsByOutboxTopNavFilter(
    val authors: Set<String>? = null,
    val hashtags: Set<String>? = null,
    val geotags: Set<String>? = null,
    val communities: Set<String>? = null,
    val defaultRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedTopNavFilter {
    val geotagScopes: Set<String>? = geotags?.mapTo(mutableSetOf()) { GeohashId.toScope(it) }
    val hashtagScopes: Set<String>? = hashtags?.mapTo(mutableSetOf()) { HashtagId.toScope(it) }

    override fun matchAuthor(pubkey: HexKey): Boolean = authors == null || pubkey in authors

    override fun match(noteEvent: Event): Boolean =
        if (noteEvent is LiveActivitiesEvent) {
            (authors != null && noteEvent.participantsIntersect(authors)) ||
                (hashtags != null && noteEvent.isTaggedHashes(hashtags)) ||
                (geotags != null && noteEvent.isTaggedGeoHashes(geotags)) ||
                (communities != null && noteEvent.isForAnyCommunity(communities))
        } else if (noteEvent is CommentEvent) {
            // ignore follows and checks only the root scope
            // (authors != null && noteEvent.pubKey in authors) ||
            (hashtags != null && noteEvent.isTaggedHashes(hashtags)) ||
                (hashtagScopes != null && noteEvent.isTaggedScopes(hashtagScopes)) ||
                (geotags != null && noteEvent.isTaggedGeoHashes(geotags)) ||
                (geotagScopes != null && noteEvent.isTaggedScopes(geotagScopes)) ||
                (communities != null && noteEvent.isForAnyCommunity(communities))
        } else {
            (authors != null && noteEvent.pubKey in authors) ||
                (hashtags != null && noteEvent.isTaggedHashes(hashtags)) ||
                (geotags != null && noteEvent.isTaggedGeoHashes(geotags)) ||
                (communities != null && noteEvent.isForAnyCommunity(communities))
        }

    override fun toPerRelayFlow(cache: ICacheProvider): Flow<AllFollowsTopNavPerRelayFilterSet> {
        val authorsPerRelay =
            if (authors != null) {
                OutboxRelayLoader().toAuthorsPerRelayFlow(authors, cache) { it }
            } else {
                MutableStateFlow(emptyMap())
            }

        val communitiesPerRelay =
            if (communities != null) {
                CommunityRelayLoader.toCommunitiesPerRelayFlow(communities, cache) { it }
            } else {
                MutableStateFlow(emptyMap())
            }

        return combine(authorsPerRelay, communitiesPerRelay, defaultRelays, blockedRelays) { perRelayAuthors, perRelayCommunities, default, blockedRelays ->
            val allRelays = (perRelayAuthors.keys + perRelayCommunities.keys).filter { it !in blockedRelays }.ifEmpty { default }

            AllFollowsTopNavPerRelayFilterSet(
                allRelays.associateWith {
                    AllFollowsTopNavPerRelayFilter(
                        authors = perRelayAuthors[it],
                        hashtags = hashtags,
                        geotags = geotags,
                        communities = perRelayCommunities[it],
                    )
                },
            )
        }
    }

    override fun startValue(cache: ICacheProvider): AllFollowsTopNavPerRelayFilterSet {
        val authorsPerRelay =
            if (authors != null) {
                OutboxRelayLoader().authorsPerRelaySnapshot(authors, cache) { it }
            } else {
                emptyMap()
            }

        val communitiesPerRelay =
            if (communities != null) {
                CommunityRelayLoader.communitiesPerRelaySnapshot(communities, cache) { it }
            } else {
                emptyMap()
            }

        val allRelays = (authorsPerRelay.keys + communitiesPerRelay.keys).filter { it !in blockedRelays.value }.ifEmpty { defaultRelays.value }

        return AllFollowsTopNavPerRelayFilterSet(
            allRelays.associateWith {
                AllFollowsTopNavPerRelayFilter(
                    authors = authorsPerRelay[it],
                    hashtags = hashtags,
                    geotags = geotags,
                    communities = communitiesPerRelay[it],
                )
            },
        )
    }
}
