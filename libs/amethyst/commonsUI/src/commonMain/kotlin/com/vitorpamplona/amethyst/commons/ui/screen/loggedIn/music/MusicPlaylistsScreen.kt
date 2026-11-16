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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.music.ui.NewMusicPlaylistFab
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.datasource.MusicPlaylistsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Composable
fun MusicPlaylistsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    MusicPlaylistsScreen(
        playlistsFeedContentState = accountViewModel.feedStates.musicPlaylistsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun MusicPlaylistsScreen(
    playlistsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(playlistsFeedContentState)
    WatchAccountForMusicPlaylistsScreen(
        playlistsFeedState = playlistsFeedContentState,
        accountViewModel = accountViewModel,
    )
    // Dedicated subscription keyed on the *playlists* follow list (the top-bar spinner
    // writes to a different setting than the tracks screen) and asking for only kind
    // 34139, so this screen's REQ is isolated from the tracks screen's REQ. Each
    // track referenced by a playlist is pulled separately by its own PlaylistTrackRow
    // via observeNoteEvent.
    MusicPlaylistsFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            MusicPlaylistsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.MusicPlaylists, nav, accountViewModel) { route ->
                if (route == Route.MusicPlaylists) {
                    playlistsFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewMusicPlaylistFab(nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(playlistsFeedContentState, true) {
            SaveableFeedContentState(playlistsFeedContentState, scrollStateKey = ScrollStateKeys.MUSIC_PLAYLISTS_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = playlistsFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "MusicPlaylistsFeed",
                    onLoaded = { loaded ->
                        MusicPlaylistsFeedLoaded(
                            loaded = loaded,
                            listState = listState,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun WatchAccountForMusicPlaylistsScreen(
    playlistsFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveMusicPlaylistsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        playlistsFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Preview
@Composable
private fun MusicPlaylistsScreenPreview() {
    val accountViewModel = mockAccountViewModel()
    ThemeComparisonColumn {
        MusicPlaylistsScreen(
            playlistsFeedContentState = accountViewModel.feedStates.musicPlaylistsFeed,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}
