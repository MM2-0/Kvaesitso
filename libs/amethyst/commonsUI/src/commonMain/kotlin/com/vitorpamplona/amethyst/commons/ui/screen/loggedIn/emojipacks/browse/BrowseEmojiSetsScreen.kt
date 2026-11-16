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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.browse

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.EmojiPackCard
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.EmojiPackInListToggle
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.EmojiPackSheet
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.StickToTopOnPrepend
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverLazyStaggeredGridState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.browse.datasource.BrowseEmojiSetsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis

@Composable
fun BrowseEmojiSetsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    BrowseEmojiSetsScreen(
        feedContentState = accountViewModel.feedStates.browseEmojiSetsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun BrowseEmojiSetsScreen(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedContentState)
    WatchAccountForBrowseEmojiSetsScreen(feedContentState, accountViewModel)
    BrowseEmojiSetsFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            BrowseEmojiSetsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.BrowseEmojiSets, nav, accountViewModel) { route ->
                if (route == Route.BrowseEmojiSets) {
                    feedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feedContentState, true) {
            val gridState = rememberForeverLazyStaggeredGridState(ScrollStateKeys.BROWSE_EMOJI_SETS_SCREEN)
            WatchScrollToTop(feedContentState, gridState)
            RenderBrowseEmojiSetsGrid(
                feedContentState = feedContentState,
                gridState = gridState,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RenderBrowseEmojiSetsGrid(
    feedContentState: FeedContentState,
    gridState: LazyStaggeredGridState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty(feedContentState::invalidateData)
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage, feedContentState::invalidateData)
            }

            is FeedState.Loaded -> {
                BrowseEmojiSetsGridLoaded(
                    loaded = state,
                    gridState = gridState,
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

@Composable
private fun BrowseEmojiSetsGridLoaded(
    loaded: FeedState.Loaded,
    gridState: LazyStaggeredGridState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    StickToTopOnPrepend(gridState, items.list.firstOrNull()?.idHex)

    var peek by remember { mutableStateOf<Note?>(null) }

    // Staggered: a card is as tall as its pack needs, so a 2-emoji pack and a 175-emoji pack stop
    // being the same box, and the columns interleave instead of lining up in rows.
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberFeedContentPadding(PaddingValues(12.dp)),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items.list,
            key = { note -> note.idHex },
        ) { note ->
            BrowsedEmojiPackCard(
                note = note,
                accountViewModel = accountViewModel,
                modifier = Modifier.animateItem(),
                onClick = { nav.nav(Route.Note(note.idHex)) },
                onLongClick = { peek = note },
            )
        }
    }

    peek?.let { note ->
        EmojiPackSheet(note, accountViewModel) { peek = null }
    }
}

@Composable
private fun BrowsedEmojiPackCard(
    note: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val event = note.event as? EmojiPackEvent ?: return

    val title =
        remember(event) {
            event.titleOrName()?.takeIf { it.isNotBlank() } ?: event.dTag()
        }
    val emojiUrls =
        remember(event) {
            event.taggedEmojis().map { it.url }
        }
    val coverImage = remember(event) { event.image() }
    val author = remember(note) { note.author?.toBestDisplayName() }

    EmojiPackCard(
        title = title,
        emojiUrls = emojiUrls,
        coverImage = coverImage,
        author = author,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        action = { EmojiPackInListToggle(note, accountViewModel) },
    )
}

@Composable
fun WatchAccountForBrowseEmojiSetsScreen(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveBrowseEmojiSetsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        feedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
