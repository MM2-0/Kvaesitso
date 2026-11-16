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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.PrefetchLoadedFeedMedia
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.FeedViewModel

@Composable
fun RenderGalleryFeed(
    viewModel: FeedViewModel,
    listState: LazyGridState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by viewModel.feedState.feedContent.collectAsStateWithLifecycle()
    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderDiscoverFeed",
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty { viewModel.invalidateData() }
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage) { viewModel.invalidateData() }
            }

            is FeedState.Loaded -> {
                GalleryFeedLoaded(
                    state,
                    listState,
                    accountViewModel,
                    nav,
                )
            }

            is FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
private fun GalleryFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyGridState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    PrefetchLoadedFeedMedia(loaded, listState, accountViewModel)

    val ratio =
        if (accountViewModel.settings.modernGalleryStyle()) {
            0.8f
        } else {
            1.0f
        }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = FeedPadding,
        state = listState,
        verticalArrangement = spacedBy(1.dp),
        horizontalArrangement = spacedBy(1.dp),
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            GalleryCardCompose(
                baseNote = item,
                modifier =
                    Modifier
                        .aspectRatio(ratio)
                        .fillMaxSize()
                        .animateItem(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
