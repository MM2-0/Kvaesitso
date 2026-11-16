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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.profile.ui.FollowButton
import com.vitorpamplona.amethyst.commons.profile.ui.ListButton
import com.vitorpamplona.amethyst.commons.profile.ui.UnfollowButton
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAboutMe
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowing
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.follow
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_follow
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_unfollow
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.ui.components.LocalReadOnlyPreview
import com.vitorpamplona.amethyst.commons.ui.layouts.listItem.SlimListItem
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ShowUserButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun UserCompose(
    baseUser: User,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SlimListItem(
        modifier = modifier.clickable { nav.nav(routeFor(baseUser)) },
        leadingContent = {
            UserPicture(baseUser, Size55dp, accountViewModel = accountViewModel, nav = nav)
        },
        headlineContent = {
            UsernameDisplay(baseUser, accountViewModel = accountViewModel)
        },
        supportingContent = {
            AboutDisplay(baseUser, accountViewModel)
        },
        trailingContent = {
            // A read-only preview (a signer consent prompt) shows who is on a list, not what to do.
            if (!LocalReadOnlyPreview.current) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserActionOptions(baseUser, accountViewModel, nav)
                }
            }
        },
    )
}

@Composable
fun UserActionOptions(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseAuthor)
    if (isHidden) {
        ShowUserButton { accountViewModel.show(baseAuthor) }
    } else {
        ShowFollowingOrUnfollowingButton(baseAuthor, accountViewModel)
        ListButton { nav.nav(Route.PeopleListManagement(baseAuthor.pubkeyHex)) }
    }
}

@Composable
fun ShowFollowingOrUnfollowingButton(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
) {
    val isFollowing = observeUserIsFollowing(accountViewModel.account.userProfile(), baseAuthor, accountViewModel)

    if (isFollowing.value) {
        UnfollowButton(true) {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    Res.string.read_only_user,
                    Res.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollow(baseAuthor)
            }
        }
    } else {
        FollowButton(Res.string.follow, true) {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    Res.string.read_only_user,
                    Res.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.follow(baseAuthor)
            }
        }
    }
}

@Composable
fun UserComposeNoAction(
    baseUser: User,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier = modifier.clickable { nav.nav(routeFor(baseUser)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(baseUser, Size55dp, accountViewModel = accountViewModel, nav = nav)

        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsernameDisplay(baseUser, accountViewModel = accountViewModel)
            }

            AboutDisplay(baseUser, accountViewModel)
        }
    }
}

@Composable
fun AboutDisplay(
    baseAuthor: User,
    accountViewModel: AccountViewModel,
) {
    val aboutMe by observeUserAboutMe(baseAuthor, accountViewModel)

    Text(
        aboutMe,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
