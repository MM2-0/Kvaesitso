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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.lists.list.FollowPackViewModel
import com.vitorpamplona.amethyst.commons.lists.list.PeopleListViewModel
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.follow_set_create_btn_label
import com.vitorpamplona.amethyst.commons.resources.my_lists
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

@Composable
fun ListOfPeopleListsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val list: PeopleListViewModel = rememberViewModel { PeopleListViewModel() }
    list.init(accountViewModel)

    val pack: FollowPackViewModel = rememberViewModel { FollowPackViewModel() }
    pack.init(accountViewModel)

    ListOfPeopleListsScreen(list, pack, accountViewModel, nav)
}

@Composable
fun ListOfPeopleListsScreen(
    list: PeopleListViewModel,
    pack: FollowPackViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.my_lists), nav)
        },
        bottomBar = {
            AppBottomBar(Route.Lists, nav, accountViewModel) { route ->
                if (route == Route.Lists) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { paddingValues ->
        Column(
            Modifier
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ).fillMaxHeight(),
        ) {
            AllPeopleListFeedView(list, pack, listState, nav)
        }
    }
}

@Composable
fun NewListButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Row(horizontalArrangement = SpacedBy5dp, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.PlaylistAdd,
                contentDescription = null,
            )
            Text(stringRes(Res.string.follow_set_create_btn_label))
        }
    }
}
