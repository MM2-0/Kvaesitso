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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.GeocacheTab
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipCCGeocaching.ui.NewGeocacheButton
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_tab_finds
import com.vitorpamplona.amethyst.commons.resources.geocache_tab_hunts
import com.vitorpamplona.amethyst.commons.resources.geocache_tab_map
import com.vitorpamplona.amethyst.commons.resources.geocache_tab_mine
import com.vitorpamplona.amethyst.commons.resources.geocache_tab_nearby
import com.vitorpamplona.amethyst.commons.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.GeocacheMapTab
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.datasource.GeocachesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The geocaching hub: five views over one subscription.
 *
 * Nearby, Map, Hunts, Finds and Mine are tabs rather than destinations because they are the same
 * data asked four different questions, and because Back should return a player to whichever list
 * they came from rather than unwinding a stack of screens. The single
 * [GeocachesFilterAssemblerSubscription] covers all of them — `GeocacheFeedKinds` fetches
 * listings, found logs and curation lists together — so switching tabs costs no round trips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeocachesScreen(
    initialTab: GeocacheTab?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val nearby = accountViewModel.feedStates.geocachesFeed
    val hunts = accountViewModel.feedStates.geocacheHuntsFeed
    val finds = accountViewModel.feedStates.geocacheFindsFeed
    val mine = accountViewModel.feedStates.geocacheMineFeed

    WatchLifecycleAndUpdateModel(nearby)
    WatchAccountForGeocachesScreen(nearby, accountViewModel)
    GeocachesFilterAssemblerSubscription(accountViewModel)

    // No Map tab where the platform has no map, rather than a tab that opens onto an empty page.
    val hasMap = LocalAppPlatform.current.hasGeocacheMap
    val tabs = remember(hasMap) { if (hasMap) GeocacheTab.entries.toList() else GeocacheTab.entries.filter { it != GeocacheTab.MAP } }
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.GEOCACHES_SCREEN) { tabs.size }

    LaunchedEffect(initialTab) {
        initialTab?.let { tab -> tabs.indexOf(tab).takeIf { it >= 0 }?.let { pagerState.scrollToPage(it) } }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        // The tab row belongs to the top bar, not to the content. DisappearingScaffold draws its
        // content from the top of the window, behind the bar, so a tab row placed first in the
        // content column lands under the status bar: invisible and untappable, which left Map,
        // Hunts, Finds and Mine unreachable. DiscoveryScreen stacks its tabs the same way.
        topBar = {
            Column {
                GeocachesTopBar(accountViewModel, nav)
                GeocacheTabRow(tabs, pagerState)
            }
        },
        bottomBar = {
            // Geocaches and Hunts are two separate pinnable tabs of this one screen, so match the
            // exact route: a class check highlighted the wrong one and turned a tap on the other
            // into a scroll-to-top instead of a tab switch.
            val selfRoute = Route.Geocaches(initialTab)
            AppBottomBar(selfRoute, nav, accountViewModel) { route ->
                if (route == selfRoute) {
                    nearby.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            // No "hide a cache" button on Finds or Hunts: neither is a place where a new cache
            // belongs, and a FAB that changes meaning per tab is worse than one that is absent.
            val currentTab = tabs.getOrNull(pagerState.currentPage)
            if (currentTab != GeocacheTab.FINDS && currentTab != GeocacheTab.HUNTS) {
                FabBottomBarPadded(nav) {
                    NewGeocacheButton(nav)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = false) { page ->
                when (tabs[page]) {
                    GeocacheTab.NEARBY ->
                        // The screen already watches `nearby` (the Map tab reads it too), so don't watch it twice.
                        GeocacheListTab(nearby, ScrollStateKeys.GEOCACHES_NEARBY, GeocacheListKind.NEARBY, accountViewModel, nav, watchLifecycle = false)
                    GeocacheTab.MAP ->
                        GeocacheMapTab(nearby, accountViewModel, nav)
                    GeocacheTab.HUNTS ->
                        GeocacheListTab(hunts, ScrollStateKeys.GEOCACHES_HUNTS, GeocacheListKind.HUNTS, accountViewModel, nav)
                    GeocacheTab.FINDS ->
                        GeocacheListTab(finds, ScrollStateKeys.GEOCACHES_FINDS, GeocacheListKind.FINDS, accountViewModel, nav)
                    GeocacheTab.MINE ->
                        GeocacheListTab(mine, ScrollStateKeys.GEOCACHES_MINE, GeocacheListKind.MINE, accountViewModel, nav)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeocacheTabRow(
    tabs: List<GeocacheTab>,
    pagerState: PagerState,
) {
    val scope = rememberCoroutineScope()

    SecondaryScrollableTabRow(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        selectedTabIndex = pagerState.currentPage,
        modifier = TabRowHeight,
        edgePadding = 8.dp,
        divider = {},
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                text = { Text(stringResource(tab.labelRes())) },
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}

private fun GeocacheTab.labelRes() =
    when (this) {
        GeocacheTab.NEARBY -> Res.string.geocache_tab_nearby
        GeocacheTab.MAP -> Res.string.geocache_tab_map
        GeocacheTab.HUNTS -> Res.string.geocache_tab_hunts
        GeocacheTab.FINDS -> Res.string.geocache_tab_finds
        GeocacheTab.MINE -> Res.string.geocache_tab_mine
    }

@Composable
private fun GeocacheListTab(
    feedContentState: FeedContentState,
    scrollStateKey: String,
    kind: GeocacheListKind,
    accountViewModel: AccountViewModel,
    nav: INav,
    watchLifecycle: Boolean = true,
) {
    if (watchLifecycle) WatchLifecycleAndUpdateModel(feedContentState)

    RefresheableBox(feedContentState, true) {
        SaveableFeedContentState(feedContentState, scrollStateKey = scrollStateKey) { listState ->
            RenderGeocacheFeed(
                feedContentState = feedContentState,
                listState = listState,
                kind = kind,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun WatchAccountForGeocachesScreen(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveGeocachesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        feedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
