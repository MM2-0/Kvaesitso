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
package com.vitorpamplona.amethyst.commons.relayClient.music

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent

/**
 * Two REQs per relay: one asks for moderator approvals (NIP-72 kind 4550) whose `k` tag
 * matches our music kinds, the other asks for the raw music events tagged with the
 * community address. Mirrors the long-form / videos pattern so an unapproved track posted
 * to a community still shows up.
 */
fun filterMusicEventsFromAllCommunities(
    relay: NormalizedRelayUrl,
    kinds: List<Int>,
    communities: Set<String>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val communityList = communities.sorted()
    val kindsAsStrings = kinds.map { it.toString() }

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.MEDIA_FEED,
                    kinds = CommunityPostApprovalEvent.KIND_LIST,
                    tags =
                        mapOf(
                            "a" to communityList,
                            "k" to kindsAsStrings,
                        ),
                    limit = communityList.size * 20,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.MEDIA_FEED,
                    kinds = kinds,
                    tags = mapOf("a" to communityList),
                    limit = communityList.size * 20,
                    since = since,
                ),
        ),
    )
}

fun filterMusicEventsByAllCommunities(
    communitySet: AllCommunitiesTopNavPerRelayFilterSet,
    kinds: List<Int>,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (communitySet.set.isEmpty()) return emptyList()

    return communitySet.set
        .mapNotNull {
            filterMusicEventsFromAllCommunities(
                relay = it.key,
                kinds = kinds,
                communities = it.value.communities,
                since = since?.get(it.key)?.time ?: defaultSince,
            )
        }.flatten()
}

fun filterMusicEventsFromCommunity(
    relay: NormalizedRelayUrl,
    kinds: List<Int>,
    community: String,
    authors: Set<String>?,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authorList = authors?.sorted()
    val kindsAsStrings = kinds.map { it.toString() }
    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.MEDIA_FEED,
                    authors = authorList,
                    kinds = CommunityPostApprovalEvent.KIND_LIST,
                    tags =
                        mapOf(
                            "a" to listOf(community),
                            "k" to kindsAsStrings,
                        ),
                    limit = 100,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.MEDIA_FEED,
                    authors = authorList,
                    kinds = kinds,
                    tags = mapOf("a" to listOf(community)),
                    limit = 100,
                    since = since,
                ),
        ),
    )
}

fun filterMusicEventsByCommunity(
    communitySet: SingleCommunityTopNavPerRelayFilterSet,
    kinds: List<Int>,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (communitySet.set.isEmpty()) return emptyList()

    return communitySet.set
        .mapNotNull {
            filterMusicEventsFromCommunity(
                relay = it.key,
                kinds = kinds,
                community = it.value.community,
                authors = it.value.authors,
                since = since?.get(it.key)?.time ?: defaultSince,
            )
        }.flatten()
}
