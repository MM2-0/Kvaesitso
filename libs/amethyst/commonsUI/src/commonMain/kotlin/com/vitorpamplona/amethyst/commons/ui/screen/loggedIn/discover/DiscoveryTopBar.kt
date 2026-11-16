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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.TopNavFilterState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.select_list_to_filter
import com.vitorpamplona.amethyst.commons.search.asSearchQuery
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun DiscoveryTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val list by accountViewModel.account.settings.defaultDiscoveryFollowList
        .collectAsStateWithLifecycle()

    // No kind seed here: Discover's tabs span live streams, communities, classifieds and DVM
    // feeds at once, so there is no one window to hand over. What the list spinner narrowed to
    // still seeds, when it is something the token language can say.
    val me = accountViewModel.userProfile().pubkeyHex
    val seed = remember(list, me) { list.asSearchQuery(me) }

    UserDrawerSearchTopBar(accountViewModel, nav, seed) {
        TopNavFilterBar(
            followListsModel = accountViewModel.feedStates.feedListOptions,
            listName = list,
            accountViewModel = accountViewModel,
            onChange = { accountViewModel.account.settings.changeDefaultDiscoveryFollowList(it.code) },
        )
    }
}

@Composable
private fun TopNavFilterBar(
    followListsModel: TopNavFilterState,
    listName: TopFilter,
    accountViewModel: AccountViewModel,
    onChange: (FeedDefinition) -> Unit,
) {
    val allLists by followListsModel.kind3GlobalPeople.collectAsStateWithLifecycle()

    FeedFilterSpinner(
        placeholderCode = listName,
        explainer = stringRes(Res.string.select_list_to_filter),
        options = allLists,
        onSelect = onChange,
        accountViewModel = accountViewModel,
    )
}
