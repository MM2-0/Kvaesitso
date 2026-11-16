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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.chatrooms.filterNip04DMsFromMe
import com.vitorpamplona.amethyst.commons.relayClient.chatrooms.filterNip04DMsToMe
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
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
 * Loads older NIP-04 DMs (kind 4) for the rooms list by **`until`+`limit` paging, per relay, on
 * demand** — the same model as the gift-wrap history loader
 * ([com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager]),
 * across the account's home (outbox, *from me*) + DM (inbox, *to me*) relays. Each relay advances one
 * page when its on-screen window-limit marker asks ([advance]); otherwise it parks. Nothing is walked
 * proactively. The per-relay cursors live on the account's
 * [ChatroomList][com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList]; the single-active
 * [BackwardRelayPager] orchestrator binds to them on [newSub].
 */
class ChatroomListNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    private fun allRelays(account: Account) = (account.homeRelays.flow.value + account.dmRelays.flow.value).toSet()

    private val pager = BackwardRelayPager("rooms.nip04.history")

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val status: StateFlow<PagingStatus> = pager.status

    override fun user(key: ChatroomListState) = key.account.userProfile()

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val user = user(key)
        if (!key.account.isWriteable()) return emptyList()
        if (!key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP04)) return emptyList()
        val homeRelays = key.account.homeRelays.flow.value
        val dmRelays = key.account.dmRelays.flow.value
        val armed = pager.armedRelays((homeRelays + dmRelays).toSet())
        if (armed.isEmpty()) return emptyList()
        DmRelayLog.log("rooms.nip04.history", key.account)
        return armed.flatMap { relay ->
            val until = pager.requestedUntilFor(relay) ?: return@flatMap emptyList()
            buildList {
                if (relay in homeRelays) add(filterNip04DMsFromMe(user, relay, since = null, until = until, limit = pager.pageLimit))
                if (relay in dmRelays) add(filterNip04DMsToMe(user, relay, since = null, until = until, limit = pager.pageLimit))
            }
        }
    }

    /** Steps a single [relay] to its next, older page. Driven by that relay's on-screen window-limit marker. */
    fun advance(relay: NormalizedRelayUrl) {
        if (pager.advance(relay)) invalidateFilters()
    }

    /** Steps every not-done, not-in-flight relay one page. For the empty/initial boundary (nothing to scroll). */
    fun advanceAll() {
        if (pager.advanceAll()) {
            Log.d("DMPagination") { "[rooms.nip04.history] advanceAll (empty-feed bootstrap)" }
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

    override fun newSub(key: ChatroomListState): Subscription {
        // Repoint the single-active orchestrator at this account's rooms-list NIP-04 cursors (on its
        // ChatroomList) and the relays it fans out to, refreshing the flows from the restored progress.
        // With NIP-04 turned off in Settings › Messages the pager neither advances nor shows relays.
        pager.bind(
            key.account.chatroomList.nip04History,
            key.account.scope,
            isEnabled = { key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP04) },
            owner = user(key),
        ) { allRelays(key.account) }

        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: ChatroomListState): SubscriptionListener {
        // A just-backgrounded account's subscription can still deliver after the orchestrator rebinds to
        // another account; gate the pager (single-active) on whether it's still bound to THIS account's
        // cursors so a late callback can't move another account's cursors. newEose runs regardless.
        val myCursors = key.account.chatroomList.nip04History
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
                    Log.d("DMPagination") { "[rooms.nip04.history] ${relay.url} reached the bottom (done)" }
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
