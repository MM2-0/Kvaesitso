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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.LiveFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.ScheduledFlag
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.meeting_room_in_space
import com.vitorpamplona.amethyst.commons.resources.meeting_room_in_space_unknown
import com.vitorpamplona.amethyst.commons.resources.meeting_space_closed_tag
import com.vitorpamplona.amethyst.commons.resources.meeting_space_open_tag
import com.vitorpamplona.amethyst.commons.resources.meeting_space_planned_starts_at
import com.vitorpamplona.amethyst.commons.resources.meeting_space_planned_tag
import com.vitorpamplona.amethyst.commons.resources.meeting_space_private_tag
import com.vitorpamplona.amethyst.commons.resources.nest_chat_send_failed_title
import com.vitorpamplona.amethyst.commons.resources.nest_listen_to_recording
import com.vitorpamplona.amethyst.commons.resources.nest_no_app_to_open_link
import com.vitorpamplona.amethyst.commons.resources.nest_presence_left
import com.vitorpamplona.amethyst.commons.resources.nest_presence_listening
import com.vitorpamplona.amethyst.commons.resources.nest_presence_on_stage
import com.vitorpamplona.amethyst.commons.resources.nest_presence_raised_hand
import com.vitorpamplona.amethyst.commons.resources.nest_presence_speaking
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeCheckIfVideoIsOnline
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.lobby.JoinNestButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag as MeetingSpaceStatusTag

@Composable
fun RenderMeetingSpaceEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderMeetingSpaceEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderMeetingSpaceEventInner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? MeetingSpaceEvent ?: return

    val eventUpdates by observeNote(baseNote, accountViewModel)

    val roomName = remember(eventUpdates) { noteEvent.room() }
    val summary = remember(eventUpdates) { noteEvent.summary() }
    val status = remember(eventUpdates) { noteEvent.status() }
    val starts = remember(eventUpdates) { noteEvent.starts() }
    val recording = remember(eventUpdates) { noteEvent.recording() }
    val participants = remember(eventUpdates) { noteEvent.participants() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth(),
    ) {
        roomName?.let {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = StdHorzSpacer)

        CrossfadeIfEnabled(targetState = status, label = "MeetingSpaceStatus") {
            when (it) {
                MeetingSpaceStatusTag.STATUS.LIVE -> {
                    MeetingSpaceOpenFlag()
                }

                MeetingSpaceStatusTag.STATUS.PRIVATE -> {
                    MeetingSpacePrivateFlag()
                }

                MeetingSpaceStatusTag.STATUS.ENDED -> {
                    MeetingSpaceClosedFlag()
                }

                MeetingSpaceStatusTag.STATUS.PLANNED -> {
                    MeetingSpacePlannedFlag(startsUnixSec = starts)
                }

                null -> {}
            }
        }
    }

    summary?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
        )
    }

    RenderParticipants(participants, accountViewModel, nav)

    // In-feed CTA. Live / scheduled rooms get a Join button; closed
    // rooms with a recording get a "Listen to recording" button so
    // audience members who missed the live session can listen back.
    // Closed rooms without a recording stay button-less — there's
    // nothing to enter.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        if (status == MeetingSpaceStatusTag.STATUS.ENDED) {
            recording?.let {
                ListenToRecordingButton(url = it, accountViewModel = accountViewModel)
            }
        } else {
            JoinNestButton(
                event = noteEvent,
                nav = nav,
            )
        }
    }
}

/**
 * Hand off a kind-30312 `recording` URL to the system media
 * player via ACTION_VIEW. Most users have a podcast / audio app
 * registered for `https://...mp3`-style URLs; the system picker
 * lets them choose. Stays out of the in-app audio path on
 * purpose — Amethyst doesn't ship its own audio player surface.
 */
@Composable
private fun ListenToRecordingButton(
    url: String,
    accountViewModel: AccountViewModel,
) {
    val uriHandler = LocalUriHandler.current
    val noAppMessage = stringRes(Res.string.nest_no_app_to_open_link)
    OutlinedButton(onClick = {
        val launched = runCatching { uriHandler.openUri(url) }.isSuccess
        if (!launched) {
            accountViewModel.toastManager.toast(
                Res.string.nest_chat_send_failed_title,
                noAppMessage,
                user = null,
            )
        }
    }) {
        Text(stringRes(Res.string.nest_listen_to_recording))
    }
}

@Composable
fun RenderMeetingRoomEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderMeetingRoomEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderMeetingRoomEventInner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? MeetingRoomEvent ?: return

    val eventUpdates by observeNote(baseNote, accountViewModel)

    val subject = remember(eventUpdates) { noteEvent.title() }
    val media = remember(eventUpdates) { noteEvent.streaming() }
    val status = remember(eventUpdates) { noteEvent.status() }
    val starts = remember(eventUpdates) { noteEvent.starts() }
    val participants = remember(eventUpdates) { noteEvent.participants() }
    val interactiveRoom = remember(eventUpdates) { noteEvent.interactiveRoom() }

    interactiveRoom?.let { spaceTag ->
        ParentMeetingSpaceLink(
            spaceAddress = spaceTag.address,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth(),
    ) {
        subject?.let {
            Text(
                text = it,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = StdHorzSpacer)

        CrossfadeIfEnabled(targetState = status, label = "MeetingRoomStatus") {
            when (it) {
                StatusTag.STATUS.LIVE -> {
                    media?.let {
                        CrossfadeCheckIfVideoIsOnline(it, accountViewModel) {
                            LiveFlag()
                        }
                    } ?: LiveFlag()
                }

                StatusTag.STATUS.PLANNED -> {
                    ScheduledFlag(starts)
                }

                StatusTag.STATUS.ENDED -> {}

                null -> {}
            }
        }
    }

    RenderParticipants(participants, accountViewModel, nav)
}

/**
 * Small "In <Space Name>" row rendered above a kind-30313
 * [MeetingRoomEvent] so users see which kind-30312 space the
 * meeting belongs to. Resolves the addressable note for the parent
 * space; if the kind-30312 hasn't been seen yet the link still
 * renders with a placeholder name and tapping navigates to its
 * thread (which auto-fetches the event). When the parent address
 * doesn't resolve to a kind-30312 we render nothing — keeping the
 * card free of broken links.
 */
@Composable
private fun ParentMeetingSpaceLink(
    spaceAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (spaceAddress.kind != MeetingSpaceEvent.KIND) return

    LoadAddressableNote(
        address = spaceAddress,
    ) { spaceNote ->
        spaceNote ?: return@LoadAddressableNote
        val spaceEvent = spaceNote.event as? MeetingSpaceEvent
        val spaceName =
            spaceEvent?.room()?.ifBlank { null }
                ?: stringRes(Res.string.meeting_room_in_space_unknown)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        nav.nav {
                            routeFor(
                                spaceNote,
                                accountViewModel.account,
                            )
                        }
                    }.padding(vertical = 4.dp),
        ) {
            Text(
                text = stringRes(Res.string.meeting_room_in_space, spaceName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Inline renderer for kind-10312 [MeetingRoomPresenceEvent] events.
 * The event's `content` is empty; the interesting bits are the
 * presence flags. Render a one-line italic status — "@user · raised
 * their hand" — so a presence event that leaks into a feed surface
 * outside the room (search results, thread view of a quoted
 * presence, …) renders as a status note instead of an empty card.
 *
 * The active room's chat panel does NOT render presence events at
 * all — `ChannelFeedFilter` filters them out so the chat stays
 * just chat. This composable is the fallback for everything else.
 */
@Composable
fun RenderMeetingRoomPresence(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? MeetingRoomPresenceEvent ?: return

    val handRaised = remember(event) { event.handRaised() == true }
    val publishing = remember(event) { event.publishing() == true }
    val onstage = remember(event) { event.onstage() == true }

    val stateRes =
        when {
            !publishing && !onstage && !handRaised -> Res.string.nest_presence_left
            handRaised -> Res.string.nest_presence_raised_hand
            onstage && publishing -> Res.string.nest_presence_speaking
            onstage -> Res.string.nest_presence_on_stage
            else -> Res.string.nest_presence_listening
        }

    val user =
        remember(event.pubKey) {
            LocalCache
                .getOrCreateUser(event.pubKey)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { nav.nav(routeFor(user)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClickableUserPicture(user, 20.dp, accountViewModel)
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        UsernameDisplay(
            baseUser = user,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
            accountViewModel = accountViewModel,
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            text = stringRes(stateRes),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                ),
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
private fun RenderParticipants(
    participants: List<ParticipantTag>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<Pair<ParticipantTag, User>>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = participants) {
        accountViewModel.loadParticipants(participants) { newParticipantUsers ->
            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    participantUsers.forEach {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(vertical = 5.dp)
                    .clickable { nav.nav(routeFor(it.second)) },
        ) {
            ClickableUserPicture(it.second, 25.dp, accountViewModel)
            Spacer(StdHorzSpacer)
            UsernameDisplay(it.second, Modifier.weight(1f), accountViewModel = accountViewModel)
            Spacer(StdHorzSpacer)
            it.first.role?.let {
                Text(
                    text =
                        it.capitalize(Locale.current),
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            }
        }
    }
}

private val MeetingSpaceOpenModifier =
    Modifier
        .clip(SmallBorder)
        .background(Color(0xFF4CAF50))
        .padding(horizontal = 5.dp)

private val MeetingSpacePrivateModifier =
    Modifier
        .clip(SmallBorder)
        .background(Color(0xFFFF9800))
        .padding(horizontal = 5.dp)

private val MeetingSpaceClosedModifier =
    Modifier
        .clip(SmallBorder)
        .background(Color.Black)
        .padding(horizontal = 5.dp)

@Composable
fun MeetingSpaceOpenFlag() {
    Text(
        text = stringRes(id = Res.string.meeting_space_open_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = MeetingSpaceOpenModifier,
    )
}

@Composable
fun MeetingSpacePrivateFlag() {
    Text(
        text = stringRes(id = Res.string.meeting_space_private_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = MeetingSpacePrivateModifier,
    )
}

@Composable
fun MeetingSpaceClosedFlag() {
    Text(
        text = stringRes(id = Res.string.meeting_space_closed_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = MeetingSpaceClosedModifier,
    )
}

/**
 * Status badge for kind-30312 rooms with `status=planned`. Renders
 * "SCHEDULED" plus a localized "Starts <human-time>" subline when
 * the event ships a `["starts", "<unix-seconds>"]` tag. Falls back
 * to just "SCHEDULED" when the start time is missing or in the
 * past — matches nostrnests' web client (which also pivots to
 * "Live now" once the moment passes).
 */
@Composable
fun MeetingSpacePlannedFlag(
    startsUnixSec: Long?,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    Column(horizontalAlignment = horizontalAlignment, verticalArrangement = SpacedBy5dp) {
        Text(
            text = stringRes(id = Res.string.meeting_space_planned_tag),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier =
                remember {
                    Modifier
                        .clip(SmallBorder)
                        .background(Color(0xFF1E88E5))
                        .padding(horizontal = 5.dp)
                },
        )
        val now = TimeUtils.now()
        if (startsUnixSec != null && startsUnixSec > now) {
            val pretty =
                remember(startsUnixSec) {
                    formatDateTime(startsUnixSec * 1000L, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)
                }
            Text(
                text = stringRes(Res.string.meeting_space_planned_starts_at, pretty),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
