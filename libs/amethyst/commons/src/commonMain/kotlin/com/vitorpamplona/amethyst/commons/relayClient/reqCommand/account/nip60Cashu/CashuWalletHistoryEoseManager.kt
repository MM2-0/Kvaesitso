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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip60Cashu

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.account.nip60Cashu.filterCashuHistoryToPubkey
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
 * Loads the account's NIP-60 spending **history** (kind:7376) by **`until`+`limit` paging, per relay, on
 * demand**, so the wallet's transaction list can be scrolled back through a wallet's whole lifetime
 * instead of showing whatever suffix one uncapped REQ happened to return.
 *
 * ### Why history needs its own loader
 *
 * [CashuWalletEoseManager] opens one live subscription per outbox relay covering six kinds at once, with
 * no `limit`. A relay answers that with its own cap applied to the newest matching events, and history
 * rows are the most numerous kind in the query — so on a wallet with a few hundred transactions the list
 * is truncated to a recent-N view that differs per device, and no later REQ ever asks for the rest (the
 * EOSE moves `since` forward). That is the same truncation that was silently costing balance, except
 * here the fix is paging rather than a one-shot walk: history is display-only and unbounded, so pulling
 * all of it at launch would be a large download for something the user may never scroll.
 *
 * ### How it pages
 *
 * There is no proactive walk. Each relay advances exactly one page when the transaction list asks
 * ([advance] / [advanceAll]), then **parks** — a relay that finished a page keeps the same `until` in
 * [updateFilter], so re-assembly triggered by *another* relay advancing does not re-REQ it. The list is
 * the driver: it pulls a page on open and another whenever the user scrolls near the end, so nothing is
 * fetched while the wallet is off screen.
 *
 * Paged over the account's **outbox** relays — the same set the wallet publishes its own events to, and
 * so the same set [CashuWalletEoseManager] reads its own kinds back from.
 *
 * ### Floor: no live tail
 *
 * The DM/notification pagers floor at `now − liveTail` because a separate live loader is known to cover
 * everything newer. The wallet has no such guarantee: its live REQ carries no `since` and no `limit`, so
 * how far back it actually reaches is whatever the relay decided — which is the very thing being fixed
 * here. Flooring at a fixed tail would therefore leave a gap between the relay's cap and the tail
 * boundary that neither loader ever asks for. So this pager floors at **now** and overlaps the live
 * subscription completely; duplicates cost nothing (both `LocalCache` and `CashuWalletState.historyEvents`
 * are keyed by event id) and gaplessness is worth more than the overlap.
 *
 * The per-relay cursors live on the [Account] (so they share the account's lifetime); this class binds
 * the single-active [BackwardRelayPager] to them on [newSub], builds the REQ filters, and forwards relay
 * callbacks into the pager. A relay is *done* once it answers an empty page; one that will not answer
 * (auth CLOSE, unreachable, silent) is flagged *stalled* but kept, and [PagingStatus.exhausted] flips
 * once every relay is done or stalled — callers rendering a terminal state should split on
 * [PagingStatus.stalledCount].
 */
class CashuWalletHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // liveTailSeconds = 0 pins the floor at `now` — see the class doc on why the wallet, unlike DMs,
    // cannot assume a live loader already covers a recent window.
    private val pager = BackwardRelayPager("cashu.history", pageLimit = PAGE_LIMIT, liveTailSeconds = 0L)

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val status: StateFlow<PagingStatus> = pager.status

    /** The relays this account pages its own NIP-60 history back through: where it publishes. */
    private fun historyRelaySet(account: Account): Set<NormalizedRelayUrl> = account.outboxRelays.flow.value

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val pubkey = user(key).pubkeyHex
        val relays = historyRelaySet(key.account)

        // Only relays that have been advanced (armed) and aren't done carry a REQ. A relay that finished
        // a page keeps the same `until` here, so re-assembly (triggered when ANOTHER relay advances)
        // doesn't re-REQ it — it stays parked until the list advances it again.
        val armed = pager.armedRelays(relays)
        if (armed.isEmpty()) return emptyList()

        return armed.flatMap { relay ->
            val until = pager.requestedUntilFor(relay) ?: return@flatMap emptyList()
            Log.d(TAG) { "[cashu.history] REQ ${relay.url} until=$until limit=${pager.pageLimit}" }
            filterCashuHistoryToPubkey(relay, pubkey, until, pager.pageLimit)
        }
    }

    /** Steps a single [relay] to its next, older page. */
    fun advance(relay: NormalizedRelayUrl) {
        if (pager.advance(relay)) invalidateFilters()
    }

    /** Steps every not-done, not-in-flight relay one page. What the transaction list drives. */
    fun advanceAll() {
        if (pager.advanceAll()) {
            Log.d(TAG) { "[cashu.history] advanceAll" }
            invalidateFilters()
        }
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        // Repoint the single-active orchestrator at this account's cashu-history cursors and the relay
        // set it fans out to, refreshing the display flows from the restored progress.
        pager.bind(key.account.cashuHistory, key.account.scope, owner = user(key)) { historyRelaySet(key.account) }

        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                // A relay joining/leaving the outbox set re-issues the REQ so a newly-added relay can be
                // armed and a removed one drops out. Sampled — a relay-list edit lands as a burst.
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.outboxRelays.flow
                        .sample(1000)
                        .collectLatest { invalidateFilters() }
                },
            )

        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: AccountQueryState): SubscriptionListener {
        // A just-backgrounded account's subscription can still deliver after the orchestrator rebinds to
        // another account; gate the pager (single-active) on whether it's still bound to THIS account's
        // cursors so a late callback can't move another account's cursors. newEose runs regardless.
        val myCursors = key.account.cashuHistory
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
                    Log.d(TAG) { "[cashu.history] ${relay.url} reached the bottom (done)" }
                }
                // No auto-advance: the relay parks here until the transaction list asks for another page.
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
        private const val TAG = "CashuPagination"

        /**
         * Rows pulled per relay per advance. Smaller than the notification pager's 500: every kind:7376
         * row costs a NIP-44 decrypt to render, which on an external signer is an out-of-process
         * round-trip, so a page is sized to fill a screen or two rather than to fill memory.
         */
        const val PAGE_LIMIT = 100
    }
}
