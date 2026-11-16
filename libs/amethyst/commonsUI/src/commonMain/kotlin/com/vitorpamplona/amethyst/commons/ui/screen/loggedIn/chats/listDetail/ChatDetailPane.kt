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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.listDetail

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.GeohashChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.MarmotGroupChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.ChatroomScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordChannelScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.ephemChat.EphemeralChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupChatScreen
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupThreadsScreen
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** Every chat a list/detail screen can open in its detail pane; see [ChatDetailPane]. */
fun isChatDetailRoute(route: Route): Boolean =
    route is Route.Room ||
        route is Route.PublicChatChannel ||
        route is Route.EphemeralChat ||
        route is Route.GeohashChat ||
        isRelayGroupDetailRoute(route) ||
        route is Route.Concord ||
        route is Route.MarmotGroupChat ||
        isPlatformChatDetailRoute(route)

/** A Buzz / NIP-29 relay's channels: chat channels, forums (their threads) and Buzz DMs. */
fun isRelayGroupDetailRoute(route: Route): Boolean = route is Route.RelayGroup || route is Route.RelayGroupThreads

fun isConcordDetailRoute(route: Route): Boolean = route is Route.Concord

fun isMarmotDetailRoute(route: Route): Boolean = route is Route.MarmotGroupChat

fun isGeohashDetailRoute(route: Route): Boolean = route is Route.GeohashChat

/**
 * Renders a chat [route] in a list/detail screen's detail pane: the same screen the nav host would
 * push, top bar and all, but driven by the pane's [nav] (a `DetailPaneNav`), so it shows no back
 * arrow and its pops close the pane.
 */
@Composable
fun ChatDetailPane(
    route: Route,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (route) {
        is Route.Room -> {
            ChatroomScreen(route.toKey(), route.message, route.attachment, route.replyId, route.draftId, route.expiresDays, accountViewModel, nav)
        }

        is Route.PublicChatChannel -> {
            PublicChatChannelScreen(route.id, route.draftId, route.replyTo, accountViewModel, nav)
        }

        is Route.EphemeralChat -> {
            EphemeralChatScreen(
                id = route.id,
                relayUrl = route.relayUrl,
                draftId = route.draftId,
                replyToId = route.replyTo,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is Route.GeohashChat -> {
            GeohashChatScreen(
                geohash = route.geohash,
                teleported = route.teleported,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is Route.RelayGroup -> {
            RelayGroupChatScreen(
                id = route.id,
                relayUrl = route.relayUrl,
                draftId = route.draftId,
                replyToId = route.replyTo,
                inviteCode = route.inviteCode,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is Route.RelayGroupThreads -> {
            RelayGroupThreadsScreen(
                id = route.id,
                relayUrl = route.relayUrl,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is Route.Concord -> {
            ConcordChannelScreen(
                communityId = route.communityId,
                channelId = route.channelId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is Route.MarmotGroupChat -> {
            MarmotGroupChatScreen(
                nostrGroupId = route.nostrGroupId,
                draftMessage = route.message,
                replyToInnerNote = route.replyId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        else -> {
            PlatformChatDetailPane(route, accountViewModel, nav)
        }
    }
}
