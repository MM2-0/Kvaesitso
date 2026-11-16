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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.datasource.PodcastEpisodesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Composable
fun PodcastEpisodesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    PodcastEpisodesScreen(
        episodesFeedContentState = accountViewModel.feedStates.podcastEpisodesFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun PodcastEpisodesScreen(
    episodesFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(episodesFeedContentState)
    WatchAccountForPodcastEpisodesScreen(
        episodesFeedState = episodesFeedContentState,
        accountViewModel = accountViewModel,
    )
    // Drives the kind:54 REQ for the user's selected follow list — without it the screen
    // only shows episodes already in cache from some other path (e.g. a nostr: link).
    PodcastEpisodesFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            PodcastEpisodesTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.PodcastEpisodes, nav, accountViewModel) { route ->
                if (route == Route.PodcastEpisodes) {
                    episodesFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(episodesFeedContentState, true) {
            SaveableFeedContentState(episodesFeedContentState, scrollStateKey = ScrollStateKeys.PODCAST_EPISODES_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = episodesFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "PodcastEpisodesFeed",
                    onLoaded = { loaded ->
                        PodcastEpisodesFeedLoaded(
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
fun WatchAccountForPodcastEpisodesScreen(
    episodesFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.livePodcastEpisodesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        episodesFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Preview
@Composable
private fun PodcastEpisodesScreenPreview() {
    val accountViewModel = mockAccountViewModel()
    ThemeComparisonColumn {
        PodcastEpisodesScreen(
            episodesFeedContentState = accountViewModel.feedStates.podcastEpisodesFeed,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}
