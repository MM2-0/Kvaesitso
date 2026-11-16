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
package com.vitorpamplona.amethyst.commons.relayClient.polls

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

val PollsFromCommunityKinds =
    listOf(
        PollEvent.KIND,
        ZapPollEvent.KIND,
    )

val PollsFromCommunityKindsStr =
    listOf(
        PollEvent.KIND.toString(),
        ZapPollEvent.KIND.toString(),
    )

fun filterPollsFromAllCommunities(
    relay: NormalizedRelayUrl,
    communities: Set<String>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val communityList = communities.sorted()

    return listOf(
        // approved
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.COMMUNITY_FEED,
                    kinds = CommunityPostApprovalEvent.KIND_LIST,
                    tags =
                        mapOf(
                            "a" to communityList,
                            "k" to PollsFromCommunityKindsStr,
                        ),
                    limit = communityList.size * 20,
                    since = since,
                ),
        ),
        // not approved
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.COMMUNITY_FEED,
                    tags = mapOf("a" to communityList),
                    kinds = PollsFromCommunityKinds,
                    limit = communityList.size * 20,
                    since = since,
                ),
        ),
    )
}

fun filterPollsByAllCommunities(
    communitySet: AllCommunitiesTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (communitySet.set.isEmpty()) return emptyList()

    return communitySet.set
        .mapNotNull {
            filterPollsFromAllCommunities(
                relay = it.key,
                communities = it.value.communities,
                since = since?.get(it.key)?.time ?: defaultSince,
            )
        }.flatten()
}

fun filterPollsFromCommunity(
    relay: NormalizedRelayUrl,
    community: String,
    authors: Set<String>?,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authors = authors?.sorted()
    return listOf(
        // approved
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.COMMUNITY_FEED,
                    authors = authors,
                    kinds = CommunityPostApprovalEvent.KIND_LIST,
                    tags =
                        mapOf(
                            "a" to listOf(community),
                            "k" to PollsFromCommunityKindsStr,
                        ),
                    limit = 100,
                    since = since,
                ),
        ),
        // not approved
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.COMMUNITY_FEED,
                    authors = authors,
                    tags = mapOf("a" to listOf(community)),
                    kinds = PollsFromCommunityKinds,
                    limit = 100,
                    since = since,
                ),
        ),
    )
}

fun filterPollsByCommunity(
    communitySet: SingleCommunityTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (communitySet.set.isEmpty()) return emptyList()

    return communitySet.set
        .mapNotNull {
            filterPollsFromCommunity(
                relay = it.key,
                community = it.value.community,
                authors = it.value.authors,
                since = since?.get(it.key)?.time ?: defaultSince,
            )
        }.flatten()
}
