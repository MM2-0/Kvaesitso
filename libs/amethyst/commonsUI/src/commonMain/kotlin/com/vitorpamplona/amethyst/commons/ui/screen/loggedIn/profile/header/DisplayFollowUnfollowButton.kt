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
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.profile.ui.FollowButton
import com.vitorpamplona.amethyst.commons.profile.ui.UnfollowButton
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowing
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.follow
import com.vitorpamplona.amethyst.commons.resources.follow_back
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_follow
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_unfollow
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun DisplayFollowUnfollowButton(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val isLoggedInFollowingUser by observeUserIsFollowing(accountViewModel.account.userProfile(), baseUser, accountViewModel)
    val isUserFollowingLoggedIn by observeUserIsFollowing(baseUser, accountViewModel.account.userProfile(), accountViewModel)

    if (isLoggedInFollowingUser) {
        UnfollowButton(isInProfileActions = true) {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    Res.string.read_only_user,
                    Res.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollow(baseUser)
            }
        }
    } else {
        if (isUserFollowingLoggedIn) {
            FollowButton(Res.string.follow_back, isInProfileActions = true) {
                if (!accountViewModel.isWriteable()) {
                    accountViewModel.toastManager.toast(
                        Res.string.read_only_user,
                        Res.string.login_with_a_private_key_to_be_able_to_follow,
                    )
                } else {
                    accountViewModel.follow(baseUser)
                }
            }
        } else {
            FollowButton(Res.string.follow, isInProfileActions = true) {
                if (!accountViewModel.isWriteable()) {
                    accountViewModel.toastManager.toast(
                        Res.string.read_only_user,
                        Res.string.login_with_a_private_key_to_be_able_to_follow,
                    )
                } else {
                    accountViewModel.follow(baseUser)
                }
            }
        }
    }
}
