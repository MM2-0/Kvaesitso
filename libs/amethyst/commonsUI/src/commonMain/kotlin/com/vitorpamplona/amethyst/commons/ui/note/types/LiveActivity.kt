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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.LiveFlag
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.ScheduledFlag
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.live_activity_started_ago
import com.vitorpamplona.amethyst.commons.resources.live_activity_starts_in
import com.vitorpamplona.amethyst.commons.resources.live_activity_viewers
import com.vitorpamplona.amethyst.commons.resources.live_activity_watch_recording
import com.vitorpamplona.amethyst.commons.resources.live_stream_has_ended
import com.vitorpamplona.amethyst.commons.resources.live_stream_is_offline
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.CheckIfVideoIsOnline
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeCheckIfVideoIsOnline
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.durationPhrase
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.note.platform.VideoView
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
@Preview
fun RenderLiveActivityEventPreview() {
    val event = Event.fromJson("{\"id\":\"19406ad34ce3c653d62eb73c1816ac27dcf473c2ccdccf5af7d90d2633c62561\",\"pubkey\":\"6b66886b3add72c779d205be574ec2d7cec619061ac3e75717389b26445989e4\",\"created_at\":1719084750,\"kind\":30311,\"tags\":[[\"r\",\"podcast:guid:72d5e069-f907-5ee7-b0d7-45404f4f0aa5\"],[\"r\",\"podcast:item:guid:bfc33d6e-e00f-4f11-a2ff-94865b7867aa\"],[\"d\",\"bfc33d6e-e00f-4f11-a2ff-94865b7867aa\"],[\"title\",\"The Online Identity Time Bomb\"],[\"summary\",\"Online identity is a ticking time bomb. But are trustworthy, open-source solutions ready to disarm it, or will we be stuck with lackluster, proprietary systems?\\n Live chat: https:/jblive.tv\"],[\"streaming\",\"https://jblive.fm\"],[\"starts\",\"1719167400\"],[\"status\",\"planned\"],[\"image\",\"https://station.us-iad-1.linodeobjects.com/art/lup-mp3.jpg\"]],\"content\":\"\",\"sig\":\"2ce3fae9ad4512541aaae4dbd9484f50df62ab95ba935d7512b736f087f151c1d15ec2bf62d3135474f16c3e070f335b24349c5493461873ddca3051804ca944\"}") as LiveActivitiesEvent
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()
    val baseNote: Note?

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
            baseNote = LocalCache.getOrCreateNote("19406ad34ce3c653d62eb73c1816ac27dcf473c2ccdccf5af7d90d2633c62561")
        }
    }

    if (baseNote != null) {
        ThemeComparisonColumn(
            toPreview = {
                RenderLiveActivityEvent(baseNote = baseNote, accountViewModel, nav)
            },
        )
    }
}

@Composable
fun RenderLiveActivityEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RenderLiveActivityEventInner(baseNote = baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderLiveActivityEventInner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? LiveActivitiesEvent ?: return

    val eventUpdates by observeNote(baseNote, accountViewModel)

    val media = remember(eventUpdates) { noteEvent.streaming() }
    val cover = remember(eventUpdates) { noteEvent.image() }
    val subject = remember(eventUpdates) { noteEvent.title() }
    val content = remember(eventUpdates) { noteEvent.summary() }
    val participants = remember(eventUpdates) { noteEvent.participants() }
    val status = remember(eventUpdates) { noteEvent.status() }
    val starts = remember(eventUpdates) { noteEvent.starts() }
    val recording = remember(eventUpdates) { noteEvent.recording() }
    val viewers = remember(eventUpdates) { noteEvent.currentParticipants() }

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

        CrossfadeIfEnabled(targetState = status, label = "RenderLiveActivityEventInner") {
            when (it) {
                StatusTag.STATUS.LIVE -> {
                    media?.let {
                        CrossfadeCheckIfVideoIsOnline(it, accountViewModel) {
                            LiveFlag()
                        }
                    }
                }

                StatusTag.STATUS.PLANNED -> {
                    ScheduledFlag(starts)
                }

                StatusTag.STATUS.ENDED -> {}

                null -> {}
            }
        }
    }

    LiveActivityTimingLine(status, starts, viewers)

    content?.ifBlank { null }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        )
    }

    if (status == StatusTag.STATUS.ENDED && recording != null) {
        LiveActivityRecording(recording, subject, cover, baseNote, accountViewModel)
    } else {
        media?.let { media ->
            if (status == StatusTag.STATUS.LIVE) {
                CheckIfVideoIsOnline(media, accountViewModel) { isOnline ->
                    if (isOnline) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VideoView(
                                videoUri = media,
                                mimeType = null,
                                title = subject,
                                artworkUri = cover,
                                authorName = baseNote.author?.toBestDisplayName(),
                                roundedCorner = true,
                                contentScale = ContentScale.FillWidth,
                                accountViewModel = accountViewModel,
                                nostrUriCallback = "nostr:${baseNote.toNEvent()}",
                                isLiveStream = true,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .padding(10.dp)
                                    .height(100.dp),
                        ) {
                            Text(
                                text = stringRes(id = Res.string.live_stream_is_offline),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else {
                if (status == StatusTag.STATUS.ENDED || (status == StatusTag.STATUS.PLANNED && (starts ?: 0) < TimeUtils.eightHoursAgo())) {
                    LiveActivityEndedCover(cover, baseNote, accountViewModel)
                }
            }
        }
    }

    var participantUsers by remember {
        mutableStateOf<ImmutableList<Pair<ParticipantTag, User>>>(
            persistentListOf(),
        )
    }

    LaunchedEffect(key1 = eventUpdates) {
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

/**
 * "Started 2 hours ago · 140 watching" for a live stream, "Starts in 3 days" for a planned one.
 * Nothing for an ended stream: its start time is history, and the ended banner says the rest.
 */
@Composable
private fun LiveActivityTimingLine(
    status: StatusTag.STATUS?,
    starts: Long?,
    viewers: Int?,
) {
    val now = TimeUtils.now()
    val timing =
        when {
            starts == null -> null
            status == StatusTag.STATUS.PLANNED && starts > now -> stringRes(Res.string.live_activity_starts_in, durationPhrase(starts - now))
            status == StatusTag.STATUS.LIVE && starts <= now -> stringRes(Res.string.live_activity_started_ago, durationPhrase(now - starts))
            else -> null
        }
    val watching =
        if (status == StatusTag.STATUS.LIVE && viewers != null && viewers > 0) {
            pluralStringRes(Res.plurals.live_activity_viewers, viewers, viewers)
        } else {
            null
        }

    val line = listOfNotNull(timing, watching).joinToString(" · ")
    if (line.isEmpty()) return

    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
    )
}

/**
 * An ended stream that left a `recording` tag: the replay, behind a button so a feed full of past
 * streams does not start loading every one of them.
 */
@Composable
private fun LiveActivityRecording(
    recording: String,
    title: String?,
    cover: String?,
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    var playing by remember(recording) { mutableStateOf(false) }

    if (playing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 5.dp),
        ) {
            VideoView(
                videoUri = recording,
                mimeType = null,
                title = title,
                artworkUri = cover,
                authorName = baseNote.author?.toBestDisplayName(),
                roundedCorner = true,
                contentScale = ContentScale.FillWidth,
                accountViewModel = accountViewModel,
                nostrUriCallback = "nostr:${baseNote.toNEvent()}",
                isLiveStream = false,
            )
        }
    } else {
        // The same cover an ended stream without a replay shows, with the way to the replay on it.
        LiveActivityEndedCover(cover, baseNote, accountViewModel) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { playing = true }) {
                Icon(
                    symbol = MaterialSymbols.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(StdHorzSpacer)
                Text(stringRes(Res.string.live_activity_watch_recording))
            }
        }
    }
}

/**
 * An ended stream's cover image (or its author's banner when it has none) with "Live stream has
 * ended" over it, and whatever [actions] the stream still offers under that line.
 */
@Composable
private fun LiveActivityEndedCover(
    cover: String?,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .padding(vertical = 10.dp)
                .heightIn(min = 100.dp),
    ) {
        cover?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription =
                        stringRes(
                            Res.string.preview_card_image_for,
                            it,
                        ),
                    contentScale = ContentScale.FillWidth,
                    mainImageModifier = Modifier.fillMaxWidth(),
                    loadedImageModifier = MaterialTheme.colorScheme.imageModifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageHeaderBackground(baseNote, accountViewModel) },
                    onError = { DefaultImageHeader(baseNote, accountViewModel) },
                )
            }
        } ?: run { DisplayAuthorBanner(baseNote, accountViewModel, MaterialTheme.colorScheme.imageModifier) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringRes(id = Res.string.live_stream_has_ended),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            actions()
        }
    }
}
