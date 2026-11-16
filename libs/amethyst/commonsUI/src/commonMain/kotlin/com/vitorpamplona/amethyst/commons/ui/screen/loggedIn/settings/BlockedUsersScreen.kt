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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.blocked_users
import com.vitorpamplona.amethyst.commons.resources.security_blocked_users_empty
import com.vitorpamplona.amethyst.commons.settings.dal.HiddenAccountsFeedViewModel
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun BlockedUsersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: HiddenAccountsFeedViewModel =
        rememberViewModel(factory = HiddenAccountsFeedViewModel.Factory(accountViewModel.account))

    InvalidateOnBlockListChange(accountViewModel) { viewModel.invalidateData() }

    var selected by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            BlockListTopBar(
                title = Res.string.blocked_users,
                selectedCount = selected.size,
                onCancel = { selected = emptySet() },
                onUnblock = {
                    accountViewModel.showUsers(selected.toList())
                    selected = emptySet()
                },
                nav = nav,
            )
        },
    ) { padding ->
        SelectableUserList(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            emptyMessage = Res.string.security_blocked_users_empty,
            selected = selected,
            onToggle = { selected = if (it in selected) selected - it else selected + it },
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
