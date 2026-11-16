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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.playableAudio
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.audio.track.tags.ParticipantTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip14Subject.subject

@Composable
fun RenderAudioTrack(
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AudioTrackEvent ?: return

    AudioTrackHeader(noteEvent, note, contentScale, accountViewModel, nav)
}

@Composable
fun AudioTrackHeader(
    noteEvent: AudioTrackEvent,
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The file the dispatch found to play (its imeta or `m` mime with it), not just `media`/`url`:
    // a podcast that names its show's page in `url` keeps the episode's file in its imeta.
    val media = remember(noteEvent) { noteEvent.playableAudio() }
    val cover = remember(noteEvent) { noteEvent.coverUrl() }
    val subject = remember(noteEvent) { noteEvent.displayTitle() }
    val participants = remember(noteEvent) { noteEvent.participants() }

    var participantUsers by remember { mutableStateOf<List<Pair<ParticipantTag, User>>>(emptyList()) }

    LaunchedEffect(key1 = participants) {
        accountViewModel.loadParticipants(participants) { participantUsers = it }
    }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                subject?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                    ) {
                        Text(
                            text = it,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            participantUsers.forEach {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .padding(top = 5.dp, start = 10.dp, end = 10.dp)
                            .clickable {
                                nav.nav(routeFor(it.second))
                            },
                ) {
                    ClickableUserPicture(it.second, 25.dp, accountViewModel)
                    Spacer(Modifier.width(5.dp))
                    UsernameDisplay(it.second, Modifier.weight(1f), accountViewModel = accountViewModel)
                    Spacer(Modifier.width(5.dp))
                }
            }

            media?.let { media ->
                Column(Modifier.fillMaxWidth()) {
                    AudioFirstMedia(
                        url = media.url,
                        mimeType = media.mimeType,
                        title = subject,
                        cover = cover,
                        showCover = true,
                        waveform = null,
                        note = note,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@Composable
fun RenderAudioHeader(
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AudioHeaderEvent ?: return

    AudioHeader(noteEvent, note, contentScale, accountViewModel, nav)
}

@Composable
fun AudioHeader(
    noteEvent: AudioHeaderEvent,
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val media = remember(noteEvent) { noteEvent.mediaUrl() }
    val mimeType = remember(noteEvent) { noteEvent.blossomMimeType() }
    val waveform = remember(noteEvent) { noteEvent.wavefrom()?.let { WaveformData(it.wave) } }
    val content = remember(noteEvent) { noteEvent.content.ifBlank { null } }

    val defaultBackground = MaterialTheme.colorScheme.background
    val background = remember { mutableStateOf(defaultBackground) }
    val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

    Row(modifier = Modifier.padding(top = 5.dp)) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            media?.let { media ->
                Column(Modifier.fillMaxWidth()) {
                    AudioFirstMedia(
                        url = media,
                        mimeType = mimeType,
                        title = noteEvent.subject(),
                        cover = null,
                        showCover = false,
                        waveform = waveform,
                        note = note,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            val callbackUri = remember(note) { note.toNostrUri() }

            content?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp),
                ) {
                    TranslatableRichTextViewer(
                        content = it,
                        canPreview = true,
                        quotesLeft = 1,
                        tags = tags,
                        backgroundColor = background,
                        id = note.idHex,
                        callbackUri = callbackUri,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            if (noteEvent.hasHashtags()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DisplayUncitedHashtags(noteEvent, callbackUri, accountViewModel, nav)
                }
            }
        }
    }
}
