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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.NewCalendarButton
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.datasource.CalendarsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Top-level screen for browsing NIP-52 kind-31924 calendars (collections of appointments). Reuses
 * the [CalendarsFilterAssembler] subscription so opening this screen also keeps the appointment
 * subscription warm — both feeds share one relay subscription.
 */
@Composable
fun CalendarCollectionsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    CalendarCollectionsScreen(
        feedState = accountViewModel.feedStates.calendarCollectionsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun CalendarCollectionsScreen(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedState)
    WatchAccountForCalendarCollectionsScreen(feedState, accountViewModel)
    CalendarsFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            CalendarCollectionsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.CalendarCollections, nav, accountViewModel) { route ->
                if (route == Route.CalendarCollections) {
                    feedState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewCalendarButton(nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        CalendarCollectionsView(feedState, accountViewModel, nav)
    }
}

@Composable
private fun WatchAccountForCalendarCollectionsScreen(
    feedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveCalendarsFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers by
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        feedState.checkKeysInvalidateDataAndSendToTop()
    }
}
