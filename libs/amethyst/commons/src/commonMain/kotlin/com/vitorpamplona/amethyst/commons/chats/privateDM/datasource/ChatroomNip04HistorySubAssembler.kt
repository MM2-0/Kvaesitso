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
package com.vitorpamplona.amethyst.commons.chats.privateDM.datasource

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads older NIP-04 DMs (kind 4) for one conversation by **`until`+`limit` paging, per relay, on
 * demand**. Each relay advances exactly one page when the conversation's on-screen window-limit marker
 * for that relay asks ([advance]); otherwise it parks. Nothing is walked proactively — a relay pages
 * only while its marker is visible and keeps paging while it stays visible.
 *
 * The per-relay cursors live on the conversation's [Chatroom][com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom]
 * (so reopening the room keeps its progress); this class binds the single-active [BackwardRelayPager]
 * orchestrator to the open room's cursors on [newSub], builds the (per-relay scoped) NIP-04 REQ
 * filters, and forwards relay callbacks into the pager. A relay is *done* once it answers an empty page;
 * one that won't answer (auth CLOSE, unreachable, or silent) is flagged *stalled* but kept. [exhausted]
 * flips once every relay is either done or stalled.
 */
class ChatroomNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomQueryState>,
) : PerUserAndFollowListEoseManager<ChatroomQueryState, String>(client, allKeys) {
    private val pager = BackwardRelayPager("convo.nip04.history")

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val status: StateFlow<PagingStatus> = pager.status

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    // This conversation's persistent paging cursors, held on its Chatroom (per account + room).
    private fun cursorsFor(key: ChatroomQueryState) =
        key.account.chatroomList
            .getOrCreatePrivateChatroom(key.room)
            .nip04History

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val relays = nip04DmRelayRouting(key.room.users, key.account)
        if (!key.account.isWriteable() || relays == null) return emptyList()
        if (!key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP04)) return emptyList()

        // Only armed (advanced, not done) relays carry a REQ, each at its own requested cursor. A parked
        // relay keeps the same filter here, so re-assembly (another relay advancing) doesn't re-REQ it.
        val armed = pager.armedRelays(relays.all).toSet()
        if (armed.isEmpty()) return emptyList()
        DmRelayLog.log("convo.nip04.history", key.account)
        val scoped =
            Nip04DmRelayRouting(
                toMeRelays = relays.toMeRelays.filterKeys { it in armed },
                fromMeRelays = relays.fromMeRelays.filterKeys { it in armed },
            )
        return filterNip04DMsHistory(key.account, scoped, pager.pageLimit) { relay ->
            pager.requestedUntilFor(relay)
        }
    }

    /** Steps a single [relay] to its next, older page for the open conversation. Driven by its marker. */
    fun advance(relay: NormalizedRelayUrl) {
        if (pager.advance(relay)) invalidateFilters()
    }

    /** Steps every not-done, not-in-flight relay one page. For a thread too short to scroll. */
    fun advanceAll() {
        if (pager.advanceAll()) {
            Log.d("DMPagination") { "[convo.nip04.history] advanceAll (empty-thread bootstrap)" }
            invalidateFilters()
        }
    }

    override val watchedChatFeeds = setOf(ChatFeedType.NIP04)

    // The toggle flipped: let the pager drop (or restore) its relays before the filters rebuild.
    override fun onChatFeedsToggled() {
        pager.onEnabledChanged()
        invalidateFilters()
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        // The subscription is gone: let go of what the app-wide pager holds for it.
        pager.unbind(key)
    }

    override fun newSub(key: ChatroomQueryState): Subscription {
        // Repoint the single-active orchestrator at this conversation's cursors (on its Chatroom) and the
        // relays it fans out to, refreshing the display flows from the restored progress.
        // With NIP-04 turned off in Settings › Messages the pager neither advances nor shows relays.
        pager.bind(
            cursorsFor(key),
            key.account.scope,
            isEnabled = { key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP04) },
            owner = user(key),
        ) { nip04DmRelayRouting(key.room.users, key.account)?.all }

        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: ChatroomQueryState): SubscriptionListener {
        // A just-backgrounded room's subscription can still deliver after the orchestrator rebinds to
        // another room; gate the pager (single-active) on whether it's still bound to THIS room's cursors
        // so a late callback can't move another room's cursors. newEose (framework bookkeeping) runs anyway.
        val myCursors = cursorsFor(key)
        return object : SubscriptionListener {
            override suspend fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors)) pager.onEvent(relay, event.createdAt)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors) && pager.onEose(relay)) {
                    Log.d("DMPagination") { "[convo.nip04.history] ${relay.url} reached the bottom (done)" }
                }
                newEose(key, relay, TimeUtils.now(), forFilters)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors)) pager.onClosed(relay, message)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                if (pager.isBoundTo(myCursors)) pager.onCannotConnect(relay, message)
            }
        }
    }
}
