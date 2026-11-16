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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip59GiftWraps

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterGiftWrapsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
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
 * Loads the account's NIP-17 gift-wrap **history** — everything older than the one-week live tail
 * ([AccountGiftWrapsEoseManager]) — by **`until`+`limit` paging, per relay, on demand**.
 *
 * There is no proactive walk: each relay advances exactly one page when the UI calls [advance] for it,
 * and then **parks** at its window limit. The on-screen window-limit markers are the drivers — a relay
 * pages only while its marker is visible, and keeps paging (page after page) as long as it stays visible
 * (see the rooms-list / conversation feed views). So a spam-dense relay never floods: the user has to
 * scroll through its messages to pull more, and nothing is fetched while its marker is off screen.
 *
 * The per-relay cursors live on the account's [ChatroomList][com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList]
 * (so they share the lifetime of the cached gift-wraps); this class binds the single-active
 * [BackwardRelayPager] orchestrator to them on [newSub], builds the gift-wrap REQ filters, and forwards
 * relay callbacks into the pager. A relay is *done* once it answers an empty page; one that won't answer
 * (auth CLOSE, unreachable, or silent) is flagged *stalled* but kept. [exhausted] flips once every relay
 * is either done or stalled.
 */
class AccountGiftWrapsHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // A screen's worth per relay, like notifications: the Messages divider pages while it is on screen
    // and only leaves once enough rooms render, so the old 10,000 default pulled ~5,800 wraps (two
    // NIP-44 decrypts each) in the first minute of a cold start just to fill one list.
    private val pager = BackwardRelayPager("giftwrap.history", pageLimit = 500)

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val status: StateFlow<PagingStatus> = pager.status

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    /**
     * Where the account's NIP-17 history lives: its DM relay list (kind 10050), which is where NIP-17
     * senders deliver, plus any local relay mirroring it. The live tail ([AccountGiftWrapsEoseManager])
     * keeps watching the wider [dmRelays][com.vitorpamplona.amethyst.commons.model.Account.dmRelays] set
     * (NIP-65 inbox, private storage) for the senders that miss the list; paging years of history from
     * those too only walks relays the history is not on. Without a DM list, that wider set is all there is.
     */
    private fun historyRelays(account: Account): Set<NormalizedRelayUrl> {
        val dmList = account.dmRelayList.flow.value
        if (dmList.isEmpty()) return account.dmRelays.flow.value
        return dmList + account.localRelayList.flow.value
    }

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (!key.account.isWriteable()) return emptyList()
        if (!key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP17)) return emptyList()
        // Only relays that have been advanced (armed) and aren't done carry a REQ. A relay that finished a
        // page keeps the same `until` here, so re-assembly (triggered when ANOTHER relay advances) doesn't
        // re-REQ it — it stays parked until the UI advances it again.
        val relays = historyRelays(key.account)
        val armed = pager.armedRelays(relays)
        if (armed.isEmpty()) return emptyList()
        DmRelayLog.log("giftwrap.history", key.account)
        return armed.flatMap { relay ->
            val until = pager.requestedUntilFor(relay) ?: return@flatMap emptyList()
            Log.d(TAG) { "[giftwrap.history] REQ ${relay.url} until ${daysAgo(until)}d, limit=${pager.pageLimit}" }
            filterGiftWrapsToPubkey(relay = relay, pubkey = key.account.userProfile().pubkeyHex, since = null, until = until, limit = pager.pageLimit)
        }
    }

    /** Steps a single [relay] to its next, older page. Driven by that relay's on-screen window-limit marker. */
    fun advance(relay: NormalizedRelayUrl) {
        if (pager.advance(relay)) invalidateFilters()
    }

    /**
     * Steps every not-done, not-in-flight relay one page: for the empty/initial boundary (nothing to
     * scroll), and for a conversation's [ChatHistoryGate][com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryGate].
     * @return true if any relay advanced.
     */
    fun advanceAll(): Boolean {
        if (!pager.advanceAll()) return false
        Log.d(TAG) { "[giftwrap.history] advanceAll" }
        invalidateFilters()
        return true
    }

    override val watchedChatFeeds = setOf(ChatFeedType.NIP17)

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

    override fun newSub(key: AccountQueryState): Subscription {
        // Repoint the single-active orchestrator at this account's gift-wrap cursors (on its ChatroomList)
        // and the relays it fans out to, refreshing the display flows from the restored progress.
        // With NIP-17 turned off in Settings › Messages the pager neither advances nor shows relays.
        pager.bind(
            key.account.chatroomList.giftWrapHistory,
            key.account.scope,
            isEnabled = { key.account.chatFeedToggles.isEnabled(ChatFeedType.NIP17) },
            owner = user(key),
        ) { historyRelays(key.account) }

        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: AccountQueryState): SubscriptionListener {
        // A just-backgrounded account's subscription can still deliver after the orchestrator rebinds to
        // another account; gate the pager (single-active) on whether it's still bound to THIS account's
        // cursors so a late callback can't move another account's cursors. newEose runs regardless.
        val myCursors = key.account.chatroomList.giftWrapHistory
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
                    Log.d(TAG) { "[giftwrap.history] ${relay.url} reached the bottom (done)" }
                }
                // No auto-advance: the relay parks here until its marker asks for the next page.
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

    companion object {
        private const val TAG = "DMPagination"
    }
}
