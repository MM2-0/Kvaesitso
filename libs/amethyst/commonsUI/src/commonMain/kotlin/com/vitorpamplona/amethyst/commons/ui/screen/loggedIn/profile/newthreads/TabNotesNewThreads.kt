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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.newthreads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.profile.newthreads.dal.UserProfileNewThreadsFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.pinnedNotes.dal.UserProfilePinnedNotesFeedViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.PrefetchLoadedFeedMedia
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.StickToTopOnPrepend
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun TabNotesNewThreads(
    feedViewModel: UserProfileNewThreadsFeedViewModel,
    pinnedNotesFeedViewModel: UserProfilePinnedNotesFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LaunchedEffect(Unit) { pinnedNotesFeedViewModel.invalidateData() }

    Column(Modifier.fillMaxHeight()) {
        RefresheableBox(feedViewModel, enablePullRefresh = false) {
            val listState =
                androidx.compose.foundation.lazy
                    .rememberLazyListState()

            WatchScrollToTop(feedViewModel.feedState, listState)

            val feedState by feedViewModel.feedState.feedContent.collectAsStateWithLifecycle()
            val pinnedFeedState by pinnedNotesFeedViewModel.feedState.feedContent.collectAsStateWithLifecycle()

            when (val state = feedState) {
                is FeedState.Empty -> {
                    val pinnedLoaded = pinnedFeedState as? FeedState.Loaded
                    if (pinnedLoaded != null) {
                        val pinnedItems by pinnedLoaded.feed.collectAsStateWithLifecycle()
                        if (pinnedItems.list.isNotEmpty()) {
                            FeedLoadedWithPinnedNotes(
                                pinnedFeedState = pinnedLoaded,
                                loaded = null,
                                listState = listState,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        } else {
                            FeedEmpty { feedViewModel.invalidateData() }
                        }
                    } else {
                        FeedEmpty { feedViewModel.invalidateData() }
                    }
                }

                is FeedState.FeedError -> {
                    FeedError(state.errorMessage) { feedViewModel.invalidateData() }
                }

                is FeedState.Loaded -> {
                    PrefetchLoadedFeedMedia(state, listState, accountViewModel)
                    FeedLoadedWithPinnedNotes(
                        pinnedFeedState = pinnedFeedState as? FeedState.Loaded,
                        loaded = state,
                        listState = listState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                is FeedState.Loading -> {
                    LoadingFeed()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedLoadedWithPinnedNotes(
    pinnedFeedState: FeedState.Loaded?,
    loaded: FeedState.Loaded?,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pinnedItems =
        pinnedFeedState?.let {
            val state by it.feed.collectAsStateWithLifecycle()
            state
        }

    val feedItems =
        loaded?.let {
            val state by it.feed.collectAsStateWithLifecycle()
            state
        }

    StickToTopOnPrepend(
        listState,
        pinnedItems?.list?.firstOrNull()?.idHex ?: feedItems?.list?.firstOrNull()?.idHex,
    )

    LazyColumn(
        contentPadding = FeedPadding,
        state = listState,
    ) {
        if (pinnedItems != null && pinnedItems.list.isNotEmpty()) {
            items(
                pinnedItems.list,
                key = { "pinned-${it.idHex}" },
                contentType = { it.event?.kind ?: -1 },
            ) { item ->
                Column(Modifier.fillMaxWidth().animateItem()) {
                    NoteCompose(
                        item,
                        modifier = Modifier.fillMaxWidth(),
                        routeForLastRead = null,
                        isBoostedNote = false,
                        isHiddenFeed = pinnedItems.showHidden,
                        isPinned = true,
                        quotesLeft = 3,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
                HorizontalDivider(thickness = DividerThickness)
            }
        }

        if (feedItems != null) {
            itemsIndexed(
                feedItems.list,
                key = { _, item -> item.idHex },
                contentType = { _, item -> item.event?.kind ?: -1 },
            ) { _, item ->
                Row(Modifier.fillMaxWidth().animateItem()) {
                    NoteCompose(
                        item,
                        modifier = Modifier.fillMaxWidth(),
                        routeForLastRead = null,
                        isBoostedNote = false,
                        isHiddenFeed = feedItems.showHidden,
                        quotesLeft = 3,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}
