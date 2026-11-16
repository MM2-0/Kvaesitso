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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Pure predicate for chip visibility. Extracted so it can be unit-tested
 * without spinning up Compose. Mirrors the inverse of `StickToTopOnPrepend`'s
 * "user is at top" check in `WatchScrollToTop.kt` so the two systems are
 * mutually exclusive: auto-snap when at top, chip when not.
 */
internal fun shouldShowNewPostsChip(
    isAtTop: Boolean,
    currentTopId: String?,
    lastSeenTopId: String?,
): Boolean =
    !isAtTop &&
        currentTopId != null &&
        lastSeenTopId != null &&
        currentTopId != lastSeenTopId

@Stable
class NewPostsChipState internal constructor(
    val visible: State<Boolean>,
    private val acknowledgeCurrentTop: () -> Unit,
    private val animateScrollToTop: suspend () -> Unit,
) {
    /**
     * Tap handler: acknowledge the current top BEFORE the scroll begins so the
     * visibility predicate flips to false immediately and the chip's exit
     * animation runs in parallel with the scroll — visually smoother than
     * waiting until the scroll lands and the predicate updates via the
     * `isAtTop` observer.
     */
    suspend fun dismissAndScrollToTop() {
        acknowledgeCurrentTop()
        animateScrollToTop()
    }
}

/**
 * Derives chip visibility from a [FeedContentState] (live head of the feed)
 * and a [LazyListState] (the user's scroll position). The returned state is
 * keyed on both inputs so that switching feeds (e.g. Following → Global, which
 * recreates the view model and ContentState) resets the chip's "last seen
 * top" baseline.
 */
@Composable
fun rememberNewPostsChipState(
    feedContentState: FeedContentState,
    listState: LazyListState,
): NewPostsChipState {
    val lastSeenTopId = remember(feedContentState) { mutableStateOf<String?>(null) }
    val currentTopId = rememberCurrentTopIdHex(feedContentState)
    val currentTopIdState = rememberUpdatedState(currentTopId)

    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    // Bootstrap the baseline on first paint, and re-acknowledge whenever the
    // user reaches the top by any means (manual scroll, StickToTopOnPrepend
    // auto-snap, or our own chip tap).
    LaunchedEffect(currentTopId, isAtTop) {
        if (lastSeenTopId.value == null && currentTopId != null) {
            lastSeenTopId.value = currentTopId
        } else if (isAtTop && currentTopId != null) {
            lastSeenTopId.value = currentTopId
        }
    }

    val visible =
        remember(lastSeenTopId, currentTopIdState) {
            derivedStateOf {
                shouldShowNewPostsChip(
                    isAtTop = isAtTop,
                    currentTopId = currentTopIdState.value,
                    lastSeenTopId = lastSeenTopId.value,
                )
            }
        }

    return remember(feedContentState, listState) {
        NewPostsChipState(
            visible = visible,
            acknowledgeCurrentTop = {
                lastSeenTopId.value = currentTopIdState.value
            },
            animateScrollToTop = {
                listState.animateScrollToItem(0)
            },
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun rememberCurrentTopIdHex(feedContentState: FeedContentState): String? {
    val flow =
        remember(feedContentState) {
            feedContentState.feedContent.flatMapLatest { state ->
                when (state) {
                    is FeedState.Loaded -> state.feed.map { it.list.firstOrNull()?.idHex }
                    else -> flowOf(null)
                }
            }
        }
    val key by flow.collectAsState(initial = null)
    return key
}
