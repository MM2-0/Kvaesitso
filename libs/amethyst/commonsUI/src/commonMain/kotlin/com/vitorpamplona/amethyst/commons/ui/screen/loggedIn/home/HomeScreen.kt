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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.ChannelFeedContentState
import com.vitorpamplona.amethyst.commons.feeds.ChannelFeedState
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.conversations
import com.vitorpamplona.amethyst.commons.resources.feed_is_empty
import com.vitorpamplona.amethyst.commons.resources.home_tab_everything
import com.vitorpamplona.amethyst.commons.resources.new_threads
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.zonedDrawerSwipeIfModal
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.BackupConflictCards
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geohash.NewGeoPostButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.hashtag.NewHashtagPostButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.datasource.HomeFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.live.RenderEphemeralBubble
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.live.RenderGeohashBubble
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.live.RenderLiveActivityBubble
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.keyBackup.BackupKeysNudge
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.keyBackup.rememberShowBackupKeysNudge
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wotNudge.WebOfTrustNudge
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wotNudge.rememberWebOfTrustNudge
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

@Composable
fun HomeScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    HomeScreen(
        liveFeedState = accountViewModel.feedStates.homeLive,
        newThreadsFeedState = accountViewModel.feedStates.homeNewThreads,
        repliesFeedState = accountViewModel.feedStates.homeReplies,
        everythingFeedState = accountViewModel.feedStates.homeEverything,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    liveFeedState: ChannelFeedContentState,
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    everythingFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAccountForHomeScreen(liveFeedState, newThreadsFeedState, repliesFeedState, everythingFeedState, accountViewModel)

    WatchLifecycleAndUpdateModel(liveFeedState)
    WatchLifecycleAndUpdateModel(newThreadsFeedState)
    WatchLifecycleAndUpdateModel(repliesFeedState)
    WatchLifecycleAndUpdateModel(everythingFeedState)

    HomeFilterAssemblerSubscription(accountViewModel)

    AssembleHomeTabs(newThreadsFeedState, repliesFeedState, everythingFeedState, liveFeedState, accountViewModel) { pagerState, tabItems ->
        HomePages(pagerState, tabItems, accountViewModel, nav)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssembleHomeTabs(
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    everythingFeedState: FeedContentState,
    liveFeedState: ChannelFeedContentState,
    accountViewModel: AccountViewModel,
    inner: @Composable (PagerState, ImmutableList<TabItem>) -> Unit,
) {
    val showNewThreads by accountViewModel.settings.uiSettingsFlow.showHomeNewThreadsTab
        .collectAsStateWithLifecycle()
    val showConversations by accountViewModel.settings.uiSettingsFlow.showHomeConversationsTab
        .collectAsStateWithLifecycle()
    val showEverything by accountViewModel.settings.uiSettingsFlow.showHomeEverythingTab
        .collectAsStateWithLifecycle()

    val tabs by
        remember(newThreadsFeedState, repliesFeedState, everythingFeedState, showNewThreads, showConversations, showEverything) {
            mutableStateOf(
                buildList {
                    if (showNewThreads) {
                        add(
                            TabItem(
                                resource = Res.string.new_threads,
                                feedState = newThreadsFeedState,
                                routeForLastRead = "HomeFollows",
                                scrollStateKey = ScrollStateKeys.HOME_FOLLOWS,
                                liveSection = liveFeedState,
                            ),
                        )
                    }
                    if (showConversations) {
                        add(
                            TabItem(
                                resource = Res.string.conversations,
                                feedState = repliesFeedState,
                                routeForLastRead = "HomeFollowsReplies",
                                scrollStateKey = ScrollStateKeys.HOME_REPLIES,
                                liveSection = liveFeedState,
                            ),
                        )
                    }
                    if (showEverything) {
                        add(
                            TabItem(
                                resource = Res.string.home_tab_everything,
                                feedState = everythingFeedState,
                                routeForLastRead = "HomeFollowsEverything",
                                scrollStateKey = ScrollStateKeys.HOME_EVERYTHING,
                                liveSection = liveFeedState,
                            ),
                        )
                    }
                    // Always render at least one tab so the screen doesn't go blank
                    // if the user disables every option.
                    if (isEmpty()) {
                        add(
                            TabItem(
                                resource = Res.string.new_threads,
                                feedState = newThreadsFeedState,
                                routeForLastRead = "HomeFollows",
                                scrollStateKey = ScrollStateKeys.HOME_FOLLOWS,
                                liveSection = liveFeedState,
                            ),
                        )
                    }
                }.toImmutableList(),
            )
        }

    val pagerState = rememberForeverPagerState(key = PagerStateKeys.HOME_SCREEN) { tabs.size }

    inner(pagerState, tabs)
}

@Composable
private fun HomePages(
    pagerState: PagerState,
    tabs: ImmutableList<TabItem>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                HomeTopBar(accountViewModel, nav)
                if (tabs.size > 1) {
                    // Clamp to a valid index: rememberForeverPagerState persists
                    // currentPage across tab-count changes, so toggling off a home
                    // tab can leave currentPage > tabs.lastIndex for a frame, which
                    // crashes Material3's TabIndicatorOffsetNode.
                    val selectedTab = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
                    SecondaryTabRow(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        modifier = TabRowHeight,
                        selectedTabIndex = selectedTab,
                    ) {
                        val coroutineScope = rememberCoroutineScope()
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                text = { Text(text = stringRes(tab.resource)) },
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomBar(Route.Home, nav, accountViewModel) { route ->
                if (route == Route.Home) {
                    tabs.getOrNull(pagerState.currentPage)?.feedState?.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                HomeScreenFloatingButton(accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) { paddingValues ->
        // Wrap pager + banner in a Box so the banner can float over the feed
        // (anchored top-center) instead of living in the topBar Column where
        // it would push the tabs down every time it appears or disappears.
        // The Box itself fills the full screen; inner LazyColumns pick up the
        // scaffold padding from LocalDisappearingScaffoldPadding via
        // rememberFeedContentPadding, so feed items still scroll behind the bars.
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = true,
                modifier = Modifier.zonedDrawerSwipeIfModal(pagerState, nav),
            ) { page ->
                HomeFeeds(
                    feedState = tabs[page].feedState,
                    routeForLastRead = tabs[page].routeForLastRead,
                    scrollStateKey = tabs[page].scrollStateKey,
                    liveSection = tabs[page].liveSection,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding()),
            ) {
                HomeAlgoFeedStatusBanner(
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun HomeScreenFloatingButton(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val list =
        accountViewModel.account.settings.defaultHomeFollowList
            .collectAsStateWithLifecycle()

    when (val filter = list.value) {
        TopFilter.AroundMe -> {
            val location by LocalAppServices.current.deviceLocation.geohashStateFlow
                .collectAsStateWithLifecycle()

            when (val myLocation = location) {
                is LocationResult.Success -> {
                    NewGeoPostButton(myLocation.geoHash.toString(), accountViewModel, nav)
                }

                is LocationResult.LackPermission -> { }

                is LocationResult.Loading -> { }
            }
        }

        is TopFilter.Geohash -> {
            NewGeoPostButton(filter.tag, accountViewModel, nav)
        }

        is TopFilter.Hashtag -> {
            NewHashtagPostButton(filter.tag, accountViewModel, nav)
        }

        is TopFilter.InterestSet -> {
            NewNoteButton(nav)
        }

        else -> {
            NewNoteButton(nav)
        }
    }
}

@Composable
fun HomeFeeds(
    feedState: FeedContentState,
    routeForLastRead: String?,
    enablePullRefresh: Boolean = true,
    scrollStateKey: String? = null,
    liveSection: ChannelFeedContentState? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val activeFilter by accountViewModel.account.settings.defaultHomeFollowList
        .collectAsStateWithLifecycle()
    val favoriteAlgoFeedAddresses by accountViewModel.account.favoriteAlgoFeedsList.flow
        .collectAsStateWithLifecycle()

    val onRefresh: () -> Unit = {
        feedState.invalidateData()
        // Swiping down on Home should also re-issue the kind-5300 request(s) so the
        // DVM(s) produce fresh feeds, not just re-render whatever's cached.
        when (val filter = activeFilter) {
            is TopFilter.FavoriteAlgoFeed -> accountViewModel.refreshFavoriteAlgoFeed(filter.address)
            is TopFilter.AllFavoriteAlgoFeeds -> favoriteAlgoFeedAddresses.forEach { accountViewModel.refreshFavoriteAlgoFeed(it) }
            else -> Unit
        }
    }

    RefresheableHomeBox(onRefresh, enablePullRefresh) {
        SaveableFeedContentState(feedState, scrollStateKey) { listState ->
            RenderFeedContentState(
                feedContentState = feedState,
                accountViewModel = accountViewModel,
                listState = listState,
                nav = nav,
                routeForLastRead = routeForLastRead,
                onLoaded = { FeedLoaded(it, listState, routeForLastRead, liveSection, accountViewModel, nav) },
                onEmpty = { WithHomeCards(accountViewModel, nav) { HomeFeedEmpty(onRefresh) } },
                onError = { WithHomeCards(accountViewModel, nav) { FeedError(it, onRefresh) } },
                onLoading = { WithHomeCards(accountViewModel, nav) { LoadingFeed() } },
            )
        }
    }
}

@Composable
private fun RefresheableHomeBox(
    onRefresh: () -> Unit,
    enablePullRefresh: Boolean,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    if (enablePullRefresh) {
        RefresheableBox(onRefresh = onRefresh, content = content)
    } else {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(),
            content = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    routeForLastRead: String?,
    liveSection: ChannelFeedContentState? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val backupConflicts by accountViewModel.account.settings.backupConflicts
        .collectAsStateWithLifecycle()

    // The list keeps its position anchored to the first visible post, so cards that appear
    // while the feed sits at the top would be inserted just above the screen. Only then (not
    // when the user has scrolled down) bring them into view.
    val hasConflicts = backupConflicts.isNotEmpty()
    val showBackupNudge = rememberShowBackupKeysNudge(accountViewModel)
    val webOfTrustNudge = rememberWebOfTrustNudge(accountViewModel)
    val hasNudges = showBackupNudge || webOfTrustNudge != null
    val atTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    val cardsAppearedAtTop = remember(hasConflicts, hasNudges) { (hasConflicts || hasNudges) && atTop }
    LaunchedEffect(cardsAppearedAtTop) {
        if (cardsAppearedAtTop) listState.scrollToItem(0)
    }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        // The cards are items only while they show, so the list's first item (and with it how
        // the scroll position anchors when new posts arrive) is unchanged the rest of the time.
        if (hasNudges) {
            item(key = "homeNudges", contentType = "homeNudges") {
                HomeNudges(showBackupNudge, webOfTrustNudge, accountViewModel, nav)
            }
        }
        if (hasConflicts) {
            item(key = "backupConflicts", contentType = "backupConflicts") {
                BackupConflictCards(backupConflicts, nav, onAccept = accountViewModel::acceptExternalBackupVersion)
            }
        }
        if (liveSection != null) {
            item {
                DisplayLiveBubbles(liveSection, accountViewModel, nav)
            }
        }
        itemsIndexed(items.list, key = { _, item -> item.idHex }, contentType = { _, item -> item.event?.kind ?: -1 }) { _, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateItem(),
            ) {
                NoteCompose(
                    item,
                    modifier = Modifier.fillMaxWidth(),
                    routeForLastRead = routeForLastRead,
                    isBoostedNote = false,
                    isHiddenFeed = items.showHidden,
                    quotesLeft = 3,
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

@Composable
fun DisplayLiveBubbles(
    liveSection: ChannelFeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by liveSection.feedContent.collectAsStateWithLifecycle()

    when (val state = feedState) {
        is ChannelFeedState.Empty -> {}
        is ChannelFeedState.FeedError -> {}
        is ChannelFeedState.Loaded -> DisplayLiveBubbles(state, accountViewModel, nav)
        is ChannelFeedState.Loading -> {}
    }
}

@Composable
fun DisplayLiveBubbles(
    liveFeed: ChannelFeedState.Loaded,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feed by liveFeed.feed.collectAsStateWithLifecycle()

    // Render the row and its trailing spacer only when there is something to show.
    // Emitting the spacer unconditionally reserved dead vertical space at the very
    // top of the feed whenever there were no live bubbles (the common case), which
    // read as an oversized gap between the top-bar divider and the first note.
    if (feed.list.isNotEmpty()) {
        LazyRow(HorzPadding, horizontalArrangement = spacedBy(Size5dp)) {
            itemsIndexed(feed.list, key = { _, item -> item.hashCode() }) { _, item ->
                when (item) {
                    is EphemeralChatChannel -> RenderEphemeralBubble(item, accountViewModel, nav)
                    is GeohashChatChannel -> RenderGeohashBubble(item, accountViewModel, nav)
                    is LiveActivitiesChannel -> RenderLiveActivityBubble(item, accountViewModel, nav)
                }
            }
        }
        Spacer(StdVertSpacer)
    }
}

@Preview
@Composable
fun HomeFeedEmptyPreview() {
    ThemeComparisonRow(
        toPreview = { HomeFeedEmpty {} },
    )
}

/** The cards Home shows before its first post: back up your keys, then the circle card. */
@Composable
private fun HomeNudges(
    showBackupNudge: Boolean,
    webOfTrustNudge: WebOfTrustViewModel?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column {
        if (showBackupNudge) BackupKeysNudge(accountViewModel, nav)
        if (webOfTrustNudge != null) WebOfTrustNudge(webOfTrustNudge, accountViewModel, nav)
    }
}

/**
 * The cards of [FeedLoaded]'s first items (nudges, backup conflicts) above a feed that has no
 * posts to show yet: empty, loading or failed, so it doesn't hide them. A new account's feed is
 * empty, and that is when the nudges matter most. They get at most half the screen and scroll
 * there if there are many.
 */
@Composable
private fun WithHomeCards(
    accountViewModel: AccountViewModel,
    nav: INav,
    content: @Composable () -> Unit,
) {
    val backupConflicts by accountViewModel.account.settings.backupConflicts
        .collectAsStateWithLifecycle()
    val showBackupNudge = rememberShowBackupKeysNudge(accountViewModel)
    val webOfTrustNudge = rememberWebOfTrustNudge(accountViewModel)
    if (backupConflicts.isEmpty() && !showBackupNudge && webOfTrustNudge == null) {
        content()
        return
    }
    val padding = rememberFeedContentPadding(FeedPadding)
    val layoutDirection = LocalLayoutDirection.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
            HomeNudges(showBackupNudge, webOfTrustNudge, accountViewModel, nav)
            if (backupConflicts.isNotEmpty()) {
                BackupConflictCards(backupConflicts, nav, onAccept = accountViewModel::acceptExternalBackupVersion)
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun HomeFeedEmpty(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringRes(Res.string.feed_is_empty))
        Spacer(modifier = StdVertSpacer)
        OutlinedButton(onClick = onRefresh) { Text(text = stringRes(Res.string.refresh)) }
    }
}

@Composable
fun WatchAccountForHomeScreen(
    liveFeedState: ChannelFeedContentState,
    newThreadsFeedState: FeedContentState,
    repliesFeedState: FeedContentState,
    everythingFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val homeFollowList by accountViewModel.account.liveHomeFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, homeFollowList) {
        newThreadsFeedState.checkKeysInvalidateDataAndSendToTop()
        repliesFeedState.checkKeysInvalidateDataAndSendToTop()
        everythingFeedState.checkKeysInvalidateDataAndSendToTop()
        liveFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Immutable
class TabItem(
    val resource: StringResource,
    val feedState: FeedContentState,
    val routeForLastRead: String,
    val scrollStateKey: String,
    val forceEventKind: Int? = null,
    val useGridLayout: Boolean = false,
    val liveSection: ChannelFeedContentState? = null,
)
