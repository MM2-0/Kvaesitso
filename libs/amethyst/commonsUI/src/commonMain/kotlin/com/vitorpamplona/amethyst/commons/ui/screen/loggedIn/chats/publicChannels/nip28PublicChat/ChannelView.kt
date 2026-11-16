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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatRoomVouches
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun PublicChatChannelView(
    channelId: String?,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    LoadPublicChatChannel(channelId) {
        PrepareChannelViewModels(
            baseChannel = it,
            draft = draft,
            replyTo = replyTo,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun PrepareChannelViewModels(
    baseChannel: PublicChatChannel,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: ChannelFeedViewModel =
        rememberViewModel(
            key = baseChannel.idHex + "ChannelFeedViewModel",
            factory =
                ChannelFeedViewModel.Factory(
                    baseChannel,
                    accountViewModel.account,
                ),
        )

    val channelScreenModel: ChannelNewMessageViewModel = rememberViewModel { ChannelNewMessageViewModel() }
    channelScreenModel.init(accountViewModel)
    channelScreenModel.load(baseChannel)

    if (draft != null) {
        LaunchedEffect(draft, channelScreenModel, accountViewModel) {
            channelScreenModel.editFromDraft(draft)
        }
    }

    if (replyTo != null) {
        LaunchedEffect(replyTo, channelScreenModel, accountViewModel) {
            channelScreenModel.reply(replyTo)
        }
    }

    ChannelView(
        channel = baseChannel,
        feedViewModel = feedViewModel,
        newPostModel = channelScreenModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ChannelView(
    channel: PublicChatChannel,
    feedViewModel: ChannelFeedViewModel,
    newPostModel: ChannelNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    ChannelFilterAssemblerSubscription(channel, accountViewModel.dataSources().channel, accountViewModel)

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier =
                remember {
                    Modifier
                        .fillMaxHeight()
                        .padding(vertical = 0.dp)
                        .weight(1f, true)
                },
        ) {
            // Who the room vouches for, rebuilt when its metadata changes (a new speaker, member).
            val channelState by channel
                .flow()
                .metadata.stateFlow
                .collectAsStateWithLifecycle()
            val vouches = remember(channelState) { ChatRoomVouches(setOfNotNull(channel.creator?.pubkeyHex)) }
            RefreshingChatroomFeedView(
                feedContentState = feedViewModel.feedState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Channel/${channel.idHex}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
                collapseOutsideNetwork = vouches,
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        // LAST ROW
        EditFieldRow(
            newPostModel,
            accountViewModel,
            onSendNewMessage = feedViewModel.feedState::sendToTop,
            nav,
        )
    }
}
