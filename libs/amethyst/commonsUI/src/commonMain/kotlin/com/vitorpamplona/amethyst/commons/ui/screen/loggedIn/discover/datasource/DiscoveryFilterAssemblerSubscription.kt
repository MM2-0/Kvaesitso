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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.relayClient.discover.DiscoveryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.discover.DiscoveryQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun DiscoveryFilterAssemblerSubscription(accountViewModel: AccountViewModel) {
    DiscoveryFilterAssemblerSubscription(
        accountViewModel.dataSources().discovery,
        accountViewModel,
    )
}

@Composable
fun DiscoveryFilterAssemblerSubscription(
    dataSource: DiscoveryFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    // different screens get different states
    // even if they are tracking the same tag.
    val state =
        remember(accountViewModel.account) {
            val account = accountViewModel.account
            val feeds = accountViewModel.feedStates
            DiscoveryQueryState(
                account = account,
                listName = account.settings.defaultDiscoveryFollowList,
                followsPerRelay = account.liveDiscoveryFollowListsPerRelay,
                scope = accountViewModel.viewModelScope,
                reads = feeds.discoverReads,
                marketplace = feeds.discoverMarketplace,
                dvms = feeds.discoverDVMs,
                followSets = feeds.discoverFollowSets,
                live = feeds.discoverLive,
                publicChats = feeds.discoverPublicChats,
                communities = feeds.discoverCommunities,
            )
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
