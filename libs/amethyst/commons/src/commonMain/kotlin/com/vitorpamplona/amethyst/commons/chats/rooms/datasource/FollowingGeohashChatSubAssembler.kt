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
package com.vitorpamplona.amethyst.commons.chats.rooms.datasource

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.commons.service.georelay.GeohashRelays
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps the user's joined geohash location channels ([account.geohashList]) live
 * in [com.vitorpamplona.amethyst.commons.model.cache.LocalCache] so they surface in the rooms
 * list and Home, mirroring [FollowingEphemeralChatSubAssembler]. Because the
 * events are ephemeral, this is a live tail — quiet cells simply have no last
 * message until one arrives.
 */
class FollowingGeohashChatSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> =
        if (!key.account.chatFeedToggles.isEnabled(ChatFeedType.GEOHASH)) {
            emptyList()
        } else {
            listOfNotNull(
                filterFollowingGeohashChats(key.account.geohashList.flow.value, since),
            ).flatten()
        }

    override fun user(key: ChatroomListState) = key.account.userProfile()

    override val watchedChatFeeds = setOf(ChatFeedType.GEOHASH)

    private val userJobMap = ConcurrentMap<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: ChatroomListState): Subscription {
        val user = key.account.userProfile()
        userJobMap.remove(user)?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                // Rebuild when the joined set changes.
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.geohashList.flow.sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
                // Once the live relay directory loads, re-route to the proper nearest relays.
                key.account.scope.launch(Dispatchers.IO) {
                    if (GeohashRelays.ensureLoaded()) invalidateFilters()
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap.remove(key)?.forEach { it.cancel() }
    }
}
