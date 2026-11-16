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
package com.vitorpamplona.amethyst.commons.relayClient.discover

import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip90DVMs.DiscoveryDvmHeartbeatSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip90DVMs.DvmHeartbeatSources
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds.TopNavFeedQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Key for the Discover screen: one top-nav selection shared by its seven tabs, each with its own
 * feed floor. The three sub-assemblers each bound their REQs by the floors of the tabs they serve.
 */
class DiscoveryQueryState(
    account: IAccount,
    listName: StateFlow<TopFilter>,
    followsPerRelay: StateFlow<IFeedTopNavPerRelayFilterSet>,
    scope: CoroutineScope,
    val reads: FeedContentState,
    val marketplace: FeedContentState,
    val dvms: FeedContentState,
    val followSets: FeedContentState,
    val live: FeedContentState,
    val publicChats: FeedContentState,
    val communities: FeedContentState,
) : TopNavFeedQueryState(
        account,
        listName,
        followsPerRelay,
        scope,
        listOf(reads, marketplace, dvms, followSets, live, publicChats, communities),
    )

class DiscoveryFilterAssembler(
    client: INostrClient,
    dvmHeartbeat: DvmHeartbeatSources,
) : TopNavFeedFilterAssembler<DiscoveryQueryState>({ keys ->
        listOf(
            DiscoveryLongFormClassifiedsAndDVMSubAssembler1(client, keys),
            DiscoveryFollowsSetsAndLiveStreamsSubAssembler2(client, keys),
            DiscoveryPublicChatsAndCommunitiesSubAssembler3(client, keys),
            DiscoveryDvmHeartbeatSubAssembler(client, keys, dvmHeartbeat),
        )
    })
