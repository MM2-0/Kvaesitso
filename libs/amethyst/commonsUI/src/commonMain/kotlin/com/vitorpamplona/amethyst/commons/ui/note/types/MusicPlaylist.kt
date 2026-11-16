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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.music.formatTrackDuration
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.music_playlist_collaborative
import com.vitorpamplona.amethyst.commons.resources.music_playlist_edit_action
import com.vitorpamplona.amethyst.commons.resources.music_playlist_loading_track
import com.vitorpamplona.amethyst.commons.resources.music_playlist_more_tracks
import com.vitorpamplona.amethyst.commons.resources.music_playlist_private
import com.vitorpamplona.amethyst.commons.resources.music_playlist_track_count
import com.vitorpamplona.amethyst.commons.resources.music_playlist_unknown_track
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageBanner
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageBannerBackground
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val PLAYLIST_COVER_ASPECT_RATIO = 1f
private const val MAX_PREVIEW_TRACKS = 25

@Composable
fun RenderMusicPlaylist(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observe so the card recomposes when a newer version of this playlist arrives from
    // a relay (e.g. tracks added/removed, title changed). observeNoteEvent also drives
    // the underlying EventFinderFilterAssembler subscription, so the playlist body itself
    // gets fetched from relays if only the address was known.
    val noteEvent by observeNoteEvent<MusicPlaylistEvent>(note, accountViewModel)
    val event = noteEvent ?: return

    MusicPlaylistHeader(
        noteEvent = event,
        note = note,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun MusicPlaylistHeader(
    noteEvent: MusicPlaylistEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val image = remember(noteEvent) { noteEvent.image() }
    // Some publishing tools copy the same string into both the `description` tag and the JSON
    // `content` field. Dedupe so we only show the long-form once: keep `content` (the
    // richer of the two — Markdown / NIP-19 references render through TranslatableRichTextViewer
    // below) and suppress the short description when it would just duplicate it.
    val rawShortDescription = remember(noteEvent) { noteEvent.description() }
    val longDescription = remember(noteEvent) { noteEvent.content.ifBlank { null } }
    val shortDescription =
        remember(rawShortDescription, longDescription) {
            rawShortDescription?.takeUnless { short ->
                longDescription != null && short.trim().equals(longDescription.trim(), ignoreCase = true)
            }
        }
    val trackAddresses = remember(noteEvent) { noteEvent.trackAddresses() }
    val isCollaborative = remember(noteEvent) { noteEvent.isCollaborative() }
    val isPrivate = remember(noteEvent) { noteEvent.isPrivate() }
    // The composer can only resolve and edit a playlist the logged-in user authored (it loads the
    // addressable from their own pubkey), so only surface the edit affordance for owned playlists.
    val isOwnPlaylist = remember(note) { accountViewModel.isLoggedUser(note.author) }

    Column(MaterialTheme.colorScheme.replyModifier) {
        MusicPlaylistCover(image, note, trackAddresses.size, accountViewModel)

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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.padding(start = 6.dp))
                val trackCount = trackAddresses.size
                Text(
                    text = pluralStringRes(Res.plurals.music_playlist_track_count, trackCount, trackCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
                if (isCollaborative) {
                    PlaylistTag(text = stringRes(Res.string.music_playlist_collaborative))
                }
                if (isPrivate) {
                    PlaylistTag(text = stringRes(Res.string.music_playlist_private))
                }
                if (isOwnPlaylist) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        symbol = MaterialSymbols.Edit,
                        contentDescription = stringRes(Res.string.music_playlist_edit_action),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .clickable { nav.nav(Route.NewMusicPlaylist(dTag = noteEvent.dTag())) }
                                .padding(4.dp)
                                .size(20.dp),
                    )
                }
            }

            shortDescription?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            longDescription?.takeIf { !makeItShort }?.let {
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
            }

            if (trackAddresses.isNotEmpty()) {
                Spacer(Modifier.padding(top = 4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    trackAddresses
                        .take(MAX_PREVIEW_TRACKS)
                        .forEachIndexed { index, address ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                )
                            }
                            LoadAddressableNote(address) { trackNote ->
                                if (trackNote != null) {
                                    PlaylistTrackRow(
                                        position = index + 1,
                                        trackNote = trackNote,
                                        accountViewModel = accountViewModel,
                                        nav = nav,
                                    )
                                } else {
                                    MissingPlaylistTrackRow(position = index + 1)
                                }
                            }
                        }

                    if (trackAddresses.size > MAX_PREVIEW_TRACKS) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        val remaining = trackAddresses.size - MAX_PREVIEW_TRACKS
                        Text(
                            text = pluralStringRes(Res.plurals.music_playlist_more_tracks, remaining, remaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.grayText,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPlaylistCover(
    image: String?,
    note: Note,
    trackCount: Int,
    accountViewModel: AccountViewModel,
) {
    val imageShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(PLAYLIST_COVER_ASPECT_RATIO)
            .clip(imageShape)

    Box(imageModifier) {
        if (image == null) {
            // No artwork at all: banner + author avatar + chip side-by-side at the bottom.
            BannerWithAuthorChip(note, trackCount, imageModifier, accountViewModel, blurred = false)
        } else {
            // Drive the painter directly so we can layer the loaded image and the chip as
            // separate siblings of the same Box — using SubcomposeAsyncImage's content-lambda
            // confused its layout (the chip's wrap-content Box was stretched to the cover's
            // full size). A plain Image + painter-state observation behaves predictably.
            val painter = rememberAsyncImagePainter(model = image)
            val state by painter.state.collectAsState()

            when (state) {
                is AsyncImagePainter.State.Success -> {
                    Image(
                        painter = painter,
                        contentDescription = stringRes(Res.string.preview_card_image_for, image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    TrackCountChip(
                        trackCount = trackCount,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                    )
                }

                is AsyncImagePainter.State.Error -> {
                    BannerWithAuthorChip(note, trackCount, imageModifier, accountViewModel, blurred = false)
                }

                else -> {
                    BannerWithAuthorChip(note, trackCount, imageModifier, accountViewModel, blurred = true)
                }
            }
        }
    }
}

/**
 * Author-banner cover used whenever the playlist has no album artwork to show — either
 * because the playlist doesn't carry an `image` tag, or because the configured URL is
 * still loading or failed to load. Lays out the author's avatar and the track-count chip
 * side-by-side at the bottom-left so the chip never overlaps the avatar.
 */
@Composable
private fun BannerWithAuthorChip(
    note: Note,
    trackCount: Int,
    imageModifier: Modifier,
    accountViewModel: AccountViewModel,
    blurred: Boolean,
) {
    Box(imageModifier) {
        if (blurred) {
            DefaultImageBannerBackground(note, accountViewModel, imageModifier)
        } else {
            DefaultImageBanner(note, accountViewModel, imageModifier)
        }

        WatchAuthor(baseNote = note, accountViewModel = accountViewModel) { author ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
            ) {
                BaseUserPicture(author, Size55dp, accountViewModel, Modifier)
                Spacer(Modifier.padding(start = 8.dp))
                TrackCountChip(trackCount = trackCount)
            }
        }
    }
}

@Composable
private fun TrackCountChip(
    trackCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.PlaylistAdd,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.padding(start = 6.dp))
            Text(
                text = pluralStringRes(Res.plurals.music_playlist_track_count, trackCount, trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    position: Int,
    trackNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observe the track note so each row recomposes when the track event arrives from a
    // relay (cards switch from "Unknown track" placeholder to the real title/artist/cover)
    // or a newer revision replaces the cached one. observeNoteEvent also drives the
    // EventFinderFilterAssembler subscription, so the row pulls the track itself if only
    // the address is known.
    val trackEvent by observeNoteEvent<MusicTrackEvent>(trackNote, accountViewModel)
    val title = trackEvent?.title() ?: stringRes(Res.string.music_playlist_unknown_track)
    val artist = trackEvent?.artist()
    val duration = trackEvent?.duration()
    val cover = trackEvent?.image()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.Note(trackNote.idHex)) }
                .padding(vertical = 8.dp),
    ) {
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            modifier =
                Modifier
                    .width(28.dp)
                    .padding(end = 4.dp),
        )

        if (cover != null) {
            MyAsyncImage(
                imageUrl = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                loadedImageModifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                accountViewModel = accountViewModel,
                onLoadingBackground = {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                    )
                },
                onError = { TrackCoverPlaceholder() },
            )
        } else {
            TrackCoverPlaceholder()
        }

        Spacer(Modifier.padding(start = 10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            artist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        duration?.let {
            Spacer(Modifier.padding(start = 6.dp))
            Text(
                text = formatTrackDuration(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MissingPlaylistTrackRow(position: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            modifier =
                Modifier
                    .width(28.dp)
                    .padding(end = 4.dp),
        )
        TrackCoverPlaceholder()
        Spacer(Modifier.padding(start = 10.dp))
        Text(
            text = stringRes(Res.string.music_playlist_loading_track),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrackCoverPlaceholder() {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PlaylistTag(text: String) {
    Spacer(Modifier.padding(start = 8.dp))
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.grayText,
        )
    }
}

// ---------------------------------------------------------------------------
// @Preview composables. Constructs a MusicPlaylistEvent plus a handful of
// MusicTrackEvents it references, pushes them through LocalCache, then
// renders the same code path the production feed uses. The cover image URL
// is unreachable on purpose so the cover falls back to the deterministic
// banner + author-avatar layout — that's what users see while the real
// cover is still loading.
// ---------------------------------------------------------------------------

private const val PREVIEW_PLAYLIST_PUBKEY = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"
private val PREVIEW_PLAYLIST_SIG = "0".repeat(128)

private fun previewPlaylistHexId(prefix: String): String {
    val mixed = (prefix.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
    return mixed.padStart(64, 'a').take(64)
}

private fun previewPlaylistTrack(
    dTag: String,
    title: String,
    artist: String,
    duration: Int,
): MusicTrackEvent =
    MusicTrackEvent(
        id = previewPlaylistHexId("ptrack-$dTag-$title"),
        pubKey = PREVIEW_PLAYLIST_PUBKEY,
        createdAt = 1_730_000_000L,
        tags =
            arrayOf(
                arrayOf("d", dTag),
                arrayOf("title", title),
                arrayOf("artist", artist),
                arrayOf("url", "https://example.invalid/$dTag.mp3"),
                arrayOf("duration", duration.toString()),
                arrayOf("t", "music"),
            ),
        content = "",
        sig = PREVIEW_PLAYLIST_SIG,
    )

private fun previewPlaylistEvent(
    dTag: String,
    title: String,
    description: String,
    tracks: List<MusicTrackEvent>,
    image: String? = "https://example.invalid/playlist.jpg",
    isCollaborative: Boolean = false,
    isPrivate: Boolean = false,
    content: String = "",
): MusicPlaylistEvent {
    val tags =
        buildList {
            add(arrayOf("d", dTag))
            add(arrayOf("title", title))
            add(arrayOf("description", description))
            image?.let { add(arrayOf("image", it)) }
            add(arrayOf("t", "playlist"))
            tracks.forEach { track ->
                add(arrayOf("a", "${MusicTrackEvent.KIND}:${track.pubKey}:${track.dTag()}"))
            }
            if (isPrivate) add(arrayOf("private", "true")) else add(arrayOf("public", "true"))
            if (isCollaborative) add(arrayOf("collaborative", "true"))
        }.toTypedArray()

    return MusicPlaylistEvent(
        id = previewPlaylistHexId("pl-$dTag-$title"),
        pubKey = PREVIEW_PLAYLIST_PUBKEY,
        createdAt = 1_730_000_000L,
        tags = tags,
        content = content,
        sig = PREVIEW_PLAYLIST_SIG,
    )
}

@Preview
@Composable
private fun RenderMusicPlaylistPreview() {
    val tracks =
        listOf(
            previewPlaylistTrack("summer-nights-2024", "Summer Nights", "The Midnight Collective", 245),
            previewPlaylistTrack("sunset-dreams", "Sunset Dreams", "Pacific Wave", 198),
            previewPlaylistTrack("ocean-breeze", "Ocean Breeze", "The Midnight Collective", 312),
        )
    val event =
        previewPlaylistEvent(
            dTag = "summer-vibes-2024",
            title = "Summer Vibes 2024",
            description = "Chill electronic tracks for summer",
            tracks = tracks,
            content = "My favorite summer vibes from 2024",
        )

    remember(event) {
        runBlocking {
            withContext(Dispatchers.IO) {
                tracks.forEach { LocalCache.justConsume(it, null, true) }
                LocalCache.justConsume(event, null, true)
            }
        }
    }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue()) { note ->
            note?.let {
                RenderMusicPlaylist(
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
private fun RenderMusicPlaylistCollaborativePrivatePreview() {
    val event =
        previewPlaylistEvent(
            dTag = "friends-collab",
            title = "Friends Collab",
            description = "Everyone adds their picks",
            tracks = emptyList(),
            isPrivate = true,
            isCollaborative = true,
            content = "",
        )

    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue()) { note ->
            note?.let {
                RenderMusicPlaylist(
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
private fun MusicPlaylistCoverPreview() {
    val event =
        previewPlaylistEvent(
            dTag = "cover-only",
            title = "Cover Only",
            description = "",
            tracks = emptyList(),
            image = null,
        )

    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue()) { note ->
            note?.let {
                MusicPlaylistCover(
                    image = null,
                    note = it,
                    trackCount = 7,
                    accountViewModel = mockAccountViewModel(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun MissingPlaylistTrackRowPreview() {
    ThemeComparisonColumn {
        MissingPlaylistTrackRow(position = 4)
    }
}

@Preview
@Composable
private fun TrackCoverPlaceholderPreview() {
    ThemeComparisonColumn {
        Row(modifier = Modifier.padding(8.dp)) {
            TrackCoverPlaceholder()
        }
    }
}

@Preview
@Composable
private fun PlaylistTagPreview() {
    ThemeComparisonColumn {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistTag(text = "Collaborative")
            PlaylistTag(text = "Private")
        }
    }
}
