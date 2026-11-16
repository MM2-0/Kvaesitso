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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.shorts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip71Video.selectVideoTrack
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.BlurhashBackdrop
import com.vitorpamplona.amethyst.commons.ui.components.ContentWarningGate
import com.vitorpamplona.amethyst.commons.ui.components.LocalShortVideo
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.collectContentWarningReasons
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video.UserCardHeader
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent

@Composable
fun VideoCardCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (baseNote.event as? VideoEvent) ?: return
    val backgroundColor = remember { mutableStateOf(Color.Transparent) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Author header row
        UserCardHeader(baseNote, accountViewModel, nav)

        // Image content
        VideoCardImage(baseNote, event, backgroundColor, accountViewModel)

        // Title and content
        VideoCardCaption(baseNote, event, backgroundColor, accountViewModel, nav)

        // Reactions row
        ReactionsRow(
            baseNote = baseNote,
            showReactionDetail = true,
            addPadding = true,
            editState = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun VideoCardImage(
    note: Note,
    event: VideoEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val videoEvent = (note.event as? VideoEvent) ?: return
    val event = (event as? Event) ?: return

    val imeta = remember(videoEvent) { videoEvent.selectVideoTrack() } ?: return
    val isSensitive = remember(note) { event.isSensitiveOrNSFW() }
    val reasons = remember(note) { collectContentWarningReasons(event) }
    // A NIP-71 event asserts its own type, so only an explicit image imeta diverts to the
    // viewer; an unclassifiable one still belongs in the player. See classifyMedia.
    val isImage = remember(note) { RichTextParser.classifyMedia(imeta.url, imeta.mimeType) == MediaContentKind.IMAGE }

    val content by
        remember(note) {
            val description = event.content.ifEmpty { null } ?: imeta.alt ?: event.alt()

            mutableStateOf<BaseMediaContent>(
                if (isImage) {
                    MediaUrlImage(
                        url = imeta.url,
                        description = description,
                        hash = imeta.hash,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = note.toNostrUri(),
                        mimeType = imeta.mimeType,
                        thumbhash = imeta.thumbhash,
                    )
                } else {
                    MediaUrlVideo(
                        url = imeta.url,
                        description = description,
                        hash = imeta.hash,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = note.toNostrUri(),
                        authorName = note.author?.toBestDisplayName(),
                        mimeType = imeta.mimeType,
                        thumbhash = imeta.thumbhash,
                        // The poster the event names (imeta `image`), shown until the video plays.
                        artworkUri = imeta.image.firstOrNull(),
                    )
                },
            )
        }

    // A video's measured shape beats its `dim`, which can be wrong (see MediaAspectRatioCache.videoRatio).
    val ratio = MediaAspectRatioCache.videoRatio(imeta.url, imeta.dimension?.aspectRatioOrNull())

    ContentWarningGate(
        isSensitive = isSensitive,
        reasons = reasons,
        preloadUrls = if (isImage) listOf(imeta.url) else emptyList(),
        accountViewModel = accountViewModel,
        modifier = mediaSizingModifier(ratio, ContentScale.FillWidth),
        backdrop = (imeta.thumbhash ?: imeta.blurhash)?.let { { BlurhashBackdrop(imeta.blurhash, content.description, imeta.thumbhash) } },
    ) {
        CompositionLocalProvider(LocalShortVideo provides (videoEvent is VideoShortEvent || videoEvent is AddressableShortVideoEvent)) {
            ZoomableContentView(
                content = content,
                roundedCorner = false,
                contentScale = ContentScale.FillWidth,
                accountViewModel = accountViewModel,
            )
        }
    }
}

/**
 * The card's title and description. The description goes through [TranslatableRichTextViewer],
 * like a note's body in [com.vitorpamplona.amethyst.commons.ui.note.types.VideoDisplay], so
 * `nostr:` mentions, links, hashtags and custom emoji render, and it translates. Previews stay
 * off: the video is the card's media, and a quoted note or link card under it would bury it.
 */
@Composable
fun VideoCardCaption(
    note: Note,
    videoEvent: VideoEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (videoEvent as? Event) ?: return

    val title = remember(videoEvent) { videoEvent.title() }
    // A description that only repeats the title adds nothing.
    val summary = remember(videoEvent, title) { event.content.ifBlank { null }?.takeIf { it != title } }

    if (title != null || summary != null) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (summary != null) {
                val tags = remember(note) { event.tags.toImmutableListOfLists() }
                val callbackUri = remember(note) { note.toNostrUri() }

                TranslatableRichTextViewer(
                    content = summary,
                    canPreview = false,
                    quotesLeft = 0,
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
