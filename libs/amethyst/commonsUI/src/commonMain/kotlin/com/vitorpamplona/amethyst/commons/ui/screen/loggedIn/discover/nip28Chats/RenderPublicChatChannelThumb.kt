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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip28Chats

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_about_topic
import com.vitorpamplona.amethyst.commons.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.commons.ui.note.Gallery
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun RenderPublicChatChannelThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadPublicChatChannel(baseNote.idHex) {
        RenderPublicChatChannelThumb(baseNote = baseNote, channel = it, accountViewModel, nav)
    }
}

@Composable
fun RenderPublicChatChannelThumb(
    baseNote: Note,
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelUpdates by observeChannel(channel, accountViewModel)
    val publicChat = (channelUpdates?.channel as? PublicChatChannel) ?: channel

    val name = remember(channelUpdates) { publicChat.toBestDisplayName() }
    val description = remember(channelUpdates) { publicChat.summary()?.ifBlank { null } }
    var cover by remember(channelUpdates) { mutableStateOf(publicChat.profilePicture()?.ifBlank { null }) }

    var participantUsers by
        remember(baseNote) {
            mutableStateOf<ImmutableList<User>>(
                persistentListOf(),
            )
        }

    LaunchedEffect(key1 = channelUpdates) {
        launch(Dispatchers.IO) {
            val topFilter = accountViewModel.account.liveDiscoveryFollowLists.value
            val topFilterAuthors =
                when (topFilter) {
                    is AuthorsByOutboxTopNavFilter -> topFilter.authors
                    is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                    is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                    is SingleCommunityTopNavFilter -> topFilter.authors
                    is AuthorsByProxyTopNavFilter -> topFilter.authors
                    is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
                    is AllFollowsByProxyTopNavFilter -> topFilter.authors
                    else -> null
                }

            val followingKeySet = topFilterAuthors
            val allParticipants =
                ParticipantListBuilder()
                    .followsThatParticipateOn(baseNote, followingKeySet)
                    .toImmutableList()

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.kind3FollowList.flow.value.authors
                    val followingParticipants =
                        ParticipantListBuilder().followsThatParticipateOn(baseNote, allFollows).toList()

                    (followingParticipants + (allParticipants - followingParticipants)).toImmutableList()
                } else {
                    allParticipants.toImmutableList()
                }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    LeftPictureLayout(
        onImage = {
            cover?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(QuoteBorder),
                    onState = {
                        if (it is AsyncImagePainter.State.Error) {
                            cover = null
                        }
                    },
                )
            } ?: run { DisplayAuthorBanner(baseNote, accountViewModel) }
        },
        onTitleRow = {
            Text(
                text = name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = StdHorzSpacer)
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing,
            ) {
                LikeReaction(
                    baseNote = baseNote,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav,
                )
            }
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        onDescription = {
            Text(
                text = description ?: stringRes(Res.string.chat_about_topic, name),
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = HalfTopPadding,
            )
        },
        onBottomRow = {
            if (participantUsers.isNotEmpty()) {
                Gallery(participantUsers, HalfTopPadding, accountViewModel, nav)
            }
        },
    )
}
