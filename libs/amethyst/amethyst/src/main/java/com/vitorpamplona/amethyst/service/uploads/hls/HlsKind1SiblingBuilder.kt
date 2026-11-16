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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.davotoula.lightcompressor.hls.HlsContentTypes
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.references.reference
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip71Video.blurhash
import com.vitorpamplona.quartz.nip71Video.dims
import com.vitorpamplona.quartz.nip71Video.hash
import com.vitorpamplona.quartz.nip71Video.image
import com.vitorpamplona.quartz.nip71Video.mimeType
import com.vitorpamplona.quartz.nip71Video.thumbhash
import com.vitorpamplona.quartz.nip92IMeta.imeta
import com.vitorpamplona.quartz.nip92IMeta.imetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Builds the kind:1 short-note that's published as a sibling of the NIP-71 video event from
 * Amethyst's HLS publish flow. Receivers that don't speak NIP-71 can still render a rich
 * preview (poster + dim + blurhash/thumbhash) from the kind:1 alone, while NIP-71-aware
 * receivers can hop to the addressable form via the `a` tag.
 *
 * Tag layout:
 * - One `imeta` mirroring the master rendition (url=master m3u8, m=hls, x=master sha256,
 *   dim=master dim, image=poster, blurhash, thumbhash).
 * - One `r` tag with the master m3u8 URL (idiomatic kind:1 reference).
 *
 * Content mirrors the previous "Draft note" layout: `title\n\ndescription\n\nmasterUrl`,
 * blank fields skipped.
 *
 * Intentionally omits an `a` tag back-reference to the NIP-71 event: in practice Amethyst's
 * note renderer prefers the embedded addressable form when an `a` tag is present, and falls
 * back to a placeholder if the relay set hasn't returned it yet — masking the rich imeta we
 * just added. Without `a`, the kind:1 renders as a vanilla rich-imeta video note across
 * clients, which is what users expect from a shared video link.
 */
object HlsKind1SiblingBuilder {
    fun build(
        title: String,
        description: String,
        masterUrl: String,
        masterSha256: String?,
        masterDimension: DimensionTag?,
        posterUrl: String?,
        blurhash: String?,
        thumbhash: String?,
        createdAt: Long? = null,
    ): EventTemplate<TextNoteEvent> {
        val content =
            listOf(title, description, masterUrl)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")

        val masterImeta =
            imetaTagBuilder(masterUrl) {
                mimeType(HlsContentTypes.HLS_PLAYLIST)
                masterSha256?.let { hash(it) }
                masterDimension?.let { dims(it) }
                posterUrl?.let { image(it) }
                blurhash?.let { this.blurhash(it) }
                thumbhash?.let { this.thumbhash(it) }
            }

        return TextNoteEvent.build(content, createdAt ?: TimeUtils.now()) {
            imeta(masterImeta)
            reference(masterUrl)
        }
    }
}
