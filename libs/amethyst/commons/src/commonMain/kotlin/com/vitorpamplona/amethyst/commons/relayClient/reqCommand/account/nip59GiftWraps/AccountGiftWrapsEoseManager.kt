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

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.GiftWrapInbox
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterConcordDirectInvitesToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterGiftWrapsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterMarmotWelcomesToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.paging.WindowLoadTracker
import com.vitorpamplona.amethyst.commons.relayClient.paging.trackingListener
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
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
 * Always-on **live tail** for the account's gift wraps (kind 1059, plus 21059 with NIP-17). It keeps a
 * fixed one-week floor with no upper bound, so the messages list is usable on boot and new incoming
 * messages always stream in. It deliberately never widens: pulling older NIP-17 history is the job of
 * [AccountGiftWrapsHistoryEoseManager], which fetches the past in bounded, one-shot slices so a
 * widen never re-streams what this tail already holds.
 *
 * Several features share the one wrap inbox, so its filter follows the Settings › Messages toggles:
 *  - **NIP-17 on:** every wrap to us on our DM relays — DMs, NIP-AC call signalling (21059), Marmot
 *    Welcomes and Concord Direct Invites all ride it.
 *  - **NIP-17 off, Marmot on:** kind-1059 wraps to us only (no calls: they are off with NIP-17). Welcome
 *    wraps carry no tag that tells them apart, so DMs still download and are dropped after unwrapping.
 *  - **NIP-17 and Marmot off, Concord on:** only the `k=3313` Direct Invite wraps.
 *
 * With Concord on, the `k=3313` filter rides along in every mode, from the invite inbox's cursor rather
 * than the one-week floor, so an older invite on our DM relays is still found.
 *
 * The stock Concord relays, where an invite sender falls back when it can't find our lists, are swept
 * periodically by [com.vitorpamplona.amethyst.commons.model.AccountConcordActions.requestConcordDirectInviteSweep]
 * instead of being held open here.
 */
class AccountGiftWrapsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // The initial-load tracker drives the boot spinner: it stays true until every DM relay has
    // settled (EOSE / CLOSED / cannot-connect) on the one-week tail.
    private val windowLoad = WindowLoadTracker("giftwrap.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val account = key.account
        val toggles = account.chatFeedToggles
        val nip17 = toggles.isEnabled(ChatFeedType.NIP17)
        // The boot spinner is the DM list's: it only waits on the NIP-17 inbox.
        if (!account.isWriteable() || !nip17) windowLoad.setExpectedRelays(emptySet())
        if (!account.isWriteable()) return emptyList()

        val marmot = toggles.isEnabled(ChatFeedType.MARMOT)
        val concord = toggles.isEnabled(ChatFeedType.CONCORD)
        val me = user(key).pubkeyHex
        val relays = account.dmRelays.flow.value
        val sinceTime = DmHistoryTuning.recentBoundary()
        val inviteSince = account.concord.directInviteInbox.since()

        return buildList {
            when (GiftWrapInbox.choose(nip17, marmot, concord)) {
                GiftWrapInbox.EVERYTHING -> {
                    windowLoad.setExpectedRelays(relays.toSet())
                    DmRelayLog.log("giftwrap.live", account)
                    Log.d(TAG) { "[giftwrap.live] REQ since=$sinceTime (no until) on ${relays.size} relay(s): ${relays.map { it.url }}" }
                    relays.forEach { addAll(filterGiftWrapsToPubkey(relay = it, pubkey = me, since = sinceTime)) }
                }

                GiftWrapInbox.MARMOT_WELCOMES -> relays.forEach { addAll(filterMarmotWelcomesToPubkey(relay = it, pubkey = me, since = sinceTime)) }

                GiftWrapInbox.CONCORD_INVITES, GiftWrapInbox.NONE -> {}
            }
            // Concord invites from the invite inbox's cursor on (all of them on a cold start) — the wrap
            // filters above only reach back a week. Same REQ as those, so a relay sends an invite once.
            if (concord) relays.forEach { addAll(filterConcordDirectInvitesToPubkey(relay = it, pubkey = me, since = inviteSince)) }
        }
    }

    override val watchedChatFeeds = setOf(ChatFeedType.NIP17, ChatFeedType.MARMOT, ChatFeedType.CONCORD)

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        windowLoad.startLoading(key.account.scope)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
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

    companion object {
        private const val TAG = "DMPagination"
    }
}
