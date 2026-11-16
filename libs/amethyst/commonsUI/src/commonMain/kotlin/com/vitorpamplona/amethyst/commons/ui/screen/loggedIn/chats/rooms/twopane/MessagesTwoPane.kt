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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.twopane

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.chats.ui.ChannelFabColumn
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.route_messages
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.layouts.HorizontalTwoPane
import com.vitorpamplona.amethyst.commons.ui.layouts.WidthClass
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.DetailPaneNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.TwoPaneNav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.UserDrawerSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.ChatDetailPane
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.PaneScopedChat
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.isChatDetailRoute
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail.listDetailSplit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.ChatroomView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.WarmJoinedRelayGroupNip11
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun MessagesTwoPane(
    knownFeedContentState: FeedContentState,
    newFeedContentState: FeedContentState,
    widthSizeClass: WidthClass,
    selection: MutableState<Route?>,
    paneWidth: Float,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    /** The index of the currently selected word, or `null` if none is selected */
    val scope = rememberCoroutineScope()
    // Every chat the list can open lands in the right pane, not just NIP-17 rooms and NIP-28
    // channels: the list also weaves in NIP-29/Buzz groups, Concord channels, Marmot and cordn
    // groups, geohash and ephemeral chats.
    val twoPaneNav = remember(nav, selection) { TwoPaneNav(nav, scope, ::isChatDetailRoute, selection) }
    val detailNav = remember(twoPaneNav) { DetailPaneNav(twoPaneNav) }

    // Read on every composition: the pane can cross the Medium/Expanded boundary while this
    // screen stays composed (window resize, the notification panel docking/undocking).
    val split = listDetailSplit(widthSizeClass, paneWidth)

    Scaffold(
        modifier = Modifier.imePaddingSafe(),
        topBar = {
            // No seed: NIP-17 messages are encrypted, so no relay can search them and no kind
            // window would return anything the reader could read.
            UserDrawerSearchTopBar(accountViewModel, nav, null) {
                Text(stringRes(Res.string.route_messages), fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            AppBottomBar(Route.Message, nav, accountViewModel) { route ->
                if (route == Route.Message) {
                    knownFeedContentState.sendToTop()
                    newFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        HorizontalTwoPane(
            first = {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.BottomEnd) {
                    // Joined groups' rosters + previews are kept live by the always-on state + preview
                    // subs (mounted at LoggedInPage), so no per-screen group subscription is needed here.

                    // Pre-warm NIP-11 for joined groups' host relays so the relay-signed check is a
                    // cache hit when those groups surface in discovery or any gated surface.
                    WarmJoinedRelayGroupNip11(accountViewModel)

                    // The inline-vs-grouped NIP-29 preference lives in Settings › Messages; joined
                    // groups (or per-relay rows in grouped mode) are woven into the list itself.
                    ChatroomList(
                        knownFeedContentState,
                        newFeedContentState,
                        accountViewModel,
                        twoPaneNav,
                    )

                    Box(Modifier.padding(Size20dp), contentAlignment = Alignment.Center) {
                        ChannelFabColumn(nav)
                    }
                }
            },
            second = {
                // Consumed as well as applied: the emoji panel docks inside this pane and would pad
                // the navigation bar the Scaffold's padding already holds.
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    twoPaneNav.innerNav.value?.let {
                        PaneScopedChat(it) {
                            when (it) {
                                is Route.Room -> {
                                    ChatroomView(
                                        room = it.toKey(),
                                        accountViewModel = accountViewModel,
                                        draftMessage = it.message,
                                        replyToNote = it.replyId,
                                        editFromDraft = it.draftId,
                                        expiresDays = it.expiresDays,
                                        nav = nav,
                                    )
                                }

                                is Route.PublicChatChannel -> {
                                    PublicChatChannelView(
                                        channelId = it.id,
                                        accountViewModel = accountViewModel,
                                        nav = nav,
                                    )
                                }

                                else -> {
                                    // The other chats bring their own top bar (members, pins, join), so
                                    // they render as full screens. The pane consumed the Messages top
                                    // bar's insets above, so theirs don't add the status bar again.
                                    ChatDetailPane(it, accountViewModel, detailNav)
                                }
                            }
                        }
                    }
                }
            },
            splitFraction = split.fraction,
            modifier = Modifier.fillMaxSize(),
            onDividerDrag = split.onDrag,
        )
    }
}
