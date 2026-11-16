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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.closed_polls
import com.vitorpamplona.amethyst.commons.resources.open_polls
import com.vitorpamplona.amethyst.commons.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.polls.datasource.PollsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

@Composable
fun PollsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    PollsScreen(
        openPollsFeedContentState = accountViewModel.feedStates.openPollsFeed,
        closedPollsFeedContentState = accountViewModel.feedStates.closedPollsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun PollsScreen(
    openPollsFeedContentState: FeedContentState,
    closedPollsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(openPollsFeedContentState)
    WatchLifecycleAndUpdateModel(closedPollsFeedContentState)
    WatchAccountForPollsScreen(openPollsFeedContentState, closedPollsFeedContentState, accountViewModel)
    PollsFilterAssemblerSubscription(accountViewModel)

    AssemblePollsTabs(openPollsFeedContentState, closedPollsFeedContentState) { pagerState, tabItems ->
        PollsPages(pagerState, tabItems, accountViewModel, nav)
    }
}

@Composable
private fun AssemblePollsTabs(
    openPollsFeedContentState: FeedContentState,
    closedPollsFeedContentState: FeedContentState,
    inner: @Composable (PagerState, ImmutableList<PollsTabItem>) -> Unit,
) {
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.POLLS_SCREEN) { 2 }

    val tabs by
        remember(openPollsFeedContentState, closedPollsFeedContentState) {
            mutableStateOf(
                listOf(
                    PollsTabItem(
                        resource = Res.string.open_polls,
                        feedState = openPollsFeedContentState,
                        routeForLastRead = "PollsOpenFeed",
                        scrollStateKey = ScrollStateKeys.POLLS_OPEN,
                    ),
                    PollsTabItem(
                        resource = Res.string.closed_polls,
                        feedState = closedPollsFeedContentState,
                        routeForLastRead = "PollsClosedFeed",
                        scrollStateKey = ScrollStateKeys.POLLS_CLOSED,
                    ),
                ).toImmutableList(),
            )
        }

    inner(pagerState, tabs)
}

@Composable
private fun PollsPages(
    pagerState: PagerState,
    tabs: ImmutableList<PollsTabItem>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                PollsTopBar(accountViewModel, nav)
                SecondaryTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = TabRowHeight,
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    val coroutineScope = rememberCoroutineScope()
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            text = { Text(text = stringRes(tab.resource)) },
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                }
            }
        },
        bottomBar = {
            AppBottomBar(Route.Polls, nav, accountViewModel) { route ->
                if (route == Route.Polls) {
                    tabs[pagerState.currentPage].feedState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewPollButton(nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
        ) { page ->
            RefresheableBox(tabs[page].feedState, true) {
                SaveableFeedContentState(tabs[page].feedState, scrollStateKey = tabs[page].scrollStateKey) { listState ->
                    RenderFeedContentState(
                        feedContentState = tabs[page].feedState,
                        accountViewModel = accountViewModel,
                        listState = listState,
                        nav = nav,
                        routeForLastRead = tabs[page].routeForLastRead,
                    )
                }
            }
        }
    }
}

@Composable
fun WatchAccountForPollsScreen(
    openPollsFeedContentState: FeedContentState,
    closedPollsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.livePollsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        openPollsFeedContentState.checkKeysInvalidateDataAndSendToTop()
        closedPollsFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Immutable
class PollsTabItem(
    val resource: StringResource,
    val feedState: FeedContentState,
    val routeForLastRead: String,
    val scrollStateKey: String,
)
