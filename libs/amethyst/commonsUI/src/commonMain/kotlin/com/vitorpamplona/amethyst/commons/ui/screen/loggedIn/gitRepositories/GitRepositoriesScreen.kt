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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_dashboard_tab
import com.vitorpamplona.amethyst.commons.resources.git_explore_tab
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.FeedContentMaxWidth
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories.dashboard.GitDashboard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories.datasource.GitRepositoriesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun GitRepositoriesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GitRepositoriesScreen(
        gitRepositoriesFeedContentState = accountViewModel.feedStates.gitRepositoriesFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * The repositories of the selected list. Searching them is the top bar's search button, which
 * opens the full search already narrowed to `kind:git`; the floating button announces a new one.
 */
@Composable
fun GitRepositoriesScreen(
    gitRepositoriesFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Dashboard first: what needs the reader. Explore keeps the list-driven feed of repositories,
    // and only loads it while it is the tab on screen.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab == 1) {
        WatchLifecycleAndUpdateModel(gitRepositoriesFeedContentState)
        WatchAccountForGitRepositoriesScreen(gitRepositoriesFeedContentState = gitRepositoriesFeedContentState, accountViewModel = accountViewModel)
        GitRepositoriesFilterAssemblerSubscription(accountViewModel)
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                GitRepositoriesTopBar(
                    accountViewModel = accountViewModel,
                    nav = nav,
                    showListPicker = tab == 1,
                )
                SecondaryTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = TabRowHeight,
                    selectedTabIndex = tab,
                ) {
                    Tab(selected = tab == 0, text = { Text(stringRes(Res.string.git_dashboard_tab)) }, onClick = { tab = 0 })
                    Tab(selected = tab == 1, text = { Text(stringRes(Res.string.git_explore_tab)) }, onClick = { tab = 1 })
                }
            }
        },
        bottomBar = {
            AppBottomBar(Route.GitRepositories, nav, accountViewModel) { route ->
                if (route == Route.GitRepositories) {
                    if (tab == 1) gitRepositoriesFeedContentState.sendToTop() else tab = 0
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        // The dashboard has its own "+ New" beside your repositories.
        floatingButton =
            if (tab == 1) {
                { FabBottomBarPadded(nav) { NewGitRepositoryButton(nav) } }
            } else {
                null
            },
        accountViewModel = accountViewModel,
    ) {
        if (tab == 0) {
            GitDashboard(accountViewModel, nav, onExplore = { tab = 1 })
            return@DisappearingScaffold
        }
        // The route is uncapped for the dashboard's columns; the list keeps the reading width.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = FeedContentMaxWidth)) {
                RefresheableBox(gitRepositoriesFeedContentState, true) {
                    SaveableFeedContentState(gitRepositoriesFeedContentState, scrollStateKey = ScrollStateKeys.GIT_REPOSITORIES_SCREEN) { listState ->
                        RenderFeedContentState(
                            feedContentState = gitRepositoriesFeedContentState,
                            accountViewModel = accountViewModel,
                            listState = listState,
                            nav = nav,
                            routeForLastRead = "GitRepositoriesFeed",
                            onLoaded = { GitRepositoryExploreList(it, listState, accountViewModel, nav) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchAccountForGitRepositoriesScreen(
    gitRepositoriesFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveGitRepositoriesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        gitRepositoriesFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
