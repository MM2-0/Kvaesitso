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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.singlepane

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.chats.ui.ChannelFabColumn
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.known
import com.vitorpamplona.amethyst.commons.resources.new_requests
import com.vitorpamplona.amethyst.commons.resources.route_messages
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.WarmJoinedRelayGroupNip11
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.feed.MessagesPager
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.feed.MessagesTabHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.feed.MessagesTabItem
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.persistentListOf

@Composable
fun MessagesSinglePane(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }

    WatchLifecycleAndUpdateModel(knownFeedContentState)
    WatchLifecycleAndUpdateModel(newFeedContentState)

    ChatroomListFilterAssemblerSubscription(accountViewModel)

    val tabs by
        remember(knownFeedContentState) {
            derivedStateOf {
                persistentListOf(
                    MessagesTabItem(Res.string.known, ScrollStateKeys.MESSAGES_KNOWN, knownFeedContentState),
                    MessagesTabItem(Res.string.new_requests, ScrollStateKeys.MESSAGES_NEW, newFeedContentState),
                )
            }
        }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                // No seed: NIP-17 messages are encrypted, so no relay can search them and no
                // kind window would return anything the reader could read.
                UserDrawerSearchTopBar(accountViewModel, nav, null) {
                    Text(stringRes(Res.string.route_messages), fontWeight = FontWeight.Bold)
                }
                MessagesTabHeader(
                    pagerState,
                    tabs,
                    { accountViewModel.markAllChatNotesAsRead(knownFeedContentState.visibleNotes()) },
                    { accountViewModel.markAllChatNotesAsRead(newFeedContentState.visibleNotes()) },
                )
            }
        },
        bottomBar = {
            AppBottomBar(Route.Message, nav, accountViewModel) { route ->
                if (route == Route.Message) {
                    tabs[pagerState.currentPage].feedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                ChannelFabColumn(nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        // Joined groups' rosters + recent-chat previews are kept live by the always-on state + preview
        // subs (mounted at LoggedInPage), so no per-screen group subscription is needed here.

        // Pre-warm NIP-11 for joined groups' host relays so the relay-signed check is a cache hit
        // when those groups surface in discovery or any gated surface.
        WarmJoinedRelayGroupNip11(accountViewModel)

        // The inline-vs-grouped NIP-29 display preference lives in Settings › Messages; joined groups
        // (or per-relay rows in grouped mode) are woven directly into the feed below.
        MessagesPager(
            pagerState,
            tabs,
            accountViewModel,
            nav,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
