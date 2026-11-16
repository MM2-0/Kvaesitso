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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.navigation.DiscoverTab
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip23LongContent.ui.NewArticleButton
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.NewProductButton
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.discover_chat
import com.vitorpamplona.amethyst.commons.resources.discover_community_v2
import com.vitorpamplona.amethyst.commons.resources.discover_content_v2
import com.vitorpamplona.amethyst.commons.resources.discover_follows
import com.vitorpamplona.amethyst.commons.resources.discover_live_v2
import com.vitorpamplona.amethyst.commons.resources.discover_marketplace
import com.vitorpamplona.amethyst.commons.resources.discover_reads
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.PrefetchLoadedFeedMedia
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableGridFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.ChannelCardCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.datasource.DiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.TabItem
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(
    initialTab: DiscoverTab?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DiscoverScreen(
        initialTab = initialTab,
        discoveryFollowSetsFeedContentState = accountViewModel.feedStates.discoverFollowSets,
        discoveryReadsFeedContentState = accountViewModel.feedStates.discoverReads,
        discoveryContentNIP89FeedContentState = accountViewModel.feedStates.discoverDVMs,
        discoveryMarketplaceFeedContentState = accountViewModel.feedStates.discoverMarketplace,
        discoveryLiveFeedContentState = accountViewModel.feedStates.discoverLive,
        discoveryCommunityFeedContentState = accountViewModel.feedStates.discoverCommunities,
        discoveryChatFeedContentState = accountViewModel.feedStates.discoverPublicChats,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    initialTab: DiscoverTab?,
    discoveryFollowSetsFeedContentState: FeedContentState,
    discoveryReadsFeedContentState: FeedContentState,
    discoveryContentNIP89FeedContentState: FeedContentState,
    discoveryMarketplaceFeedContentState: FeedContentState,
    discoveryLiveFeedContentState: FeedContentState,
    discoveryCommunityFeedContentState: FeedContentState,
    discoveryChatFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedTabs by
        remember(accountViewModel) {
            mutableStateOf(
                listOf(
                    TabItem(
                        Res.string.discover_follows,
                        discoveryFollowSetsFeedContentState,
                        "DiscoverFollowSets",
                        ScrollStateKeys.DISCOVER_FOLLOWS,
                        StarterPackEvent.KIND,
                    ),
                    TabItem(
                        Res.string.discover_reads,
                        discoveryReadsFeedContentState,
                        "DiscoverReads",
                        ScrollStateKeys.DISCOVER_READS,
                        LongFormContentEvent.KIND,
                    ),
                    TabItem(
                        Res.string.discover_content_v2,
                        discoveryContentNIP89FeedContentState,
                        "DiscoverDiscoverContent",
                        ScrollStateKeys.DISCOVER_CONTENT,
                        AppDefinitionEvent.KIND,
                    ),
                    TabItem(
                        Res.string.discover_live_v2,
                        discoveryLiveFeedContentState,
                        "DiscoverLive",
                        ScrollStateKeys.DISCOVER_LIVE,
                        LiveActivitiesEvent.KIND,
                    ),
                    TabItem(
                        Res.string.discover_community_v2,
                        discoveryCommunityFeedContentState,
                        "DiscoverCommunity",
                        ScrollStateKeys.DISCOVER_COMMUNITY,
                        CommunityDefinitionEvent.KIND,
                    ),
                    TabItem(
                        Res.string.discover_marketplace,
                        discoveryMarketplaceFeedContentState,
                        "DiscoverMarketplace",
                        ScrollStateKeys.DISCOVER_MARKETPLACE,
                        ClassifiedsEvent.KIND,
                        useGridLayout = true,
                    ),
                    TabItem(
                        Res.string.discover_chat,
                        discoveryChatFeedContentState,
                        "DiscoverChats",
                        ScrollStateKeys.DISCOVER_CHATS,
                        ChannelCreateEvent.KIND,
                    ),
                ).toImmutableList(),
            )
        }

    val pagerState = rememberForeverPagerState(key = PagerStateKeys.DISCOVER_SCREEN) { feedTabs.size }

    LaunchedEffect(initialTab) {
        initialTab?.let { pagerState.scrollToPage(it.toTabIndex()) }
    }

    WatchAccountForDiscoveryScreen(
        discoveryFollowSetsFeedContentState = discoveryFollowSetsFeedContentState,
        discoveryReadsFeedContentState = discoveryReadsFeedContentState,
        discoveryContentNIP89FeedContentState = discoveryContentNIP89FeedContentState,
        discoveryMarketplaceFeedContentState = discoveryMarketplaceFeedContentState,
        discoveryLiveFeedContentState = discoveryLiveFeedContentState,
        discoveryCommunityFeedContentState = discoveryCommunityFeedContentState,
        discoveryChatFeedContentState = discoveryChatFeedContentState,
        accountViewModel = accountViewModel,
    )

    WatchLifecycleAndUpdateModel(discoveryFollowSetsFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryReadsFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryContentNIP89FeedContentState)
    WatchLifecycleAndUpdateModel(discoveryMarketplaceFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryLiveFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryCommunityFeedContentState)
    WatchLifecycleAndUpdateModel(discoveryChatFeedContentState)

    DiscoveryFilterAssemblerSubscription(accountViewModel.dataSources().discovery, accountViewModel)

    DiscoverPages(pagerState, feedTabs, accountViewModel, nav)
}

@Composable
private fun DiscoverPages(
    pagerState: PagerState,
    feedTabs: ImmutableList<TabItem>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                DiscoveryTopBar(accountViewModel, nav)
                SecondaryScrollableTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    selectedTabIndex = pagerState.currentPage,
                    modifier = TabRowHeight,
                    edgePadding = 8.dp,
                ) {
                    val coroutineScope = rememberCoroutineScope()

                    feedTabs.forEachIndexed { index, tab ->
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
            AppBottomBar(Route.Discover(), nav, accountViewModel) { route ->
                if (route is Route.Discover) {
                    val currentPage = pagerState.currentPage
                    if (currentPage >= 0 && currentPage < feedTabs.size) {
                        feedTabs[currentPage].feedState.sendToTop()
                    }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            val currentPage = pagerState.currentPage
            if (currentPage >= 0 && currentPage < feedTabs.size) {
                FabBottomBarPadded(nav) {
                    if (feedTabs[currentPage].resource == Res.string.discover_marketplace) {
                        NewProductButton(nav)
                    }

                    if (feedTabs[currentPage].resource == Res.string.discover_reads) {
                        NewArticleButton(nav)
                    }
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(state = pagerState) { page ->
            if (page >= 0 && page < feedTabs.size) {
                val tab = feedTabs[page]
                RefresheableBox(
                    onRefresh = {
                        tab.feedState.invalidateData()
                        // The DVM tab's freshness gate lives or dies with beat delivery, and
                        // invalidateData only re-reads the cache — re-issue the discovery REQs
                        // (31990 + both heartbeat streams) with fresh rolling windows so refresh
                        // actually fetches.
                        if (tab.feedState == accountViewModel.feedStates.discoverDVMs) {
                            accountViewModel.dataSources().discovery.invalidateFilters()
                        }
                    },
                ) {
                    if (tab.useGridLayout) {
                        SaveableGridFeedContentState(tab.feedState, scrollStateKey = tab.scrollStateKey) { listState ->
                            RenderDiscoverFeed(
                                feedContentState = tab.feedState,
                                routeForLastRead = tab.routeForLastRead,
                                forceEventKind = tab.forceEventKind,
                                listState = listState,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                    } else {
                        SaveableFeedContentState(tab.feedState, scrollStateKey = tab.scrollStateKey) { listState ->
                            RenderDiscoverFeed(
                                feedContentState = tab.feedState,
                                routeForLastRead = tab.routeForLastRead,
                                forceEventKind = tab.forceEventKind,
                                listState = listState,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDiscoverFeed(
    feedContentState: FeedContentState,
    routeForLastRead: String?,
    forceEventKind: Int?,
    listState: LazyGridState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderDiscoverFeed",
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty(feedContentState::invalidateData)
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage, feedContentState::invalidateData)
            }

            is FeedState.Loaded -> {
                DiscoverFeedColumnsLoaded(
                    state,
                    routeForLastRead,
                    listState,
                    forceEventKind,
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
private fun RenderDiscoverFeed(
    feedContentState: FeedContentState,
    routeForLastRead: String?,
    forceEventKind: Int?,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderDiscoverFeed",
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                FeedEmpty(feedContentState::invalidateData)
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage, feedContentState::invalidateData)
            }

            is FeedState.Loaded -> {
                DiscoverFeedLoaded(
                    state,
                    routeForLastRead,
                    listState,
                    forceEventKind,
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
fun WatchAccountForDiscoveryScreen(
    discoveryFollowSetsFeedContentState: FeedContentState,
    discoveryReadsFeedContentState: FeedContentState,
    discoveryContentNIP89FeedContentState: FeedContentState,
    discoveryMarketplaceFeedContentState: FeedContentState,
    discoveryLiveFeedContentState: FeedContentState,
    discoveryCommunityFeedContentState: FeedContentState,
    discoveryChatFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveDiscoveryFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState) {
        discoveryFollowSetsFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryReadsFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryContentNIP89FeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryMarketplaceFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryLiveFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryCommunityFeedContentState.checkKeysInvalidateDataAndSendToTop()
        discoveryChatFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverFeedLoaded(
    loaded: FeedState.Loaded,
    routeForLastRead: String?,
    listState: LazyListState,
    forceEventKind: Int?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    PrefetchLoadedFeedMedia(loaded, listState, accountViewModel)

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                ChannelCardCompose(
                    baseNote = item,
                    routeForLastRead = routeForLastRead,
                    modifier = Modifier.fillMaxWidth(),
                    forceEventKind = forceEventKind,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverFeedColumnsLoaded(
    loaded: FeedState.Loaded,
    routeForLastRead: String?,
    listState: LazyGridState,
    forceEventKind: Int?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    PrefetchLoadedFeedMedia(loaded, listState, accountViewModel)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                ChannelCardCompose(
                    baseNote = item,
                    routeForLastRead = routeForLastRead,
                    modifier = Modifier,
                    forceEventKind = forceEventKind,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

// Exhaustive on DiscoverTab so adding a new enum value forces an update of feedTabs above.
private fun DiscoverTab.toTabIndex(): Int =
    when (this) {
        DiscoverTab.FOLLOWS -> 0
        DiscoverTab.READS -> 1
        DiscoverTab.ALGOS -> 2
        DiscoverTab.LIVE -> 3
        DiscoverTab.COMMUNITY -> 4
        DiscoverTab.MARKETPLACE -> 5
        DiscoverTab.CHATS -> 6
    }
