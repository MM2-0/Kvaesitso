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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource.ConcordChannelFilterAssembler
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource.ConcordChannelQueryState
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Mount on any screen that lists the user's joined Concord Channels (the Messages
 * tab, the Concord home) to keep their planes live and their folded metadata in
 * the LocalCache channel index.
 *
 * The query state is keyed on the account (stable), so the assembler wouldn't
 * re-run its filter derivation on its own when a community folds or the joined set
 * changes. We watch [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager.revision]
 * — bumped on every join/leave and every Control-Plane fold — and on each change:
 *  1. refresh the LocalCache [com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel]
 *     rows from the freshly-folded state, so list/chat UIs see the new name,
 *     channels and membership, then
 *  2. invalidate the assembler so a newly-revealed channel plane is subscribed
 *     (its Chat Plane address is only known after the Control Plane folds).
 */
@Composable
fun ConcordChannelSubscription(
    dataSource: ConcordChannelFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account) { ConcordChannelQueryState(account) }

    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        // The channel-index refresh (community name/icon, membership, ban pruning) runs
        // account-wide from Account on this same revision, so the Messages tab has chips even
        // when this screen was never opened. Here we only need to re-derive the subscription
        // filters, since a newly-folded channel plane must now be subscribed.
        dataSource.invalidateFilters()
    }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * Always-on account-level preload of every joined community's Control (and folded Chat) planes,
 * mounted once high in the logged-in tree ([com.vitorpamplona.amethyst.commons.ui.app.AmethystApp])
 * — the Concord analog of the always-on account/DM gift-wrap tail
 * ([com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssemblerSubscription]).
 *
 * Concord control-plane wraps are addressed to *derived stream keys*, not `#p=self`, so the always-on
 * DM tail never picks them up — without this, communities only fold (and thus reveal their channels,
 * metadata/icon and membership) while a Concord screen happens to be open. Uses the non-lifecycle
 * [KeyDataSourceSubscription] so the planes stay requested app-wide, exactly like DMs, and keeps the
 * same [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager.revision] watch so a
 * fresh fold subscribes its newly-revealed channel planes.
 *
 * Preloading a community's planes requires its keys, which come from the private kind-13302 list —
 * so a community pinned to the bottom bar whose list never reached the cache preloads nothing and
 * shows a blank tab + server screen ("doesn't load at all"). That list often lives only on the
 * community's own relays (Armada/Vector publish it there, never to the user's outbox), so before we
 * can preload we may first have to fetch it: [bootstrapPinnedCommunities] imports the list for any
 * pinned community we don't yet know, from the relays saved on its tab. Once it folds into the cache,
 * [com.vitorpamplona.amethyst.commons.model.concord.ConcordChannelListState.liveCommunities] surfaces
 * the entry, the tab/screen fill in, and the plane preload above picks it up.
 */
@Composable
fun ConcordChannelPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val dataSource = accountViewModel.dataSources().concordChannels
    val state = remember(account) { ConcordChannelQueryState(account) }

    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        dataSource.invalidateFilters()
    }

    bootstrapPinnedCommunities(accountViewModel)

    // COMPLETE-mode Control-Plane sweep: page every joined community's whole Control Plane (no `since`,
    // past the relay's per-filter cap) so the channel/roster/banlist fold is never silently truncated.
    ConcordControlPlaneSync(accountViewModel)

    // Warm the last-message preview of every channel of every joined community (one drain per relay),
    // so the Messages inbox shows a preview for channels the user has never opened.
    ConcordChannelPreviewAccountPreload(accountViewModel)

    KeyDataSourceSubscription(state, dataSource)
}

/**
 * Account-wide COMPLETE-mode sync of every joined community's Control Plane — the completeness pass
 * that pairs with the always-on live subscription. The live plane subscription
 * ([ConcordChannelFilterAssembler]) carries brand-new editions in real time, but it advances a
 * `since` cursor and rides the relay's per-filter cap, so it can miss an edition below the high-water
 * mark or a cropped initial page — either of which folds a partial channel list / stale roster.
 * [com.vitorpamplona.amethyst.commons.model.Account.syncConcordControlPlanes] closes both by re-fetching the
 * whole plane with no `since`, paging past the cap.
 *
 * It fires only on the **events** that can create a gap — never on a wall-clock poll, because a
 * persistent live subscription already keeps a connected relay complete:
 *  1. **Load / membership / held-epoch change** — keyed on a signature of the joined set + held
 *     epochs (the moments a new Control-Plane address appears: join/leave, Refounding). NOT the fold
 *     revision — a revision-keyed sweep would loop (drain → ingest → fold → revision → drain).
 *  2. **Reconnect** — a relay hosting a joined community that comes back online may have missed
 *     editions published while it was down; that gap is exactly what the live sub can't backfill. We
 *     re-sweep when the connected-relay set *gains* one of our relays, coalescing flaps with a min
 *     interval.
 */
@Composable
private fun ConcordControlPlaneSync(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()
    // Always-current set for the reconnect collector, whose effect is keyed on relays (which don't
    // change when a same-relay community leaves) — so it must not close over a stale `communities`.
    val liveCommunities by rememberUpdatedState(communities)

    val sig =
        remember(communities) {
            communities.joinToString(",") { entry ->
                "${entry.id}@${entry.rootEpoch}:" + entry.heldRoots.joinToString("-") { it.epoch.toString() }
            }
        }

    // (1) Load + membership/epoch change: one complete sweep of the whole set.
    LaunchedEffect(sig) {
        if (communities.isNotEmpty()) account.concord.syncConcordControlPlanes(communities)
    }

    // (2) Reconnect: re-sweep when a relay of ours transitions disconnected → connected.
    val ourRelays =
        remember(communities) {
            communities
                .flatMap { it.relays }
                .mapNotNullTo(HashSet()) { RelayUrlNormalizer.normalizeOrNull(it) }
        }
    LaunchedEffect(ourRelays) {
        if (ourRelays.isEmpty()) return@LaunchedEffect
        val connectedFlow = account.client.connectedRelaysFlow()
        // Baseline = already-open relays; the (1) sweep covered those, so only NEW connections sweep.
        var open = connectedFlow.value.intersect(ourRelays)
        var lastSweep = 0L
        connectedFlow.collect { nowConnected ->
            val ours = nowConnected.intersect(ourRelays)
            val newlyUp = ours - open
            open = ours
            if (newlyUp.isEmpty()) return@collect
            val now = TimeUtils.nowMillis()
            if (now - lastSweep < RECONNECT_RESWEEP_MIN_INTERVAL_MS) return@collect
            lastSweep = now
            account.concord.syncConcordControlPlanes(liveCommunities)
        }
    }
}

/** Coalesce relay flaps: at most one reconnect-driven completeness sweep per this window. */
private const val RECONNECT_RESWEEP_MIN_INTERVAL_MS = 60_000L

/**
 * Fetch the private kind-13302 list of any Concord community pinned to the bottom bar whose list we
 * don't already have, from the relays saved on its tab (see
 * [com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel.importConcordCommunities]). Runs
 * app-wide as part of [ConcordChannelPreload], so a pinned community loads without the user ever
 * opening the Concord hub (which has its own import). Keyed on the exact set of missing communities,
 * so the (slow, stock-relay) fetch runs when a new gap appears — a freshly pinned community we can't
 * yet resolve — and not on every recomposition; it stops once every pinned community is known.
 */
@Composable
private fun bootstrapPinnedCommunities(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val items by accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
        .collectAsStateWithLifecycle()
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()

    val missingPinned =
        remember(items, communities) {
            val known = communities.mapTo(HashSet()) { it.id }
            items
                .mapNotNull {
                    // Both a pinned community and a pinned channel need their community's list fetched.
                    when (it) {
                        is BottomBarEntry.Concord -> it.communityId
                        is BottomBarEntry.ConcordChannel -> it.communityId
                        else -> null
                    }
                }.filter { it !in known }
                .sorted()
                .toSet()
        }

    LaunchedEffect(missingPinned) {
        if (missingPinned.isNotEmpty()) accountViewModel.importConcordCommunities()
    }
}
