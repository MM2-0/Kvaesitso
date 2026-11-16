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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities.list.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.relayClient.communities.list.CommunitiesListFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.topNavFeedQueryState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun CommunitiesListFilterAssemblerSubscription(accountViewModel: AccountViewModel) {
    CommunitiesListFilterAssemblerSubscription(
        accountViewModel.dataSources().communitiesList,
        accountViewModel,
    )
}

@Composable
fun CommunitiesListFilterAssemblerSubscription(
    dataSource: CommunitiesListFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val state =
        remember(accountViewModel.account) {
            val account = accountViewModel.account
            accountViewModel.topNavFeedQueryState(
                account.settings.defaultCommunitiesFollowList,
                account.liveCommunitiesFollowListsPerRelay,
                accountViewModel.feedStates.communitiesList,
            )
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
