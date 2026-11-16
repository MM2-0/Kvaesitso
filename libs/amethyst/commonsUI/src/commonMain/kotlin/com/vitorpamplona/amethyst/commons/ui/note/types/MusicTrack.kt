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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.music.MusicTrackMedia
import com.vitorpamplona.amethyst.commons.music.formatTrackDuration
import com.vitorpamplona.amethyst.commons.music.media
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_explicit
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.note.platform.NoteMediaView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font10SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val COVER_ASPECT_RATIO = 1f

// Content text prefixes that some music-publishing tools auto-fill — purely restate the
// title/artist that the card already shows above. Suppress the description block in that case
// so the card doesn't waste a paragraph echoing its own header.
private val MUSIC_TRACK_BOILERPLATE_PREFIXES =
    listOf(
        "Listen to my song",
    )

@Composable
fun RenderMusicTrack(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? MusicTrackEvent ?: return

    MusicTrackHeader(
        noteEvent = noteEvent,
        note = note,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicTrackHeader(
    noteEvent: MusicTrackEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val artist = remember(noteEvent) { noteEvent.artist() }
    val media = remember(noteEvent) { noteEvent.media() }
    val image = remember(noteEvent) { noteEvent.image() }
    val album = remember(noteEvent) { noteEvent.album() }
    val trackNumber = remember(noteEvent) { noteEvent.trackNumber() }
    val released = remember(noteEvent) { noteEvent.released() }
    val duration = remember(noteEvent) { noteEvent.duration() }
    val isExplicit = remember(noteEvent) { noteEvent.isExplicit() }
    // Suppress publishing-tool boilerplate that just restates the title/artist already shown
    // above. Today the only known offender is "Listen to my song ..." (emitted by some Blossom
    // uploaders); extend MUSIC_TRACK_BOILERPLATE_PREFIXES if more turn up.
    val description =
        remember(noteEvent) {
            noteEvent.content
                .takeUnless { content ->
                    MUSIC_TRACK_BOILERPLATE_PREFIXES.any { content.trim().startsWith(it, ignoreCase = true) }
                }?.ifBlank { null }
        }
    val topics =
        remember(noteEvent) {
            noteEvent.tags
                .mapNotNull { if (it.size > 1 && it[0] == "t" && it[1] != MusicTrackEvent.GENRE_TAG) it[1] else null }
                .distinct()
                .take(4)
                .toImmutableList()
        }

    Column(MaterialTheme.colorScheme.replyModifier) {
        when (media) {
            // A music video — from the `video` tag, or a video file published as `url` — plays
            // through the full video player, with the album cover as its thumb when there is one.
            is MusicTrackMedia.Video -> {
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
            }

            // Audio: the cover, with the player strip butting directly against it so the cover's
            // top corners and the strip's bottom corners read as one continuous unit — unless an
            // `.mp4` `url` turns out to be a music video, which then plays as one.
            is MusicTrackMedia.Audio -> {
                AudioFirstMedia(
                    url = media.url,
                    mimeType = media.mimeType,
                    title = title,
                    cover = image,
                    showCover = true,
                    waveform = null,
                    note = note,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            null -> {
                MusicTrackCover(image, note, accountViewModel)
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            artist?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        symbol = MaterialSymbols.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (album != null || released != null || duration != null || isExplicit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    album?.let {
                        val trackPart = trackNumber?.let { n -> " · #$n" } ?: ""
                        MetaText(text = it + trackPart)
                    }
                    if (album != null && (released != null || duration != null || isExplicit)) MetaSeparator()
                    released?.let { MetaText(text = it) }
                    if (released != null && (duration != null || isExplicit)) MetaSeparator()
                    duration?.let { MetaText(text = formatTrackDuration(it)) }
                    if (duration != null && isExplicit) MetaSeparator()
                    if (isExplicit) ExplicitBadge()
                }
            }

            description?.takeIf { !makeItShort }?.let {
                Spacer(Modifier.padding(top = 4.dp))
                val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }
                val callbackUri = remember(note) { note.toNostrUri() }

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
                // No DisplayUncitedHashtags here — the TopicChip FlowRow below already shows
                // every non-genre-marker hashtag, and stacking both produces visually identical
                // duplicate chip rows.
            }

            if (topics.isNotEmpty()) {
                Spacer(Modifier.padding(top = 4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    topics.forEach { topic ->
                        TopicChip(topic) { nav.nav(Route.Hashtag(topic.lowercase())) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MusicTrackCover(
    image: String?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val imageShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(imageShape)

    Box(imageModifier) {
        if (image != null) {
            MyAsyncImage(
                imageUrl = image,
                contentDescription = stringRes(Res.string.preview_card_image_for, image),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = imageModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel, imageModifier) },
                onError = { DefaultImageHeader(note, accountViewModel, imageModifier) },
            )
        } else {
            DefaultImageHeader(note, accountViewModel, imageModifier)
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MetaSeparator() {
    Text(
        text = " · ",
        color = MaterialTheme.colorScheme.grayText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ExplicitBadge() {
    val description = stringRes(Res.string.podcast_explicit)
    Text(
        text = "E",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .semantics { contentDescription = description },
    )
}

@Composable
private fun TopicChip(
    topic: String,
    onClick: (() -> Unit)? = null,
) {
    // clip→clickable→background→padding so the tap target is the same as the visible chip
    // and the Material ripple stays inside the rounded shape.
    val base =
        Modifier
            .clip(CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = base,
    )
}

// ---------------------------------------------------------------------------
// @Preview composables. These wire a constructed MusicTrackEvent through
// LocalCache so the renderer pulls the real Note path it would in production.
// The audio/image URLs are intentionally unreachable — VideoView falls back to
// its tap-to-load thumbnail state, and MyAsyncImage falls back to the
// DefaultImageHeader robohash. That's the same first-paint state real users
// see for a track they haven't tapped to download yet, which is what the
// preview is meant to show.
// ---------------------------------------------------------------------------

private const val PREVIEW_TRACK_PUBKEY = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"
private val PREVIEW_TRACK_SIG = "0".repeat(128)

// Deterministic 64-char hex from any string — Event ids must be hex even for previews so
// downstream consumers that validate the format don't blow up. Not cryptographic; just
// reproducible per (preview, dTag, title) so the LocalCache slot stays stable across recomposes.
private fun previewHexId(prefix: String): String {
    val mixed = (prefix.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
    return mixed.padStart(64, 'a').take(64)
}

private fun previewMusicTrackEvent(
    dTag: String,
    title: String,
    artist: String,
    album: String? = null,
    duration: Int? = null,
    released: String? = null,
    trackNumber: Int? = null,
    explicit: Boolean = false,
    image: String? = "https://example.invalid/cover.jpg",
    url: String = "https://example.invalid/track.mp3",
    content: String = "",
): MusicTrackEvent {
    val tags =
        buildList {
            add(arrayOf("d", dTag))
            add(arrayOf("title", title))
            add(arrayOf("artist", artist))
            add(arrayOf("url", url))
            add(arrayOf("t", "music"))
            image?.let { add(arrayOf("image", it)) }
            album?.let { add(arrayOf("album", it)) }
            trackNumber?.let { add(arrayOf("track_number", it.toString())) }
            released?.let { add(arrayOf("released", it)) }
            duration?.let { add(arrayOf("duration", it.toString())) }
            if (explicit) add(arrayOf("explicit", "true"))
        }.toTypedArray()

    return MusicTrackEvent(
        id = previewHexId("track-$dTag-$title"),
        pubKey = PREVIEW_TRACK_PUBKEY,
        createdAt = 1_730_000_000L,
        tags = tags,
        content = content,
        sig = PREVIEW_TRACK_SIG,
    )
}

@Preview
@Composable
private fun RenderMusicTrackPreview() {
    val event =
        previewMusicTrackEvent(
            dTag = "summer-nights-2024",
            title = "Summer Nights",
            artist = "The Midnight Collective",
            album = "Endless Summer",
            duration = 245,
            released = "2024-06-15",
            trackNumber = 3,
            explicit = false,
            content = "Lyrics:\n[Verse 1]\nWalking through the city lights...\n\nProducer: John Doe",
        )

    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue()) { note ->
            note?.let {
                RenderMusicTrack(
                    note = it,
                    makeItShort = false,
                    canPreview = true,
                    backgroundColor = remember { mutableStateOf(Color.Transparent) },
                    accountViewModel = mockAccountViewModel(),
                    nav = EmptyNav(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun RenderMusicTrackExplicitPreview() {
    val event =
        previewMusicTrackEvent(
            dTag = "loud-and-clear",
            title = "Loud and Clear",
            artist = "Static Riot",
            album = "Distortion",
            duration = 187,
            explicit = true,
            content = "",
        )

    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue()) { note ->
            note?.let {
                RenderMusicTrack(
                    note = it,
                    makeItShort = false,
                    canPreview = true,
                    backgroundColor = remember { mutableStateOf(Color.Transparent) },
                    accountViewModel = mockAccountViewModel(),
                    nav = EmptyNav(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun MusicTrackCoverPreview() {
    val event = previewMusicTrackEvent(dTag = "cover-only", title = "Cover Only", artist = "Anon")
    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue()) { note ->
            note?.let { MusicTrackCover(image = null, note = it, accountViewModel = mockAccountViewModel()) }
        }
    }
}

@Preview
@Composable
private fun ExplicitBadgePreview() {
    ThemeComparisonColumn {
        Row(modifier = Modifier.padding(8.dp)) {
            ExplicitBadge()
        }
    }
}

@Preview
@Composable
private fun TopicChipPreview() {
    ThemeComparisonColumn {
        Row(modifier = Modifier.padding(8.dp)) {
            TopicChip("electronic")
            Spacer(Modifier.padding(start = 4.dp))
            TopicChip("synthwave")
            Spacer(Modifier.padding(start = 4.dp))
            TopicChip("80s")
        }
    }
}
