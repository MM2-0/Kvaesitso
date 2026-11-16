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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterGroupNotificationsHistoryToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterNotificationsHistoryToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Loads the account's notification **history** — everything older than the one-week live tail
 * ([AccountNotificationsEoseFromInboxRelaysManager]) — by **`until`+`limit` paging, per relay, on
 * demand**, so the notifications feed can be scrolled back in time instead of being pinned to the
 * recent week.
 *
 * There is no proactive walk: each relay advances exactly one page when the feed's on-screen
 * window-limit marker for that relay asks ([advance]), then **parks** at its window limit. The markers
 * are the drivers — a relay pages only while its marker is visible, and keeps paging as long as it
 * stays visible (see the notifications card feed). So a spam-dense relay never floods: the user has to
 * scroll through its notifications to pull more, and nothing is fetched while its marker is off screen.
 *
 * Relays paged: the same set the live inbox loader covers — the user's inbox relays (all notification
 * kinds tagging me) plus each joined NIP-29 group's host relay (group-activity kinds scoped by `#h`).
 * The foreground "random follows" straggler query ([AccountNotificationsEoseFromRandomRelaysManager],
 * tiny latest-N limits) is deliberately live-tail only and is NOT paged here.
 *
 * The per-relay cursors live on the [Account] (so they share the account's lifetime); this class binds
 * the single-active [BackwardRelayPager] orchestrator to them on [newSub], builds the notification REQ
 * filters, and forwards relay callbacks into the pager. A relay is *done* once it answers an empty page;
 * one that won't answer (auth CLOSE, unreachable, or silent) is flagged *stalled* but kept. [exhausted]
 * flips once every relay is either done or stalled.
 */
class AccountNotificationsHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // A modest page: each marker-triggered advance pulls ~500 older notifications, digestible to render
    // and enough to fill a scroll, rather than the gift-wrap default (a whole encrypted-blob band at once).
    private val pager = BackwardRelayPager("notifications.history", pageLimit = 500)

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val status: StateFlow<PagingStatus> = pager.status

    // Each joined group's id, bucketed by the normalized host relay it lives on. Used both to route the
    // group filter and (its keys) to add group host relays to the paged relay set. Buzz DM channels aren't
    // in the published list (server-side membership, tracked in BuzzDmChannels), so include them here too —
    // otherwise paging back never loads older reactions/zaps on my DM messages. Hidden DMs are excluded.
    private fun groupsByRelay(account: Account): Map<NormalizedRelayUrl, List<String>> {
        val myPubkey = account.userProfile().pubkeyHex
        val hiddenDms = BuzzDmRegistry.hiddenFor(myPubkey)

        val listGroups =
            account.relayGroupList.liveRelayGroupList.value
                .mapNotNull { tag -> RelayUrlNormalizer.normalizeOrNull(tag.relayUrl)?.let { it to tag.groupId } }
        val dmGroups =
            BuzzDmChannels
                .channelsFor(myPubkey)
                .filterKeys { it !in hiddenDms }
                .map { (channelId, relay) -> relay to channelId }

        return (listGroups + dmGroups)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ids) -> ids.distinct() }
    }

    // The full relay set this account pages notifications back through: inbox relays + group host relays.
    private fun notificationRelaySet(account: Account): Set<NormalizedRelayUrl> = account.notificationRelays.flow.value + groupsByRelay(account).keys

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (!key.account.isWriteable()) return emptyList()

        val pubkey = user(key).pubkeyHex
        val inbox = key.account.notificationRelays.flow.value
        val groups = groupsByRelay(key.account)

        // Only relays that have been advanced (armed) and aren't done carry a REQ. A relay that finished a
        // page keeps the same `until` here, so re-assembly (triggered when ANOTHER relay advances) doesn't
        // re-REQ it — it stays parked until the marker advances it again.
        val armed = pager.armedRelays(inbox + groups.keys)
        if (armed.isEmpty()) return emptyList()

        return armed.flatMap { relay ->
            val until = pager.requestedUntilFor(relay) ?: return@flatMap emptyList()
            Log.d(TAG) { "[notifications.history] REQ ${relay.url} until=$until limit=${pager.pageLimit}" }
            buildList {
                if (relay in inbox) {
                    addAll(filterNotificationsHistoryToPubkey(relay, pubkey, until, pager.pageLimit))
                }
                groups[relay]?.let { groupIds ->
                    addAll(filterGroupNotificationsHistoryToPubkey(relay, pubkey, groupIds, until, pager.pageLimit))
                }
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
            Log.d(TAG) { "[notifications.history] advanceAll (empty-feed bootstrap)" }
            invalidateFilters()
        }
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        // Repoint the single-active orchestrator at this account's notification cursors and the relay set
        // it fans out to, refreshing the display flows from the restored progress.
        pager.bind(key.account.notificationHistory, key.account.scope, owner = user(key)) { notificationRelaySet(key.account) }

        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                // A relay joining/leaving the paged set (inbox change, group join/leave) re-issues the REQ
                // so a newly-added relay can be armed and a removed one drops out.
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.notificationRelays.flow
                        .sample(1000)
                        .collectLatest { invalidateFilters() }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.relayGroupList.liveRelayGroupList
                        .sample(1000)
                        .collectLatest { invalidateFilters() }
                },
                // A Buzz DM discovered/hidden adds or drops its host relay from the paged set.
                key.account.scope.launch(Dispatchers.IO) {
                    BuzzDmChannels.flow.sample(1000).collectLatest { invalidateFilters() }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    BuzzDmRegistry.hidden.sample(1000).collectLatest { invalidateFilters() }
                },
            )

        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: AccountQueryState): SubscriptionListener {
        // A just-backgrounded account's subscription can still deliver after the orchestrator rebinds to
        // another account; gate the pager (single-active) on whether it's still bound to THIS account's
        // cursors so a late callback can't move another account's cursors. newEose runs regardless.
        val myCursors = key.account.notificationHistory
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
                    Log.d(TAG) { "[notifications.history] ${relay.url} reached the bottom (done)" }
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

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap.remove(key)?.forEach { it.cancel() }
        // The subscription is gone: let go of what the app-wide pager holds for it.
        pager.unbind(key)
    }

    companion object {
        private const val TAG = "NotificationPagination"
    }
}
