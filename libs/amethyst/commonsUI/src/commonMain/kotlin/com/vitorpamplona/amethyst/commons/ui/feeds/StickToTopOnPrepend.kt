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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Keeps the user pinned to index 0 when new items prepend to a feed, but
 * only if they were already at the very top right before the update.
 *
 * Why this is non-trivial: every feed uses stable `key = item.idHex` in
 * its lazy list, which makes Compose preserve the visual anchor across
 * data changes. When N items prepend, the user's previously-visible
 * top item is still on screen but its index is now N — so
 * `firstVisibleItemIndex` shifts from 0 to N without any user gesture.
 * A naive `if (firstVisibleItemIndex <= 1) scrollToItem(0)` check inside
 * `LaunchedEffect(items.firstOrNull())` therefore fails as soon as more
 * than one item arrives in the same batch.
 *
 * The trick: track "was at top" continuously via [snapshotFlow], but
 * only flip it from true → false when [LazyListState.isScrollInProgress]
 * is true (i.e. the user is actively scrolling). Compose's keyed-item
 * shift after a data update does not set that flag — only real touch
 * gestures and `animate*` calls do — so data-driven index shifts can
 * never poison the cached value. When [firstItemKey] changes (head of
 * the list moved), if the cached value is still true, snap back to 0
 * with an instant (non-animated) scroll so the prepend appears as
 * in-place growth rather than a visible jump-then-scroll.
 *
 * Most callers should not invoke this directly: `SaveableFeedContentState`,
 * `SaveableGridFeedContentState`, and the analogous wrappers in the app's
 * `ui/screen/FeedView.kt` already apply auto-stick to every feed they
 * own. Invoke the explicit overload only when the listState is
 * constructed outside one of those wrappers, or when the key that
 * should trigger the snap is not the default `items.list[0].idHex`
 * (e.g. notifications, chats, or feeds keyed on something other than a
 * Note's hex id).
 */
@Composable
fun StickToTopOnPrepend(
    listState: LazyListState,
    firstItemKey: Any?,
) {
    stickToTopOnPrepend(
        stateKey = listState,
        firstItemKey = firstItemKey,
        initialAtTop = {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        },
        sampler = {
            snapshotFlow {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        },
        isScrollInProgress = { listState.isScrollInProgress },
        firstVisibleItemIndex = { listState.firstVisibleItemIndex },
        scrollToTop = { listState.scrollToItem(0) },
    )
}

@Composable
fun StickToTopOnPrepend(
    gridState: LazyGridState,
    firstItemKey: Any?,
) {
    stickToTopOnPrepend(
        stateKey = gridState,
        firstItemKey = firstItemKey,
        initialAtTop = {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        },
        sampler = {
            snapshotFlow {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            }
        },
        isScrollInProgress = { gridState.isScrollInProgress },
        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
        scrollToTop = { gridState.scrollToItem(0) },
    )
}

@Composable
fun StickToTopOnPrepend(
    gridState: LazyStaggeredGridState,
    firstItemKey: Any?,
) {
    stickToTopOnPrepend(
        stateKey = gridState,
        firstItemKey = firstItemKey,
        initialAtTop = {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        },
        sampler = {
            snapshotFlow {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            }
        },
        isScrollInProgress = { gridState.isScrollInProgress },
        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
        scrollToTop = { gridState.scrollToItem(0) },
    )
}

/**
 * Auto-stick wired straight to a [FeedContentState]: derives the head
 * key from `feedContent → Loaded.feed → list.firstOrNull()?.idHex` so
 * callers don't have to collect the inner feed flow themselves.
 */
@Composable
fun StickToTopOnPrepend(
    feedContentState: FeedContentState,
    listState: LazyListState,
) {
    StickToTopOnPrepend(listState, rememberFirstItemIdHex(feedContentState))
}

@Composable
fun StickToTopOnPrepend(
    feedContentState: FeedContentState,
    gridState: LazyGridState,
) {
    StickToTopOnPrepend(gridState, rememberFirstItemIdHex(feedContentState))
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun rememberFirstItemIdHex(feedContentState: FeedContentState): String? {
    val flow =
        remember(feedContentState) {
            feedContentState.feedContent.flatMapLatest { state ->
                when (state) {
                    is FeedState.Loaded -> state.feed.map { it.list.firstOrNull()?.idHex }
                    else -> flowOf(null)
                }
            }
        }
    val key by flow.collectAsStateWithLifecycle(initialValue = null)
    return key
}

@Composable
private fun stickToTopOnPrepend(
    stateKey: Any,
    firstItemKey: Any?,
    initialAtTop: () -> Boolean,
    sampler: () -> Flow<Boolean>,
    isScrollInProgress: () -> Boolean,
    firstVisibleItemIndex: () -> Int,
    scrollToTop: suspend () -> Unit,
) {
    // Plain holder instead of mutableStateOf — we only read this inside
    // effects, never in composition, so we don't need snapshot tracking.
    // Seed from the actual restored scroll position: when the user returns
    // to a feed via a saved-state lazy list state, the saved offset is
    // already in place, and a hardcoded `true` would mis-snap them to 0.
    val wasAtTop = remember(stateKey) { booleanArrayOf(initialAtTop()) }

    LaunchedEffect(stateKey) {
        sampler().collect { atTop ->
            if (atTop) {
                wasAtTop[0] = true
            } else if (isScrollInProgress()) {
                wasAtTop[0] = false
            }
        }
    }

    LaunchedEffect(firstItemKey) {
        if (firstItemKey != null && wasAtTop[0] && firstVisibleItemIndex() > 0) {
            scrollToTop()
        }
    }
}
