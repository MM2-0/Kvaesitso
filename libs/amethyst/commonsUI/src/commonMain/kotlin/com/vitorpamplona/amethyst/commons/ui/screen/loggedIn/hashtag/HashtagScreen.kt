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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.hashtag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtag.dal.HashtagFeedViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.profile.ui.FollowButton
import com.vitorpamplona.amethyst.commons.profile.ui.UnfollowButton
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowingHashtag
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsMutingHashtag
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_follow
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_hide_word
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_show_word
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_unfollow
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.mute_hashtag
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.resources.unmute_hashtag
import com.vitorpamplona.amethyst.commons.search.SearchSeed
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SearchTopBarAction
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.hashtag.datasource.HashtagFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun HashtagScreen(
    tag: Route.Hashtag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (tag.hashtag.isEmpty()) return

    PrepareViewModelsHashtagScreen(tag, accountViewModel, nav)
}

@Suppress("StateFlowValueCalledInComposition")
@Composable
fun PrepareViewModelsHashtagScreen(
    tag: Route.Hashtag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val hashtagFeedViewModel: HashtagFeedViewModel =
        rememberViewModel(
            key = tag.hashtag + "HashtagFeedViewModel",
            factory =
                HashtagFeedViewModel.Factory(
                    tag.hashtag,
                    accountViewModel.account.followOutboxesOrProxy.flow.value,
                    accountViewModel.account,
                ),
        )

    HashtagScreen(tag, hashtagFeedViewModel, accountViewModel, nav)
}

@Composable
fun HashtagScreen(
    tag: Route.Hashtag,
    feedViewModel: HashtagFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    HashtagFilterAssemblerSubscription(tag, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Text("#${tag.hashtag}", modifier = Modifier.weight(1f))
                    HashtagActionOptions(tag.hashtag, accountViewModel)
                },
                // Search this topic rather than everything: the box opens holding `#tag`.
                actions = { SearchTopBarAction(SearchSeed.byHashtag(tag.hashtag), nav) },
                nav = nav,
            )
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewHashtagPostButton(tag.hashtag, accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableFeedView(
            feedViewModel,
            null,
            accountViewModel = accountViewModel,
            nav = nav,
            onLoaded = { state, listState ->
                HashtagFeedLoaded(tag.hashtag, state, listState, accountViewModel, nav)
            },
        )
    }
}

@Composable
fun HashtagHeader(
    tag: String,
    modifier: Modifier = StdPadding,
    account: AccountViewModel,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "#$tag",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        HashtagActionOptions(tag, account)
    }
}

@Composable
fun HashtagActionOptions(
    tag: String,
    accountViewModel: AccountViewModel,
) {
    val isFollowingTag by observeUserIsFollowingHashtag(tag, accountViewModel)

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    Res.string.read_only_user,
                    Res.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollowHashtag(tag)
            }
        }
    } else {
        FollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    Res.string.read_only_user,
                    Res.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.followHashtag(tag)
            }
        }
    }

    HashtagMuteMenu(tag, accountViewModel)
}

@Composable
fun HashtagMuteMenu(
    tag: String,
    accountViewModel: AccountViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isMuted by observeUserIsMutingHashtag(tag, accountViewModel)

    IconButton(onClick = { menuOpen = true }) {
        Icon(
            symbol = MaterialSymbols.MoreVert,
            contentDescription = stringRes(Res.string.more_options),
            modifier = Modifier.size(22.dp),
        )
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        if (isMuted) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.unmute_hashtag)) },
                onClick = {
                    menuOpen = false
                    if (!accountViewModel.isWriteable()) {
                        accountViewModel.toastManager.toast(
                            Res.string.read_only_user,
                            Res.string.login_with_a_private_key_to_be_able_to_show_word,
                        )
                    } else {
                        accountViewModel.showHashtag(tag)
                    }
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.mute_hashtag)) },
                onClick = {
                    menuOpen = false
                    if (!accountViewModel.isWriteable()) {
                        accountViewModel.toastManager.toast(
                            Res.string.read_only_user,
                            Res.string.login_with_a_private_key_to_be_able_to_hide_word,
                        )
                    } else {
                        accountViewModel.hideHashtag(tag)
                    }
                },
            )
        }
    }
}
