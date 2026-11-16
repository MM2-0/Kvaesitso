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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.napplets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedDefinition
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_manage_permissions
import com.vitorpamplona.amethyst.commons.resources.select_list_to_filter
import com.vitorpamplona.amethyst.commons.search.SearchSeed
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarNavigationIcon
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.searchRouteFor
import com.vitorpamplona.amethyst.commons.ui.note.SearchIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent

/**
 * Top bar for the Napplets browse screen, matching the other feed screens (Pictures, Articles, …): a
 * drawer/back navigation icon, a centered follow-list [FeedFilterSpinner] that filters which authors'
 * napplets are shown, and Search + "manage permissions" actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NappletsTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ShorterTopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NappletsTopNavFilterBar(accountViewModel)
            }
        },
        navigationIcon = { TopBarNavigationIcon(accountViewModel, nav) },
        actions = {
            IconButton(onClick = { nav.nav(Route.ConnectedApps) }) {
                Icon(MaterialSymbols.Tune, contentDescription = stringRes(Res.string.napplet_manage_permissions))
            }
            IconButton(onClick = { nav.nav(searchRouteFor(SearchSeed.ofKinds(RootNappletEvent.KIND, NamedNappletEvent.KIND))) }) {
                SearchIcon(modifier = Size22Modifier, MaterialTheme.colorScheme.placeholderText)
            }
        },
    )
}

@Composable
private fun NappletsTopNavFilterBar(accountViewModel: AccountViewModel) {
    val listName: TopFilter by accountViewModel.account.settings.defaultNappletsFollowList
        .collectAsStateWithLifecycle()
    val allLists by accountViewModel.feedStates.feedListOptions.authorOnlyRoutes
        .collectAsStateWithLifecycle()

    FeedFilterSpinner(
        placeholderCode = listName,
        explainer = stringRes(Res.string.select_list_to_filter),
        options = allLists,
        onSelect = { selected: FeedDefinition -> accountViewModel.account.settings.changeDefaultNappletsFollowList(selected.code) },
        accountViewModel = accountViewModel,
    )
}
