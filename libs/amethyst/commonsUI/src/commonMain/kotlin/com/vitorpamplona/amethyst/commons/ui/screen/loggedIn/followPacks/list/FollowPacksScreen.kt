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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.followPacks.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.followPacks.list.datasource.FollowPacksFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun FollowPacksScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    FollowPacksScreen(
        followPacksFeedContentState = accountViewModel.feedStates.followPacksFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun FollowPacksScreen(
    followPacksFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(followPacksFeedContentState)
    WatchAccountForFollowPacksScreen(feedState = followPacksFeedContentState, accountViewModel = accountViewModel)
    FollowPacksFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            FollowPacksTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.FollowPacks, nav, accountViewModel) { route ->
                if (route == Route.FollowPacks) {
                    followPacksFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(followPacksFeedContentState, true) {
            SaveableFeedContentState(followPacksFeedContentState, scrollStateKey = ScrollStateKeys.FOLLOW_PACKS_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = followPacksFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "FollowPacksFeed",
                    onLoaded = { loaded ->
                        FollowPacksFeedLoaded(
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
fun WatchAccountForFollowPacksScreen(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveFollowPacksFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        feedState.checkKeysInvalidateDataAndSendToTop()
    }
}
