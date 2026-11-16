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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.NOTIFICATION_LAST_READ_KEY
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.notifications.CardFeedContentState
import com.vitorpamplona.amethyst.commons.notifications.NotificationSummaryState
import com.vitorpamplona.amethyst.commons.notifications.OpenPollsState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.notification_tab_everyone
import com.vitorpamplona.amethyst.commons.resources.notification_tab_following
import com.vitorpamplona.amethyst.commons.ui.feeds.PagerStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverLazyListState
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.NotificationProviderPrompt
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.ObserveInboxRelayListAndDisplayIfNotFound
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

@Composable
fun NotificationScreen(
    scrollToEventId: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NotificationScreen(
        notifFeedContentState = accountViewModel.feedStates.notifications,
        notifFollowingState = accountViewModel.feedStates.notificationsFollowing,
        notifEveryoneState = accountViewModel.feedStates.notificationsEveryone,
        notifSummaryState = accountViewModel.feedStates.notificationSummary,
        notifPolls = accountViewModel.feedStates.notificationsOpenPolls,
        sharedPrefs = accountViewModel.settings.uiSettingsFlow,
        scrollToEventId = scrollToEventId,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun NotificationScreen(
    notifFeedContentState: CardFeedContentState,
    notifFollowingState: CardFeedContentState,
    notifEveryoneState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    notifPolls: OpenPollsState,
    sharedPrefs: UiSettingsFlow,
    scrollToEventId: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NotificationProviderPrompt(sharedPrefs)

    val split by accountViewModel.account.settings.splitNotificationsEnabled
        .collectAsStateWithLifecycle()

    if (split) {
        WatchAccountForNotifications(notifFollowingState, accountViewModel)
        WatchAccountForNotifications(notifEveryoneState, accountViewModel)
        SplitNotificationsScaffold(
            notifFollowingState = notifFollowingState,
            notifEveryoneState = notifEveryoneState,
            notifSummaryState = notifSummaryState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        WatchAccountForNotifications(notifFeedContentState, accountViewModel)
        SingleNotificationsScaffold(
            notifFeedContentState = notifFeedContentState,
            notifSummaryState = notifSummaryState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun SingleNotificationsScaffold(
    notifFeedContentState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                NotificationTopBar(accountViewModel, nav, showSpinner = true)
                SummaryBar(state = notifSummaryState)
            }
        },
        bottomBar = {
            AppBottomBar(Route.Notification(), nav, accountViewModel) { route ->
                if (route is Route.Notification) {
                    notifFeedContentState.invalidateDataAndSendToTop(true)
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        SingleNotificationsBody(
            notifFeedContentState = notifFeedContentState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun SplitNotificationsScaffold(
    notifFollowingState: CardFeedContentState,
    notifEveryoneState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberForeverPagerState(key = PagerStateKeys.NOTIFICATION_SCREEN) { 2 }
    val coroutineScope = rememberCoroutineScope()

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                NotificationTopBar(accountViewModel, nav, showSpinner = false)
                SummaryBar(state = notifSummaryState)
                SecondaryTabRow(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = TabRowHeight,
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        text = { Text(stringRes(Res.string.notification_tab_following)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        text = { Text(stringRes(Res.string.notification_tab_everyone)) },
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    )
                }
            }
        },
        bottomBar = {
            AppBottomBar(Route.Notification(), nav, accountViewModel) { route ->
                if (route is Route.Notification) {
                    val active =
                        if (pagerState.currentPage == 0) notifFollowingState else notifEveryoneState
                    active.invalidateDataAndSendToTop(true)
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        SplitNotificationsBody(
            pagerState = pagerState,
            notifFollowingState = notifFollowingState,
            notifEveryoneState = notifEveryoneState,
            notifPolls = notifPolls,
            scrollToEventId = scrollToEventId,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

/**
 * The refreshable notifications card feed (list + scroll-to-top watcher + inbox-relay
 * warning header). Shared between the Notifications screen and the docked
 * [NotificationSidePanel], parameterized by the persisted scroll-state key so each
 * surface keeps its own position.
 */
@Composable
fun SingleNotificationsBody(
    notifFeedContentState: CardFeedContentState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    scrollStateKey: String = ScrollStateKeys.NOTIFICATION_SCREEN,
) {
    RefresheableBox(notifFeedContentState, true) {
        val listState = rememberForeverLazyListState(scrollStateKey)

        WatchScrollToTop(notifFeedContentState, listState)

        RenderCardFeed(
            feedContent = notifFeedContentState,
            pollContent = notifPolls,
            accountViewModel = accountViewModel,
            listState = listState,
            nav = nav,
            routeForLastRead = NOTIFICATION_LAST_READ_KEY,
            scrollToEventId = scrollToEventId,
            // "X added you to #channel" is not a header prompt any more — each pending invite is a
            // ChannelInviteCard in the feed itself, sorted ahead of the dated rows by
            // NotificationFeedOrderCard.
            headerContent = {
                ObserveInboxRelayListAndDisplayIfNotFound(accountViewModel, nav)
            },
        )
    }
}

@Composable
private fun SplitNotificationsBody(
    pagerState: PagerState,
    notifFollowingState: CardFeedContentState,
    notifEveryoneState: CardFeedContentState,
    notifPolls: OpenPollsState,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    HorizontalPager(state = pagerState) { page ->
        // Only the settled, on-screen tab drives the shared account history pager, so the off-screen tab
        // (composed during a swipe) doesn't page notifications the user isn't looking at.
        val drivesPaging = page == pagerState.currentPage
        when (page) {
            0 -> {
                NotificationPagerPage(
                    state = notifFollowingState,
                    pollContent = notifPolls,
                    scrollStateKey = ScrollStateKeys.NOTIFICATION_FOLLOWING,
                    scrollToEventId = scrollToEventId,
                    drivesPaging = drivesPaging,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            1 -> {
                NotificationPagerPage(
                    state = notifEveryoneState,
                    pollContent = notifPolls,
                    scrollStateKey = ScrollStateKeys.NOTIFICATION_EVERYONE,
                    // Only the Following tab honors the deep-link scroll target so users
                    // aren't bounced when they swipe across to Everyone.
                    scrollToEventId = null,
                    drivesPaging = drivesPaging,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun NotificationPagerPage(
    state: CardFeedContentState,
    pollContent: OpenPollsState,
    scrollStateKey: String,
    scrollToEventId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    drivesPaging: Boolean = true,
) {
    RefresheableBox(state, true) {
        val listState = rememberForeverLazyListState(scrollStateKey)

        WatchScrollToTop(state, listState)

        RenderCardFeed(
            feedContent = state,
            pollContent = pollContent,
            accountViewModel = accountViewModel,
            listState = listState,
            nav = nav,
            routeForLastRead = NOTIFICATION_LAST_READ_KEY,
            scrollToEventId = scrollToEventId,
            headerContent = { ObserveInboxRelayListAndDisplayIfNotFound(accountViewModel, nav) },
            drivesPaging = drivesPaging,
        )
    }
}

@Composable
fun WatchAccountForNotifications(
    notifFeedContentState: CardFeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by
        accountViewModel.account.liveNotificationFollowLists.collectAsStateWithLifecycle()
    val showMessages by
        accountViewModel.account.settings.showMessagesInNotifications
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, showMessages) {
        notifFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
