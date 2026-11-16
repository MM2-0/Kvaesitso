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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nsites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedDefinition
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.select_list_to_filter
import com.vitorpamplona.amethyst.commons.search.SearchSeed
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent

/**
 * Top bar for the nSites browse screen, matching the other feed screens (Pictures, nApplets, …): the
 * avatar drawer + search, wrapping a centered follow-list [FeedFilterSpinner] that filters which
 * authors' nSites are shown.
 */
@Composable
fun NsitesTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UserDrawerSearchTopBar(accountViewModel, nav, SearchSeed.ofKinds(RootSiteEvent.KIND, NamedSiteEvent.KIND)) {
        val listName: TopFilter by accountViewModel.account.settings.defaultNsitesFollowList
            .collectAsStateWithLifecycle()
        val allLists by accountViewModel.feedStates.feedListOptions.authorOnlyRoutes
            .collectAsStateWithLifecycle()

        FeedFilterSpinner(
            placeholderCode = listName,
            explainer = stringRes(Res.string.select_list_to_filter),
            options = allLists,
            onSelect = { selected: FeedDefinition -> accountViewModel.account.settings.changeDefaultNsitesFollowList(selected.code) },
            accountViewModel = accountViewModel,
        )
    }
}
