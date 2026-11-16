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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip53LiveActivities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.EndedFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.LiveFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.OfflineFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.ScheduledFlag
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.ui.components.CheckIfVideoIsOnline
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.commons.ui.note.Gallery
import com.vitorpamplona.amethyst.commons.ui.note.LoadLiveActivityChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header.LiveActivitiesChannelHeader
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveStreamLike
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Immutable
data class LiveActivityCard(
    val id: Address?,
    val name: String,
    val cover: String?,
    val media: String?,
    val subject: String?,
    val content: String?,
    val participants: ImmutableList<ParticipantTag>,
    val status: StatusTag.STATUS?,
    val starts: Long?,
)

@Composable
fun RenderLiveActivityThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card by observeNoteAndMap(baseNote, accountViewModel) {
        when (val noteEvent = it.event) {
            is LiveStreamLike -> {
                // Both LiveActivitiesEvent (kind 30311) and MeetingRoomEvent (kind 30312)
                // expose the same NIP-53 streaming tags via LiveStreamLike. address()/dTag()
                // come from the shared BaseAddressableEvent parent.
                LiveActivityCard(
                    id = (noteEvent as BaseAddressableEvent).address(),
                    name = noteEvent.dTag(),
                    cover = noteEvent.image()?.ifBlank { null },
                    media = noteEvent.streaming(),
                    subject = noteEvent.title()?.ifBlank { null },
                    content = noteEvent.summary(),
                    participants = noteEvent.participants().toImmutableList(),
                    status = noteEvent.status(),
                    starts = noteEvent.starts(),
                )
            }

            is MeetingSpaceEvent -> {
                LiveActivityCard(
                    id = noteEvent.address(),
                    name = noteEvent.dTag(),
                    cover = noteEvent.image()?.ifBlank { null },
                    media = null,
                    subject = noteEvent.room()?.ifBlank { null },
                    content = noteEvent.summary(),
                    participants = noteEvent.participants().toImmutableList(),
                    status =
                        when (noteEvent.status()) {
                            com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.LIVE -> StatusTag.STATUS.LIVE
                            com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.PRIVATE -> StatusTag.STATUS.PLANNED
                            com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag.STATUS.ENDED -> StatusTag.STATUS.ENDED
                            else -> null
                        },
                    starts = null,
                )
            }

            else -> {
                LiveActivityCard(
                    id = null,
                    name = "",
                    cover = null,
                    media = null,
                    subject = null,
                    content = null,
                    participants = persistentListOf(),
                    status = null,
                    starts = null,
                )
            }
        }
    }

    RenderLiveActivityThumb(
        card,
        baseNote,
        accountViewModel,
        nav,
    )
}

@Composable
fun RenderLiveActivityThumb(
    card: LiveActivityCard,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = TopEnd,
            modifier =
                Modifier
                    .aspectRatio(ratio = 16f / 9f)
                    .fillMaxWidth(),
        ) {
            card.cover?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(QuoteBorder),
                )
            } ?: run { DisplayAuthorBanner(baseNote, accountViewModel) }

            Box(Modifier.padding(10.dp)) {
                CrossfadeIfEnabled(targetState = card.status, label = "RenderLiveActivityThumb") {
                    when (it) {
                        StatusTag.STATUS.LIVE -> {
                            val url = card.media
                            if (url.isNullOrBlank()) {
                                EndedFlag()
                            } else {
                                CheckIfVideoIsOnline(url, accountViewModel) { isOnline ->
                                    if (isOnline) {
                                        LiveFlag()
                                    } else {
                                        OfflineFlag()
                                    }
                                }
                            }
                        }

                        StatusTag.STATUS.ENDED -> {
                            EndedFlag()
                        }

                        StatusTag.STATUS.PLANNED -> {
                            ScheduledFlag(card.starts)
                        }

                        else -> {
                            EndedFlag()
                        }
                    }
                }
            }

            LoadParticipants(card.participants, baseNote, accountViewModel) { participantUsers ->
                Box(
                    Modifier
                        .padding(10.dp)
                        .align(BottomStart),
                ) {
                    if (participantUsers.isNotEmpty()) {
                        Gallery(participantUsers, Modifier, accountViewModel, nav)
                    }
                }
            }
        }

        Spacer(modifier = DoubleVertSpacer)

        baseNote.address()?.let {
            LoadLiveActivityChannel(it) {
                LiveActivitiesChannelHeader(
                    baseChannel = it,
                    showVideo = false,
                    showFlag = false,
                    sendToChannel = true,
                    modifier = Modifier,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun LoadParticipants(
    participants: ImmutableList<ParticipantTag>,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (ImmutableList<User>) -> Unit,
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<User>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = participants) {
        launch(Dispatchers.IO) {
            val hosts =
                participants.mapNotNull { part ->
                    if (part.pubKey != baseNote.author?.pubkeyHex) {
                        LocalCache.checkGetOrCreateUser(part.pubKey)
                    } else {
                        null
                    }
                }

            val hostsAuthor = hosts + (baseNote.author?.let { listOf(it) } ?: emptyList())

            val topFilter = accountViewModel.account.liveDiscoveryFollowLists.value

            val followingKeySet =
                when (topFilter) {
                    is AuthorsByOutboxTopNavFilter -> topFilter.authors
                    is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                    is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                    is SingleCommunityTopNavFilter -> topFilter.authors
                    is AuthorsByProxyTopNavFilter -> topFilter.authors
                    is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
                    is AllFollowsByProxyTopNavFilter -> topFilter.authors
                    else -> emptySet()
                }

            val allParticipants =
                ParticipantListBuilder()
                    .followsThatParticipateOn(baseNote, followingKeySet)
                    .minus(hostsAuthor)

            val newParticipantUsers =
                if (followingKeySet == null) {
                    val allFollows = accountViewModel.account.kind3FollowList.flow.value.authors
                    val followingParticipants =
                        ParticipantListBuilder()
                            .followsThatParticipateOn(baseNote, allFollows)
                            .minus(hostsAuthor)

                    (hosts + followingParticipants + (allParticipants - followingParticipants))
                        .toImmutableList()
                } else {
                    (hosts + allParticipants).toImmutableList()
                }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    inner(participantUsers)
}
