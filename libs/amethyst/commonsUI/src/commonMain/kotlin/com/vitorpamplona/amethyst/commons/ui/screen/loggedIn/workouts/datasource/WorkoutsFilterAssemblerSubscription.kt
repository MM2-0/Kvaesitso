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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.workouts.WorkoutsFilterAssembler
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.topNavFeedQueryState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun WorkoutsFilterAssemblerSubscription(accountViewModel: AccountViewModel) {
    WorkoutsFilterAssemblerSubscription(
        accountViewModel.dataSources().workouts,
        accountViewModel,
    )
}

@Composable
fun WorkoutsFilterAssemblerSubscription(
    dataSource: WorkoutsFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val state =
        remember(accountViewModel.account) {
            val account = accountViewModel.account
            accountViewModel.topNavFeedQueryState(
                account.settings.defaultWorkoutsFollowList,
                account.liveWorkoutsFollowListsPerRelay,
                accountViewModel.feedStates.workoutsFeed,
            )
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * The user's own workouts, for screens that need them regardless of the Workouts feed's top-nav
 * selection (My Fitness). Same data source as the feed, keyed to a fixed "Mine": the screen would
 * otherwise only see the workouts some other screen happened to load into the cache. No feed floor,
 * so it asks each of the user's relays for their newest workouts (the filter's own limit) back to
 * any date, which is what a history dashboard needs.
 */
@Composable
fun MyWorkoutsFilterAssemblerSubscription(accountViewModel: AccountViewModel) {
    val state =
        remember(accountViewModel.account) {
            val account = accountViewModel.account
            accountViewModel.topNavFeedQueryState(
                account.mineWorkoutsListName,
                account.liveMineWorkoutsPerRelay,
            )
        }

    LifecycleAwareKeyDataSourceSubscription(state, accountViewModel.dataSources().workouts)
}
