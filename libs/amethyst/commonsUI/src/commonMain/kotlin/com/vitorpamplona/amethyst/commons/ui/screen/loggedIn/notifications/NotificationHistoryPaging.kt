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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsHistoryEoseManager
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachSentinels
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * Drives the notifications feed's infinite-scroll backward pagination over the account's per-relay
 * [AccountNotificationsHistoryEoseManager] (a [BackwardRelayPager][com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager]:
 * each relay keeps its own until+limit cursor so faulty relays with different datasets page independently
 * and can't gap each other) and returns the per-relay [RelayReachCursor]s the feed draws as frontier
 * markers.
 *
 * Three cooperating drivers, all gated on [drivesPaging] so only the on-screen feed pages the shared
 * account pager (an off-screen split tab / second pane stays idle):
 *  1. **Look-ahead buffer** — keeps a fat runway of older notifications loaded ahead of the viewport, so
 *     healthy relays fill the feed and the user practically never reaches the end. Bounded per burst
 *     ([NOTIFICATION_MAX_PAGES_PER_BURST]) and reset by scrolling, so a dense account whose events collapse
 *     into few cards can't burst-download its whole history to hit the row target, while a normal account
 *     still preloads the full look-ahead from the top.
 *  2. **Auto-retry** — when every relay is done-or-stalled (exhausted) but some are merely stalled (a
 *     slow/unreachable relay, not a real end), re-advances them on a backoff so recovery doesn't depend on
 *     the user scrolling to the marker or reopening.
 *  3. **Per-relay sentinels** — retry an individual relay the moment its frontier marker scrolls into view;
 *     the buffer keeps the frontier below the fold, so these stay quiet unless the buffer can't keep up.
 *
 * @param createdAtAt createdAt of the card at a LazyColumn index (null past the ends / non-card rows), so
 *   the hoisted sentinel can test which inter-card gap a relay's cursor sits in against the visible rows.
 */
@Composable
fun rememberNotificationHistoryPaging(
    history: AccountNotificationsHistoryEoseManager,
    listState: LazyListState,
    drivesPaging: Boolean,
    createdAtAt: (index: Int) -> Long?,
): List<RelayReachCursor> {
    val historyStatus by history.status.collectAsStateWithLifecycle()
    val exhausted = historyStatus.exhausted
    val loadingMore by history.loadingMore.collectAsStateWithLifecycle()

    // Keep a big runway of already-loaded rows below the fold so the user effectively never reaches the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleIndex >= totalItems - NOTIFICATION_LOOKAHEAD_BUFFER
        }
    }

    // Bound the eager fill. Pages are pulled in events but the buffer is counted in rows, and notifications
    // collapse heavily into cards — so on a dense account a page can add very few rows, and an uncapped fill
    // would keep pulling until it downloaded the whole history to reach the row target. Cap the consecutive
    // pages pulled WITHOUT the user scrolling; scrolling (firstVisibleItemIndex moving) resets the budget so
    // paging resumes as the buffer is consumed. From position 0 this still preloads the full look-ahead for a
    // normal account (1–2 pages), yet a dense whale can't burst-download everything on open.
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var pagesThisBurst by remember { mutableIntStateOf(0) }
    LaunchedEffect(firstVisibleIndex) { pagesThisBurst = 0 }

    // Re-evaluated when the buffer runs low, a page settles (loadingMore falls), paging exhausts, this feed
    // (de)activates, or the burst budget changes — so a page that doesn't refill the buffer keeps pulling the
    // next (up to the burst cap) until the buffer is full or relays run dry.
    LaunchedEffect(drivesPaging, shouldLoadMore, loadingMore, exhausted, pagesThisBurst) {
        if (drivesPaging && shouldLoadMore && !loadingMore && !exhausted && pagesThisBurst < NOTIFICATION_MAX_PAGES_PER_BURST) {
            history.advanceAll()
            pagesThisBurst++
        }
    }

    // Auto-retry faulty relays with backoff, only while this feed drives paging. A single non-restarting
    // loop so the backoff survives the transient in-flight blips each retry causes.
    LaunchedEffect(history, drivesPaging) {
        if (!drivesPaging) return@LaunchedEffect
        var backoffMs = STALLED_RETRY_MIN_MS
        while (true) {
            history.status.first { it.exhausted && it.stalledCount > 0 } // park until stuck on a stalled relay
            while (true) {
                delay(backoffMs)
                val s = history.status.value
                if (!(s.exhausted && s.stalledCount > 0)) break // recovered (a relay answered, or scroll retried)
                history.advanceAll()
                history.loadingMore.first { !it } // let the retry settle before escalating
                backoffMs = (backoffMs * 2).coerceAtMost(STALLED_RETRY_MAX_MS)
            }
            backoffMs = STALLED_RETRY_MIN_MS // reset for the next stall
        }
    }

    // One cursor per relay: its reached depth, state (reaching / stalled / done) and the advance() that pulls
    // its next page. A done relay's marker sinks to the oldest end reading "fully loaded".
    val limits =
        remember(historyStatus) {
            historyStatus.relayProgress.map { (relay, p) ->
                RelayReachCursor(relay.url, relayShortName(relay), p.reachedUntil, reachState(p)) { history.advance(relay) }
            }
        }

    // Per-relay retry driver: when a relay's frontier marker is on screen (the buffer couldn't keep the
    // frontier ahead, i.e. that relay stalled or the feed is genuinely at its end), step that one relay.
    if (drivesPaging && limits.isNotEmpty()) {
        RelayReachSentinels(limits, listState, createdAtAt)
    }

    return limits
}

/**
 * Bootstraps notification history while the feed is genuinely empty: steps every relay one page at a
 * time, gated on its own loader, until notifications appear or every relay exhausts. Once cards load this
 * stops and the look-ahead buffer driver takes over, keeping older pages loaded ahead of the viewport.
 *
 * Leads with a debounce so the brief Empty/Loading flash navigation passes through does NOT trigger a
 * hunt; if [active] drops before it elapses (cards loaded) the effect cancels and nothing pages.
 */
@Composable
fun BootstrapNotificationHistoryWhenEmpty(
    active: Boolean,
    loadingMore: StateFlow<Boolean>,
    status: StateFlow<PagingStatus>,
    advanceAll: () -> Unit,
) {
    LaunchedEffect(active, loadingMore, status) {
        if (!active) return@LaunchedEffect
        delay(BOOTSTRAP_DEBOUNCE_MS)
        combine(loadingMore, status) { loading, s -> !loading && !s.exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { advanceAll() }
    }
}

// Ignore the transient empty feed that navigation flashes through before notifications re-appear.
private const val BOOTSTRAP_DEBOUNCE_MS = 1200L

// How many already-loaded rows to keep below the last visible one before pulling the next older page.
// Large on purpose: the feed reads as infinite scroll, the user practically never reaches the bottom.
private const val NOTIFICATION_LOOKAHEAD_BUFFER = 100

// Cap on consecutive pages pulled to fill the buffer WITHOUT the user scrolling (the budget resets on
// scroll). Generous so a normal account preloads the full look-ahead from the top in 1–2 pages, while a
// dense account whose events collapse into few cards is bounded instead of burst-downloading everything.
private const val NOTIFICATION_MAX_PAGES_PER_BURST = 6

// Backoff bounds for auto-retrying stalled (slow/unreachable) relays: first retry ~3s after a stall,
// doubling up to ~30s, so a faulty relay is retried gently but keeps a chance to recover on its own.
private const val STALLED_RETRY_MIN_MS = 3_000L
private const val STALLED_RETRY_MAX_MS = 30_000L

private fun reachState(p: RelayPagingProgress): RelayReachState =
    when {
        p.done -> RelayReachState.DONE
        p.stalled -> RelayReachState.STALLED
        else -> RelayReachState.REACHING
    }

private fun relayShortName(relay: NormalizedRelayUrl): String =
    relay.url
        .substringAfter("://")
        .trimEnd('/')
        .substringBefore('/')
