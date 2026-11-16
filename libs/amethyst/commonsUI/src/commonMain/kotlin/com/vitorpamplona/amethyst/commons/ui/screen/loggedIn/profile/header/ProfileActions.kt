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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.profile.ui.EditButton
import com.vitorpamplona.amethyst.commons.profile.ui.ListButton
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ShowUserButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun ProfileActions(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    MessageButton(baseUser, accountViewModel, nav)

    val isMe by
        remember(accountViewModel) { derivedStateOf { accountViewModel.userProfile() == baseUser } }

    if (isMe) {
        EditButton(nav)
    }

    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseUser)

    if (isHidden) {
        ShowUserButton { accountViewModel.showUser(baseUser.pubkeyHex) }
    } else {
        DisplayFollowUnfollowButton(baseUser, accountViewModel)

        ListButton { nav.nav(Route.PeopleListManagement(baseUser.pubkeyHex)) }
    }
}
