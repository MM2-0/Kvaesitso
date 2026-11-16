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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.notifications.CardFeedContentState

@Composable
fun WatchScrollToTop(
    feedContentState: FeedContentState,
    listState: LazyListState,
) {
    val scrollToTop by feedContentState.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && feedContentState.scrollToTopPending) {
            listState.scrollToItem(index = 0)
            feedContentState.sentToTop()
        }
    }

    ReportToBackToTop(listState) { feedContentState.sendToTop() }
}

@Composable
fun WatchScrollToTop(
    feedContentState: FeedContentState,
    listState: LazyStaggeredGridState,
) {
    val scrollToTop by feedContentState.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && feedContentState.scrollToTopPending) {
            listState.scrollToItem(index = 0)
            feedContentState.sentToTop()
        }
    }

    ReportToBackToTop(listState) { feedContentState.sendToTop() }
}

@Composable
fun WatchScrollToTop(
    feedContentState: FeedContentState,
    listState: LazyGridState,
) {
    val scrollToTop by feedContentState.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && feedContentState.scrollToTopPending) {
            listState.scrollToItem(index = 0)
            feedContentState.sentToTop()
        }
    }

    ReportToBackToTop(listState) { feedContentState.sendToTop() }
}

@Composable
fun WatchScrollToTop(
    videoFeedContentState: FeedContentState,
    pagerState: PagerState,
) {
    val scrollToTop by videoFeedContentState.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && videoFeedContentState.scrollToTopPending) {
            pagerState.scrollToPage(page = 0)
            videoFeedContentState.sentToTop()
        }
    }
}

@Composable
fun WatchScrollToTop(
    feedContent: CardFeedContentState,
    listState: LazyListState,
) {
    val scrollToTop by feedContent.scrollToTop.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0 && feedContent.scrolltoTopPending) {
            listState.scrollToItem(index = 0)
            feedContent.sentToTop()
        }
    }

    ReportToBackToTop(listState) { feedContent.sendToTop() }
}
