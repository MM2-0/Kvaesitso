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
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.chatrooms.filterNip04DMsFromMe
import com.vitorpamplona.amethyst.commons.relayClient.chatrooms.filterNip04DMsToMe
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.paging.WindowLoadTracker
import com.vitorpamplona.amethyst.commons.relayClient.paging.trackingListener
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Always-on **live tail** for the account's NIP-04 DMs (kind 4) in the rooms list. Mirrors the
 * gift-wrap live tail: a fixed one-week floor, no upper bound, never widens. Older NIP-04 history is
 * loaded by [ChatroomListNip04HistorySubAssembler] in bounded slices.
 */
class ChatroomListNip04SubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("rooms.nip04.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        if (key.account.isWriteable() && key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP04)) {
            val homeRelays = key.account.homeRelays.flow.value
            val dmRelays = key.account.dmRelays.flow.value
            windowLoad.setExpectedRelays((homeRelays + dmRelays).toSet())
            val sinceTime = DmHistoryTuning.recentBoundary()
            DmRelayLog.log("rooms.nip04.live", key.account)
            Log.d("DMPagination") { "[rooms.nip04.live] REQ since=$sinceTime (no until) fromMe(outbox)=${homeRelays.map { it.url }} toMe(inbox)=${dmRelays.map { it.url }}" }
            homeRelays.map { filterNip04DMsFromMe(key.account.userProfile(), it, sinceTime) } +
                dmRelays.map { filterNip04DMsToMe(key.account.userProfile(), it, sinceTime) }
        } else {
            windowLoad.setExpectedRelays(emptySet())
            emptyList()
        }

    override fun user(key: ChatroomListState) = key.account.userProfile()

    override val watchedChatFeeds = setOf(ChatFeedType.NIP04)

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: ChatroomListState): Subscription {
        val user = user(key)
        windowLoad.startLoading(key.account.scope)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.homeRelays.flow
                        .collectLatest { invalidateFilters() }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow
                        .collectLatest { invalidateFilters() }
                },
            )

        return requestNewSubscription(
            windowLoad.trackingListener { relay, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap.remove(key)?.forEach { it.cancel() }
    }
}
