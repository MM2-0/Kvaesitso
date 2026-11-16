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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.MediaTracks
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_chapters
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_number
import com.vitorpamplona.amethyst.commons.resources.podcast_season
import com.vitorpamplona.amethyst.commons.resources.podcast_season_episode
import com.vitorpamplona.amethyst.commons.resources.podcast_transcript
import com.vitorpamplona.amethyst.commons.resources.podcast_video
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.PodcastBadge
import com.vitorpamplona.amethyst.commons.ui.note.PodcastLinkChip
import com.vitorpamplona.amethyst.commons.ui.note.PodcastValueSplits
import com.vitorpamplona.amethyst.commons.ui.note.platform.NoteMediaView
import com.vitorpamplona.amethyst.commons.ui.note.platform.PodcastEpisodeAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

// Bottom-rounded border on the audio player so it visually butts up against the cover's
// top-rounded corners as one card. Constant — keep out of recomposition.
private val PLAYER_BORDER_MODIFIER =
    Modifier.clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderPodcastEpisode(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    // Both NIP-F4 (kind 54) and Podcasting-2.0 (kind 30054) episodes implement PodcastEpisode,
    // so this one renderer serves both. Title/image/description/audio come through the shared
    // abstraction; content and tags come from the underlying event.
    val episode = noteEvent as? PodcastEpisode ?: return

    val title = remember(noteEvent) { episode.episodeTitle() }
    val image = remember(noteEvent) { episode.episodeImage() }
    val description = remember(noteEvent) { episode.episodeDescription() }
    // Prefer audio (podcasts are audio-first); fall back to the video source if that's all the
    // episode ships. The media-controller player handles both.
    val media = remember(noteEvent) { episode.episodeAudio().firstOrNull() ?: episode.episodeVideo() }
    val hasVideo = remember(noteEvent) { episode.episodeVideo() != null }
    val season = remember(noteEvent) { episode.episodeSeason() }
    val episodeNumber = remember(noteEvent) { episode.episodeNumber() }
    val transcriptUrl = remember(noteEvent) { episode.episodeTranscriptUrl() }
    val chaptersUrl = remember(noteEvent) { episode.episodeChaptersUrl() }
    val value = remember(noteEvent) { episode.episodeValue() }
    var chaptersExpanded by remember(noteEvent) { mutableStateOf(false) }
    // Suppress the markdown block if blank — title + description already describe a short
    // episode — and ALSO when it merely repeats the description. Most feeds put the same text in
    // both the `description` tag and the event content, and the thread view renders both blocks
    // (`makeItShort` is false there), so the whole description appeared twice inside one card,
    // each copy with its own "Show More". Compared on collapsed whitespace so a copy differing
    // only in wrapping still counts as a duplicate.
    val markdown =
        remember(noteEvent, description) {
            noteEvent.content.ifBlank { null }?.takeUnless { body ->
                description?.let { normalizeForCompare(body) == normalizeForCompare(it) } == true
            }
        }

    // An `audio` tag can hold a whole music video (`video/mp4`, or a `.mp4` with no type). Drawn as an
    // episode, its frames render inside the waveform strip; once its track list shows a video track
    // it plays through the video player, with the episode's cover as the thumb.
    val tracks = media?.let { rememberMediaTracks(it.url, it.mediaType, accountViewModel) }

    Column(MaterialTheme.colorScheme.replyModifier) {
        if (media != null && tracks == MediaTracks.HAS_VIDEO) {
            NoteMediaView(
                mediaUri = media.url,
                isAudio = false,
                title = title,
                coverUri = image,
                waveform = null,
                contentScale = ContentScale.FillWidth,
                note = note,
                accountViewModel = accountViewModel,
            )
        } else {
            PodcastCoverCard(image, note, accountViewModel)
            media?.let { audio ->
                PodcastEpisodeAudioPlayer(
                    audio = audio,
                    note = note,
                    title = title,
                    image = image,
                    borderModifier = PLAYER_BORDER_MODIFIER,
                    accountViewModel = accountViewModel,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                PodcastBookmarkButton(note, accountViewModel)
            }

            if (season != null || episodeNumber != null || hasVideo || transcriptUrl != null || chaptersUrl != null) {
                val uriHandler = LocalUriHandler.current
                val seasonEpisodeLabel =
                    when {
                        season != null && episodeNumber != null -> stringRes(Res.string.podcast_season_episode, season, episodeNumber)
                        episodeNumber != null -> stringRes(Res.string.podcast_episode_number, episodeNumber)
                        season != null -> stringRes(Res.string.podcast_season, season)
                        else -> null
                    }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    seasonEpisodeLabel?.let {
                        PodcastBadge(
                            label = it,
                            symbol = null,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (hasVideo) {
                        PodcastBadge(
                            label = stringRes(Res.string.podcast_video),
                            symbol = MaterialSymbols.Videocam,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    transcriptUrl?.let { url ->
                        PodcastLinkChip(stringRes(Res.string.podcast_transcript), MaterialSymbols.Description) {
                            runCatching { uriHandler.openUri(url) }
                        }
                    }
                    chaptersUrl?.let {
                        PodcastLinkChip(stringRes(Res.string.podcast_chapters), MaterialSymbols.Checklist) {
                            chaptersExpanded = !chaptersExpanded
                        }
                    }
                }
            }

            if (chaptersExpanded) {
                chaptersUrl?.let { PodcastChaptersSection(it, accountViewModel) }
            }

            description?.let {
                val descriptionTags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = descriptionTags,
                    backgroundColor = backgroundColor,
                    id = note.idHex + "-description",
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            value?.takeIf { !makeItShort }?.let {
                PodcastValueSplits(value = it)
            }

            if (!makeItShort) {
                val persons = remember(noteEvent) { episode.episodePersons() }
                PodcastPeople(persons, accountViewModel, nav)

                val transcriptUrl = remember(noteEvent) { episode.episodeTranscriptUrl() }
                transcriptUrl?.let { PodcastTranscriptView(it, accountViewModel) }
            }

            markdown?.takeIf { !makeItShort }?.let {
                Spacer(Modifier.padding(top = 4.dp))
                val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }
                val callbackUri = remember(noteEvent) { note.toNostrUri() }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

/** Collapses whitespace so two copies of the same text that differ only in wrapping compare equal. */
private fun normalizeForCompare(text: String): String = text.trim().replace(WHITESPACE_RUN, " ")

private val WHITESPACE_RUN = Regex("\\s+")
