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
import com.davotoula.lightcompressor.hls.HlsRenditionSummary
import com.davotoula.lightcompressor.hls.HlsUploaded
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.duration
import com.vitorpamplona.quartz.nip71Video.title
import com.vitorpamplona.quartz.nip71Video.videoIMetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class HlsVideoPublishInput(
    val renditions: List<HlsRenditionSummary>,
    val uploads: Map<String, HlsUploaded<MediaUploadResult>>,
    val masterUrl: String,
    val masterSha256: String?,
    val title: String,
    val description: String,
    val alt: String? = null,
    val durationSeconds: Int? = null,
    val contentWarning: String? = null,
    val dTag: String? = null,
    val createdAt: Long? = null,
    // Poster JPEG URL (e.g. a frame extracted from the source video and uploaded alongside the
    // HLS segments). Threaded into every imeta's `image` property so HLS-unaware UI surfaces
    // (gallery thumbnails, previews) have a still to render — the .m3u8 playlist itself is a
    // text manifest that can't be decoded as an image frame.
    val posterUrl: String? = null,
    // Identical for every rendition since the source frame is the same.
    val blurhash: String? = null,
    val thumbhash: String? = null,
)

sealed class HlsVideoEventTemplate {
    data class Horizontal(
        val template: EventTemplate<AddressableNormalVideoEvent>,
    ) : HlsVideoEventTemplate()

    data class Vertical(
        val template: EventTemplate<AddressableShortVideoEvent>,
    ) : HlsVideoEventTemplate()
}

/**
 * Result of [HlsVideoEventBuilder.build]. Exposes the unsigned NIP-71 [template] plus the
 * master [masterDimension] (largest rendition's WxH) so the kind:1 sibling imeta can carry
 * the same dim as the NIP-71 master imeta.
 */
data class HlsBuiltTemplate(
    val template: HlsVideoEventTemplate,
    val masterDimension: DimensionTag?,
)

/**
 * Assembles a NIP-71 AddressableNormalVideoEvent / AddressableShortVideoEvent template from an HLS upload
 * result. Orientation is decided from the first rendition's width/height: portrait
 * (height > width) selects kind 34236, otherwise 34235.
 *
 * The template carries one `imeta` tag for the master playlist (primary) plus one per rendition
 * so HLS-unaware clients can still pick a specific variant. Every imeta is marked
 * `m application/vnd.apple.mpegurl`. The rendition imeta's `x`/`size` come from the combined
 * fMP4 upload (single-file layout) while the `url` points at the rewritten media playlist.
 *
 * Returns the unsigned template wrapped in a sealed [HlsVideoEventTemplate]; the caller signs
 * via the account's signer and publishes via the relay client.
 */
@OptIn(ExperimentalUuidApi::class)
object HlsVideoEventBuilder {
    fun build(input: HlsVideoPublishInput): HlsBuiltTemplate {
        val firstRendition = input.renditions.firstOrNull()
        val isVertical = firstRendition != null && firstRendition.height > firstRendition.width

        val largest = input.renditions.maxByOrNull { it.width * it.height }
        val masterDimension = largest?.let { DimensionTag(it.width, it.height) }
        val posterImage = input.posterUrl?.let { listOf(it) } ?: emptyList()
        val masterVideoMeta =
            VideoMeta(
                url = input.masterUrl,
                mimeType = HlsContentTypes.HLS_PLAYLIST,
                hash = input.masterSha256,
                dimension = masterDimension,
                alt = input.alt,
                image = posterImage,
                blurhash = input.blurhash,
                thumbhash = input.thumbhash,
            )

        val renditionMetas =
            input.renditions.map { summary ->
                val combinedFilename =
                    summary.combinedFilename
                        ?: "${summary.rendition.resolution.label}.mp4"
                val combinedMetadata = input.uploads[combinedFilename]?.metadata
                val playlistUpload =
                    input.uploads[summary.playlistFilename]
                        ?: error("No upload recorded for media playlist ${summary.playlistFilename}")

                VideoMeta(
                    url = playlistUpload.url,
                    mimeType = HlsContentTypes.HLS_PLAYLIST,
                    hash = combinedMetadata?.sha256,
                    size = combinedMetadata?.size?.toInt(),
                    dimension = DimensionTag(summary.width, summary.height),
                    image = posterImage,
                    blurhash = input.blurhash,
                    thumbhash = input.thumbhash,
                )
            }

        val videoMetas = listOf(masterVideoMeta) + renditionMetas
        val dTag = input.dTag ?: Uuid.random().toString()
        val createdAt = input.createdAt ?: TimeUtils.now()

        val template =
            if (isVertical) {
                HlsVideoEventTemplate.Vertical(
                    AddressableShortVideoEvent.build(input.description, dTag, createdAt) {
                        videoIMetas(videoMetas)
                        title(input.title)
                        input.durationSeconds?.let { duration(it) }
                        input.contentWarning?.let { contentWarning(it) }
                    },
                )
            } else {
                HlsVideoEventTemplate.Horizontal(
                    AddressableNormalVideoEvent.build(input.description, dTag, createdAt) {
                        videoIMetas(videoMetas)
                        title(input.title)
                        input.durationSeconds?.let { duration(it) }
                        input.contentWarning?.let { contentWarning(it) }
                    },
                )
            }

        return HlsBuiltTemplate(template, masterDimension)
    }
}
