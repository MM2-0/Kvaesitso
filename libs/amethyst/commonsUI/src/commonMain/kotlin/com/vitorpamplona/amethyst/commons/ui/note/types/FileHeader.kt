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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.BleedingMedia
import com.vitorpamplona.amethyst.commons.ui.components.FileAttachmentCard
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.note.WebxdcCard
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent

@Composable
fun FileMetadataDisplay(
    note: Note,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    val event = (note.event as? FileMetadataEvent) ?: return
    val fullUrl = event.url() ?: return
    val mimeType = remember(note) { event.mimeType() }
    val content = remember(note) { event.toMediaContent(note, fullUrl, mimeType) }

    // The sensitivity gate wraps both branches: a content warning is about the file, not about
    // which viewer happens to render it, so an NSFW-tagged archive stays behind the same gate.
    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        if (content == null) {
            FileMetadataAttachmentCard(event, fullUrl, mimeType)
        } else {
            BleedingMedia(roundedCorner = roundedCorner, enabled = content !is MediaUrlPdf) { rounded ->
                ZoomableContentView(
                    content = content,
                    roundedCorner = rounded,
                    contentScale = contentScale,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

/**
 * Builds the viewer for a kind-1063 header, or **null** when no viewer can show the blob.
 *
 * Kind 1063 is a *generic* file container — its `m` tag can name any type, so unlike a NIP-71
 * video event the kind itself asserts nothing about how to render the payload. A null here means
 * the file belongs in [FileMetadataAttachmentCard] rather than being pushed into the video player.
 */
fun FileMetadataEvent.toMediaContent(
    note: Note,
    url: String,
    mimeType: String?,
): BaseMediaContent? {
    val blurHash = blurhash()
    val thumbHash = thumbhash()
    val hash = hash()
    val dimensions = dimensions()
    val description = fileDescription()
    val uri = note.toNostrUri()

    return when (RichTextParser.classifyMedia(url, mimeType)) {
        MediaContentKind.IMAGE ->
            MediaUrlImage(
                url = url,
                description = description,
                hash = hash,
                blurhash = blurHash,
                dim = dimensions,
                uri = uri,
                mimeType = mimeType,
                thumbhash = thumbHash,
            )

        MediaContentKind.VIDEO ->
            MediaUrlVideo(
                url = url,
                description = description,
                hash = hash,
                blurhash = blurHash,
                dim = dimensions,
                uri = uri,
                authorName = note.author?.toBestDisplayName(),
                mimeType = mimeType,
                thumbhash = thumbHash,
            )

        MediaContentKind.PDF ->
            MediaUrlPdf(
                url = url,
                description = description,
                hash = hash,
                blurhash = blurHash,
                dim = dimensions,
                uri = uri,
                mimeType = mimeType,
                thumbhash = thumbHash,
            )

        null -> null
    }
}

/** The link card a kind-1063 header falls back to when [toMediaContent] returns null. */
@Composable
fun FileMetadataAttachmentCard(
    event: FileMetadataEvent,
    url: String,
    mimeType: String?,
) {
    val webxdc = remember(event, url) { WebxdcApp.from(url) { name -> event.tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1) } }
    if (webxdc != null) {
        WebxdcCard(webxdc, event.id, event.pubKey)
        return
    }

    val description = remember(event) { event.fileDescription() }
    val sizeInBytes = remember(event) { event.size()?.toLong() }

    FileAttachmentCard(
        url = url,
        description = description,
        mimeType = mimeType,
        sizeInBytes = sizeInBytes,
    )
}

/** The human-facing name of the file: NIP-94 `content` when present, else the `alt` tag. */
private fun FileMetadataEvent.fileDescription(): String? = content.ifEmpty { null } ?: alt()
