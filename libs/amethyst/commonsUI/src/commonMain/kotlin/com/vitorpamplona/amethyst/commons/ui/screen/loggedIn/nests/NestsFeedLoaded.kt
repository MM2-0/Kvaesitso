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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nests.dal.NestsFeedFilter
import com.vitorpamplona.amethyst.commons.nests.dal.NestsFeedFilter.NestBucket
import com.vitorpamplona.amethyst.commons.nests.datasource.NestRoomLivenessProbeSubscription
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.EndedFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.LiveFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.PrivateFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.ScheduledFlag
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.live_stream_ended_tag
import com.vitorpamplona.amethyst.commons.resources.nests_ended_ago
import com.vitorpamplona.amethyst.commons.resources.nests_section_live_now
import com.vitorpamplona.amethyst.commons.resources.nests_section_recently_ended
import com.vitorpamplona.amethyst.commons.resources.nests_section_scheduled
import com.vitorpamplona.amethyst.commons.resources.wallet_loading
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.commons.ui.note.Gallery
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip53LiveActivities.LoadParticipants
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.Size34dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NestsFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    // The DAL emits items pre-sorted in bucket order (LIVE → SCHEDULED
    // → ENDED). Walk the list once to find bucket boundaries so we
    // can inject sticky section headers without re-sorting on every
    // recomposition.
    val sections =
        remember(items.list) {
            val live = ArrayList<Note>()
            val scheduled = ArrayList<Note>()
            val ended = ArrayList<Note>()
            items.list.forEach {
                when (NestsFeedFilter.bucketOf(it.event as? MeetingSpaceEvent)) {
                    NestBucket.LIVE -> live.add(it)
                    NestBucket.SCHEDULED -> scheduled.add(it)
                    NestBucket.ENDED -> ended.add(it)
                }
            }
            Sections(
                live = live.toImmutableList(),
                scheduled = scheduled.toImmutableList(),
                ended = ended.toImmutableList(),
            )
        }

    // Hoist string lookups out of the LazyListScope builder lambda
    // (which is not @Composable) so they can be passed in as plain
    // strings.
    val liveLabel = stringRes(Res.string.nests_section_live_now)
    val scheduledLabel = stringRes(Res.string.nests_section_scheduled)
    val endedLabel = stringRes(Res.string.nests_section_recently_ended)

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        if (sections.live.isNotEmpty()) {
            sectionHeader("live", liveLabel)
            fullSection(sections.live, accountViewModel, nav)
        }
        if (sections.scheduled.isNotEmpty()) {
            sectionHeader("scheduled", scheduledLabel)
            fullSection(sections.scheduled, accountViewModel, nav)
        }
        if (sections.ended.isNotEmpty()) {
            sectionHeader("ended", endedLabel)
            compactSection(sections.ended, accountViewModel, nav)
        }
    }
}

@Immutable
private data class Sections(
    val live: ImmutableList<Note>,
    val scheduled: ImmutableList<Note>,
    val ended: ImmutableList<Note>,
)

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.sectionHeader(
    key: String,
    title: String,
) {
    stickyHeader(key = "header-$key") {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.fullSection(
    rooms: ImmutableList<Note>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    items(rooms.size, key = { rooms[it].idHex }) { index ->
        val item = rooms[index]
        Row(Modifier.fillMaxWidth().animateItem()) {
            NestFeedCard(
                baseNote = item,
                modifier = Modifier.fillMaxWidth(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        HorizontalDivider(thickness = DividerThickness)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.compactSection(
    rooms: ImmutableList<Note>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    items(rooms.size, key = { rooms[it].idHex }) { index ->
        val item = rooms[index]
        Row(Modifier.fillMaxWidth().animateItem()) {
            NestEndedCompactCard(
                baseNote = item,
                modifier = Modifier.fillMaxWidth(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        HorizontalDivider(thickness = DividerThickness)
    }
}

/**
 * Audio-rooms list card. Mirrors [ObserveAndRenderSpace] visually and
 * gates a tap on the underlying [MeetingSpaceEvent] by the same liveness
 * heuristic the badge uses: cards rendering as [EndedFlag] (status =
 * ENDED, status = LIVE with stale presence, or unknown status) route
 * through the read-only [NestLobbyScreen] so re-entry can't accidentally
 * boot the audio pipeline or trigger a host-side kind-30312 refresh on
 * a dead room. Cards that the UI still shows as live, scheduled, or
 * private launch [NestActivity] directly — the lobby's purpose is only
 * to gate stale rooms.
 *
 * Long-pressing the card opens the standard [NoteQuickActionMenu] from
 * [LongPressToQuickAction], so authors can delete (kind:5 against the
 * room's `a`-tag) and listeners can share/copy the room link without
 * needing to enter the room first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NestFeedCard(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val meetingEvent = baseNote.event as? MeetingSpaceEvent ?: return
    val addressableNote = baseNote as? AddressableNote ?: return

    val appPlatform = LocalAppPlatform.current
    val status = meetingEvent.checkStatus(meetingEvent.status())

    val isUiClosed =
        when (status) {
            StatusTag.STATUS.LIVE -> {
                val latestPresence by observeRoomLatestPresence(addressableNote, accountViewModel)
                val presence = latestPresence
                presence != null && presence <= TimeUtils.now() - PRESENCE_FRESHNESS_WINDOW_SECONDS
            }

            StatusTag.STATUS.PRIVATE,
            -> {
                false
            }

            else -> {
                true
            }
        }

    val onClick =
        remember(meetingEvent, isUiClosed) {
            {
                openRoom(meetingEvent, baseNote, isUiClosed, accountViewModel, nav, appPlatform)
            }
        }

    LongPressToQuickAction(baseNote, accountViewModel, nav) { showQuickAction ->
        Column(
            modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = showQuickAction,
                ),
        ) {
            Column(StdPadding) {
                SensitivityWarning(
                    note = baseNote,
                    accountViewModel = accountViewModel,
                ) {
                    ObserveAndRenderSpace(addressableNote, accountViewModel, nav)
                }
            }
        }
    }
}

/**
 * Compact one-line variant for the "Recently ended" bucket. Skips the
 * 16:9 hero, participants gallery, and reaction row to keep the
 * historic list scannable while preserving the entry point — tapping
 * still routes through [Route.NestLobby] so users can open the
 * recording (NIP-53 EGG-11) if one is attached.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NestEndedCompactCard(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val meetingEvent = baseNote.event as? MeetingSpaceEvent ?: return

    val appPlatform = LocalAppPlatform.current
    val onClick =
        remember(meetingEvent) {
            {
                openRoom(meetingEvent, baseNote, isUiClosed = true, accountViewModel, nav, appPlatform)
            }
        }

    val name = meetingEvent.room()?.ifBlank { null } ?: meetingEvent.dTag()
    val cover = meetingEvent.image()?.ifBlank { null }
    val endedAt = baseNote.createdAt()
    val endedAgo = endedAt?.let { timeAgoNoDot(it) }.orEmpty()

    LongPressToQuickAction(baseNote, accountViewModel, nav) { showQuickAction ->
        Row(
            modifier =
                modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = showQuickAction,
                    ).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Size55dp)
                        .clip(QuoteBorder),
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val addressable = baseNote as? AddressableNote
                    if (addressable != null) {
                        DisplayAuthorBanner(addressable, accountViewModel)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val creator = baseNote.author
                if (creator != null) {
                    UsernameDisplay(
                        baseUser = creator,
                        fontWeight = FontWeight.Normal,
                        textColor = MaterialTheme.colorScheme.grayText,
                        accountViewModel = accountViewModel,
                    )
                }
                Text(
                    text =
                        if (endedAgo.isNotEmpty()) {
                            stringRes(Res.string.nests_ended_ago, endedAgo)
                        } else {
                            stringRes(Res.string.live_stream_ended_tag)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            MoreOptionsButton(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/**
 * Resolve the destination for tapping a room card and navigate.
 * Stale-promoted LIVE rooms and ENDED rooms route to the lobby
 * (read-only) so re-entry doesn't kick off the audio pipeline or
 * trigger host-side kind-30312 refreshes on a dead room. Live and
 * private rooms launch [NestActivity] directly. Rooms missing
 * service/endpoint tags fall back to the standard note route so
 * the user at least sees what we know about the event.
 */
private fun openRoom(
    meetingEvent: MeetingSpaceEvent,
    baseNote: Note,
    isUiClosed: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    appPlatform: AppPlatform,
) {
    val service = meetingEvent.service()
    val endpoint = meetingEvent.endpoint()
    val dTag = meetingEvent.address().dTag
    if (!service.isNullOrBlank() && !endpoint.isNullOrBlank() && dTag.isNotBlank()) {
        if (isUiClosed) {
            nav.nav(Route.NestLobby(meetingEvent.address().toValue()))
        } else {
            NestBridge.set(accountViewModel)
            appPlatform.openNestRoom(meetingEvent.address().toValue())
        }
    } else {
        nav.nav { routeFor(baseNote, accountViewModel.account) }
    }
}

@Composable
fun ObserveAndRenderSpace(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card by observeNoteAndMap(baseNote, accountViewModel) {
        when (val noteEvent = it.event) {
            is MeetingSpaceEvent -> {
                NestCard(
                    id = noteEvent.address(),
                    name = noteEvent.dTag(),
                    cover = noteEvent.image()?.ifBlank { null },
                    media = null,
                    subject = noteEvent.room()?.ifBlank { null },
                    content = noteEvent.summary(),
                    participants = noteEvent.participants().toImmutableList(),
                    status = noteEvent.checkStatus(noteEvent.status()),
                    starts = noteEvent.starts(),
                )
            }

            else -> {
                null
            }
        }
    }

    card?.let {
        RenderLiveSpacesThumb(
            it,
            baseNote,
            accountViewModel,
            nav,
        )
    }
}

@Immutable
data class NestCard(
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

/**
 * Window inside which a kind-10312 presence event still counts the
 * room as "live": three consecutive 60 s heartbeat periods. Speakers
 * publish presence on join and re-publish every ~60 s while
 * broadcasting, so anything fresher than this means at least one
 * speaker is still in the room. Older means the host crashed
 * without flipping status to CLOSED, and we should demote the badge
 * to "ended" even though the kind-30312 event still says OPEN.
 */
private const val PRESENCE_FRESHNESS_WINDOW_SECONDS = 180L

/**
 * Liveness-gated badge for an OPEN room. Optimistically renders
 * [LiveFlag] on first paint (we have no presence data yet) and
 * keeps it once a fresh kind-10312 presence lands; flips to
 * [EndedFlag] once we observe that the latest cached presence is
 * older than [PRESENCE_FRESHNESS_WINDOW_SECONDS]. Falls back to
 * [LiveFlag] when the room has no addressable id (defensive — every
 * 30312 event has one in practice).
 */
@Composable
private fun RenderLiveOrEndedFromPresence(
    note: AddressableNote?,
    accountViewModel: AccountViewModel,
) {
    if (note == null) {
        LiveFlag()
        return
    }
    val latestPresence by observeRoomLatestPresence(note, accountViewModel)
    val fresh = latestPresence == null || latestPresence!! > TimeUtils.now() - PRESENCE_FRESHNESS_WINDOW_SECONDS
    if (fresh) LiveFlag() else EndedFlag()
}

/**
 * Most recent kind-10312 presence `createdAt` cached for [address],
 * or null until one arrives. Uses the dedicated
 * [NestRoomLivenessProbeSubscription] — `kinds=[10312], #a=[roomA],
 * limit=1` per outbox relay — so a feed of N rooms doesn't pay
 * each popular room's chat-history pull just to render a LIVE /
 * ENDED badge. The assembler reads version notes off the room's
 * [LocalCache] channel, which `LocalCache.consume(MeetingRoomPresenceEvent, …)`
 * attaches by `#a=room`. The full chat / reaction / admin REQs
 * stay gated behind the join flow (see [NestRoomFilterAssemblerSubscription]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun observeRoomLatestPresence(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
): State<Long?> {
    NestRoomLivenessProbeSubscription(note, accountViewModel)

    val channel = remember(note.idHex) { LocalCache.getOrCreateLiveChannel(note.address) }
    val flow =
        remember(channel) {
            channel
                .flow()
                .notes.stateFlow
                .mapLatest { _ ->
                    var max: Long? = null
                    channel.presenceNotes.forEach { _, note ->
                        val event = note.event
                        if (event is MeetingRoomPresenceEvent) {
                            val createdAt = event.createdAt
                            if (max == null || createdAt > max!!) max = createdAt
                        }
                    }
                    max
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }
    return flow.collectAsStateWithLifecycle(null)
}

@Composable
fun RenderLiveSpacesThumb(
    card: NestCard,
    baseNote: AddressableNote,
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
                CrossfadeIfEnabled(targetState = card.status) {
                    when (it) {
                        StatusTag.STATUS.LIVE -> {
                            RenderLiveOrEndedFromPresence(baseNote, accountViewModel)
                        }

                        StatusTag.STATUS.ENDED -> {
                            EndedFlag()
                        }

                        StatusTag.STATUS.PLANNED -> {
                            ScheduledFlag(card.starts)
                        }

                        StatusTag.STATUS.PRIVATE -> {
                            PrivateFlag()
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

        SpaceHostAndReactions(
            card.subject,
            baseNote,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun SpaceHostAndReactions(
    name: String?,
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val creator = baseNote.author
        if (creator == null) {
            Text(
                text = stringRes(Res.string.wallet_loading),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            UserPicture(
                user = creator,
                size = Size34dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            Column(
                modifier =
                    Modifier
                        .padding(start = 10.dp)
                        .height(35.dp)
                        .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!name.isNullOrBlank()) {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        UsernameDisplay(creator, accountViewModel = accountViewModel)
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .height(Size35dp)
                    .padding(start = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = RowColSpacing,
        ) {
            LikeReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(modifier = StdHorzSpacer)
            MoreOptionsButton(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
