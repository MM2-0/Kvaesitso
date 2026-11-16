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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.chats.ui.ChatSystemMessage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_system_created_channel
import com.vitorpamplona.amethyst.commons.resources.chat_system_created_channel_unnamed
import com.vitorpamplona.amethyst.commons.resources.chat_system_updated_channel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent

/**
 * Channel admin events (NIP-28 kind 40/41) narrate the room instead of talking in
 * it, so they render as a centered system line — "X created the channel", "X
 * updated the channel profile" — that taps through to the channel, instead of a
 * full profile card inside a user bubble.
 */
@Composable
fun RenderChannelAdminSystemMessage(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val noteEvent = note.event) {
        is ChannelCreateEvent -> {
            val authorName = watchAuthorName(note, accountViewModel)
            val channelName = remember(noteEvent) { noteEvent.channelInfo().name }

            ChatSystemMessage(
                text =
                    if (channelName.isNullOrBlank()) {
                        stringRes(Res.string.chat_system_created_channel_unnamed, authorName)
                    } else {
                        stringRes(Res.string.chat_system_created_channel, authorName, channelName)
                    },
                onClick = { nav.nav(Route.PublicChatChannel(note.idHex)) },
            )
        }

        is ChannelMetadataEvent -> {
            val authorName = watchAuthorName(note, accountViewModel)
            val channelId = remember(noteEvent) { noteEvent.channelId() }

            ChatSystemMessage(
                text = stringRes(Res.string.chat_system_updated_channel, authorName),
                onClick = channelId?.let { { nav.nav(Route.PublicChatChannel(it)) } },
            )
        }

        else -> {}
    }
}

@Composable
private fun watchAuthorName(
    note: Note,
    accountViewModel: AccountViewModel,
): String {
    val author = note.author ?: return note.event?.pubKey?.take(8) ?: ""
    val userState by observeUserInfo(author, accountViewModel)
    return userState?.info?.bestName() ?: author.pubkeyDisplayHex()
}
