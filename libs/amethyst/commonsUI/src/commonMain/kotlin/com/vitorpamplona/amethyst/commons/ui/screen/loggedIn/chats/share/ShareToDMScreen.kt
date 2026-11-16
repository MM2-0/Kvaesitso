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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.share_to_dm_start_new
import com.vitorpamplona.amethyst.commons.resources.share_to_dm_title
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.ShareToDMNav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.feed.ChatroomListFeedView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToDMScreen(
    message: String?,
    attachment: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Deliberately a screen-scoped, transient FeedContentState (not wired into
    // AccountFeedContentStates like dmKnown/dmNew). The share picker is a one-shot,
    // short-lived screen, so it owns its feed via viewModelScope and relies on
    // WatchLifecycleAndUpdateModel to load/refresh on entry and resume rather than
    // on the always-on additive update loop. Account switch recreates it (the
    // remember key), which is correct for a transient picker.
    val feedContentState =
        remember(accountViewModel) {
            FeedContentState(
                ShareDMRoomsFeedFilter(accountViewModel.account),
                accountViewModel.viewModelScope,
                LocalCache,
            )
        }

    val shareNav =
        remember(nav, message, attachment) {
            ShareToDMNav(nav, message, attachment)
        }

    WatchLifecycleAndUpdateModel(feedContentState)

    Scaffold(
        topBar = {
            ShorterTopAppBar(title = { Text(stringRes(Res.string.share_to_dm_title)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringRes(Res.string.share_to_dm_start_new),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringRes(Res.string.share_to_dm_start_new),
                        ) { nav.popUpTo(Route.NewGroupDM(message = message, attachment = attachment), Route.ShareToDM::class) }
                        .padding(16.dp),
            )

            HorizontalDivider(thickness = DividerThickness)

            ChatroomListFeedView(
                feedContentState = feedContentState,
                scrollStateKey = "ShareToDM",
                accountViewModel = accountViewModel,
                nav = shareNav,
            )
        }
    }
}
