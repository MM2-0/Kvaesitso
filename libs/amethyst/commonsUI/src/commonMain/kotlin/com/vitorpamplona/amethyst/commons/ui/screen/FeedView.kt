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
package com.vitorpamplona.amethyst.commons.ui.screen

import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedLoaded
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.PrefetchLoadedFeedMedia
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.StickToTopOnPrepend
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverLazyGridState
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverLazyListState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.FeedViewModel

@Composable
fun RefresheableFeedView(
    viewModel: FeedViewModel,
    routeForLastRead: String?,
    enablePullRefresh: Boolean = true,
    scrollStateKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onLoaded: (@Composable (FeedState.Loaded, LazyListState) -> Unit)? = null,
) {
    RefresheableBox(viewModel, enablePullRefresh) {
        SaveableFeedState(viewModel.feedState, scrollStateKey) { listState ->
            if (onLoaded != null) {
                RenderFeedState(
                    viewModel,
                    accountViewModel,
                    listState,
                    nav,
                    routeForLastRead,
                    onLoaded = { onLoaded(it, listState) },
                )
            } else {
                RenderFeedState(viewModel, accountViewModel, listState, nav, routeForLastRead)
            }
        }
    }
}

@Composable
fun SaveableFeedState(
    feedContentState: FeedContentState,
    scrollStateKey: String? = null,
    content: @Composable (LazyListState) -> Unit,
) {
    val listState =
        if (scrollStateKey != null) {
            rememberForeverLazyListState(scrollStateKey)
        } else {
            rememberLazyListState()
        }

    WatchScrollToTop(feedContentState, listState)
    StickToTopOnPrepend(feedContentState, listState)

    content(listState)
}

@Composable
fun SaveableGridFeedState(
    viewModel: FeedViewModel,
    scrollStateKey: String? = null,
    content: @Composable (LazyGridState) -> Unit,
) {
    val gridState =
        if (scrollStateKey != null) {
            rememberForeverLazyGridState(scrollStateKey)
        } else {
            rememberLazyGridState()
        }

    WatchScrollToTop(viewModel.feedState, gridState)
    StickToTopOnPrepend(viewModel.feedState, gridState)

    content(gridState)
}

@Composable
fun RenderFeedState(
    viewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String?,
    onLoaded: @Composable (FeedState.Loaded) -> Unit = {
        FeedLoaded(it, listState, routeForLastRead, accountViewModel, nav)
    },
    onEmpty: @Composable () -> Unit = { FeedEmpty { viewModel.invalidateData() } },
    onError: @Composable (String) -> Unit = { FeedError(it) { viewModel.invalidateData() } },
    onLoading: @Composable () -> Unit = { LoadingFeed() },
) {
    val feedState by viewModel.feedState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is FeedState.Empty -> onEmpty()
            is FeedState.FeedError -> onError(state.errorMessage)
            is FeedState.Loaded -> {
                PrefetchLoadedFeedMedia(state, listState, accountViewModel)
                onLoaded(state)
            }
            is FeedState.Loading -> onLoading()
        }
    }
}
