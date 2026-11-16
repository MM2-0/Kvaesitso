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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.BlurhashBackdrop
import com.vitorpamplona.amethyst.commons.ui.components.ContentWarningGate
import com.vitorpamplona.amethyst.commons.ui.components.collectContentWarningReasons
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.note.types.FileMetadataAttachmentCard
import com.vitorpamplona.amethyst.commons.ui.note.types.toMediaContent
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent

@Composable
fun FileMetadataCardCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (baseNote.event as? FileMetadataEvent) ?: return
    val backgroundColor = remember { mutableStateOf(Color.Transparent) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Author header row
        UserCardHeader(baseNote, accountViewModel, nav)

        // Image content
        FileMetadataCardImage(baseNote, event, backgroundColor, accountViewModel)

        // Title and content
        FileMetadataCardCaption(event)

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
private fun FileMetadataCardImage(
    note: Note,
    event: FileMetadataEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val fullUrl = event.url() ?: return

    val isSensitive = remember(note) { event.isSensitiveOrNSFW() }
    val reasons = remember(note) { collectContentWarningReasons(event) }
    val mimeType = remember(note) { event.mimeType() }
    val blurHash = remember(note) { event.blurhash() }
    val thumbHash = remember(note) { event.thumbhash() }
    val dimensions = remember(note) { event.dimensions() }

    val content = remember(note) { event.toMediaContent(note, fullUrl, mimeType) }

    // Reachable despite VideoFeedFilter admitting only image/video types: the filter accepts on
    // `urls().any { … }` while this card renders `url()`, the first tag — so a multi-mirror event
    // whose first URL is unrenderable lands here. The gate wraps it for the same reason it wraps
    // the viewer in FileMetadataDisplay: a content warning is about the file, and the card still
    // spells out its filename, alt text, MIME and size. Sizing stays on the gate's defaults
    // (fillMaxWidth, no backdrop) — a link card has no aspect ratio to reserve and no blurhash
    // to show behind it.
    if (content == null) {
        ContentWarningGate(
            isSensitive = isSensitive,
            reasons = reasons,
            preloadUrls = emptyList(),
            accountViewModel = accountViewModel,
        ) {
            FileMetadataAttachmentCard(event, fullUrl, mimeType)
        }
        return
    }

    val isImage = content is MediaUrlImage
    // A video's measured shape beats its `dim`, which can be wrong (see MediaAspectRatioCache.videoRatio).
    val ratio = MediaAspectRatioCache.videoRatio(fullUrl, dimensions?.aspectRatioOrNull())

    ContentWarningGate(
        isSensitive = isSensitive,
        reasons = reasons,
        preloadUrls = if (isImage) listOf(fullUrl) else emptyList(),
        accountViewModel = accountViewModel,
        modifier = mediaSizingModifier(ratio, ContentScale.FillWidth),
        backdrop = (thumbHash ?: blurHash)?.let { { BlurhashBackdrop(blurHash, content.description, thumbHash) } },
    ) {
        ZoomableContentView(
            content = content,
            roundedCorner = false,
            contentScale = ContentScale.FillWidth,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun FileMetadataCardCaption(videoEvent: FileMetadataEvent) {
    val event = (videoEvent as? Event) ?: return

    val title = videoEvent.summary()
    val content = event.content

    if (title != null || content.isNotBlank()) {
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

            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
