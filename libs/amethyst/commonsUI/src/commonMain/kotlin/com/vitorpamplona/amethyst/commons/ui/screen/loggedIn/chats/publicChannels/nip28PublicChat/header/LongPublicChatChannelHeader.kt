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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.ui.LeaveChannelButton
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.nip28PublicChat.ui.EditChannelButton
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowingChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.channel_image
import com.vitorpamplona.amethyst.commons.resources.created_at
import com.vitorpamplona.amethyst.commons.resources.groups_no_descriptor
import com.vitorpamplona.amethyst.commons.resources.owner
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.elements.NormalTimeAgo
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions.LinkChatButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions.MuteChatButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions.OpenChatButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions.ShareChatButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.largeProfilePictureModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun LongPublicChatChannelHeader(
    baseChannel: PublicChatChannel,
    lineModifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? PublicChatChannel ?: return

    Spacer(StdVertSpacer)

    channel.info.picture?.let {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = lineModifier,
        ) {
            RobohashFallbackAsyncImage(
                robot = baseChannel.idHex,
                model = it,
                contentDescription = stringRes(Res.string.channel_image),
                modifier = MaterialTheme.colorScheme.largeProfilePictureModifier,
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif =
                    accountViewModel.settings.autoPlayVideosFlow
                        .collectAsStateWithLifecycle()
                        .value,
            )
        }
    }

    channel.info.name?.let {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = lineModifier,
        ) {
            CreateTextWithEmoji(
                text = it,
                tags = channel.infoTags,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.Center, modifier = lineModifier) {
        LongChannelActionOptions(channel, accountViewModel, nav)
    }

    Row(lineModifier) {
        val summary = remember(channelState) { channel.summary()?.ifBlank { null } }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember { mutableStateOf(defaultBackground) }

                TranslatableRichTextViewer(
                    content = summary ?: stringRes(id = Res.string.groups_no_descriptor),
                    canPreview = false,
                    quotesLeft = 1,
                    tags = channel.infoTags,
                    backgroundColor = background,
                    id = baseChannel.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }

    LoadNote(baseNoteHex = channel.idHex) { loadingNote ->
        loadingNote?.let { note ->
            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(id = Res.string.owner),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NoteAuthorPicture(note, Size25dp, accountViewModel = accountViewModel, nav = nav)
                Spacer(DoubleHorzSpacer)
                NoteUsernameDisplay(note, Modifier.weight(1f), accountViewModel = accountViewModel)
            }

            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(id = Res.string.created_at),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NormalTimeAgo(note, Modifier.weight(1f))
                MoreOptionsButton(note, null, accountViewModel, nav)
            }
        }
    }

    Spacer(StdVertSpacer)
}

@Composable
fun LongChannelActionOptions(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    OpenChatButton(channel, accountViewModel, nav)

    LinkChatButton(channel, accountViewModel, nav)

    ShareChatButton(channel, accountViewModel, nav)

    MuteChatButton(channel, accountViewModel)

    EditButtonIfIamCreator(channel, accountViewModel, nav)

    LeaveButtonIfFollowing(channel, accountViewModel)
}

@Composable
fun EditButtonIfIamCreator(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isMe by
        remember(accountViewModel) {
            derivedStateOf { channel.creator == accountViewModel.account.userProfile() }
        }

    if (isMe) {
        EditChannelButton { nav.nav(Route.ChannelMetadataEdit(channel.idHex)) }
    }
}

@Composable
fun LeaveButtonIfFollowing(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
) {
    val isFollowing by observeUserIsFollowingChannel(accountViewModel.account, channel, accountViewModel)

    if (isFollowing) {
        LeaveChannelButton { accountViewModel.unfollow(channel) }
    }
}
