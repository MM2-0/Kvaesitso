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
package com.vitorpamplona.amethyst.commons.richtext

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.cartridges.CartLinks
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// URL-based media content models. The locally-cached variants
// (MediaPreloadedContent, MediaLocalImage, MediaLocalVideo) are in MediaLocalContent.kt.

@Immutable
abstract class BaseMediaContent(
    val description: String? = null,
    val dim: DimensionTag? = null,
    val blurhash: String? = null,
    val thumbhash: String? = null,
)

@Immutable
abstract class MediaUrlContent(
    val url: String,
    description: String? = null,
    val hash: String? = null,
    dim: DimensionTag? = null,
    blurhash: String? = null,
    val uri: String? = null,
    mimeType: String? = null,
    thumbhash: String? = null,
    val authorPubKey: String? = null,
    sizeBytes: Long? = null,
) : BaseMediaContent(description, dim, blurhash, thumbhash) {
    /**
     * The file's size in bytes as the event declares it (imeta or URL fragment `size`, or a file
     * message's `size` tag): a held-back placeholder and the audio card show it before anything loads.
     * Zero or less means the uploader didn't know; kept as null so the placeholder asks the host
     * instead of showing "1 KB".
     */
    val sizeBytes: Long? = sizeBytes?.takeIf { it > 0 }

    /**
     * Repaired at construction rather than at each of the eight call sites that build a model from
     * an author-supplied `m` tag — a bare subtype reaching this field is what puts `Intent.type =
     * "jpeg"` on the share sheet (matching no `IntentFilter`) and what republishes the malformed
     * tag under the user's own key when media is added to a gallery. See [normalizeMimeType].
     */
    val mimeType: String? = normalizeMimeType(mimeType)
}

@Immutable
open class MediaUrlImage(
    url: String,
    description: String? = null,
    hash: String? = null,
    blurhash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    val contentWarning: String? = null,
    mimeType: String? = null,
    thumbhash: String? = null,
    authorPubKey: String? = null,
    /** The note's `imeta` tags when they may link game carts: see [CartLinks.forEntry]. */
    val cartImetas: Map<String, IMetaTag>? = null,
    sizeBytes: Long? = null,
) : MediaUrlContent(url, description, hash, dim, blurhash, uri, mimeType, thumbhash, authorPubKey, sizeBytes = sizeBytes)

class EncryptedMediaUrlImage(
    url: String,
    description: String? = null,
    hash: String? = null,
    blurhash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    contentWarning: String? = null,
    mimeType: String? = null,
    val encryptionAlgo: String,
    val encryptionKey: ByteArray,
    val encryptionNonce: ByteArray,
    thumbhash: String? = null,
    authorPubKey: String? = null,
    sizeBytes: Long? = null,
) : MediaUrlImage(url, description, hash, blurhash, dim, uri, contentWarning, mimeType, thumbhash, authorPubKey, sizeBytes = sizeBytes)

@Immutable
open class MediaUrlPdf(
    url: String,
    description: String? = null,
    hash: String? = null,
    blurhash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    mimeType: String? = null,
    thumbhash: String? = null,
    authorPubKey: String? = null,
    sizeBytes: Long? = null,
) : MediaUrlContent(url, description, hash, dim, blurhash, uri, mimeType, thumbhash, authorPubKey, sizeBytes = sizeBytes)

@Immutable
open class MediaUrlVideo(
    url: String,
    description: String? = null,
    hash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    val artworkUri: String? = null,
    val authorName: String? = null,
    blurhash: String? = null,
    val contentWarning: String? = null,
    mimeType: String? = null,
    thumbhash: String? = null,
    val isLiveStream: Boolean = false,
    authorPubKey: String? = null,
    // NIP-71 `text-track` captions resolved for this video, for the player to side-load.
    val captions: ImmutableList<CaptionTrack> = persistentListOf(),
    sizeBytes: Long? = null,
    // The imeta `waveform` (NIP-A0 amplitudes, not normalised): an audio card draws it as its seek bar.
    val waveform: List<Float>? = null,
    // The imeta `duration`, in seconds: an audio card shows it, and offers picture-in-picture for long
    // audio, before the player has probed the file.
    val durationSeconds: Double? = null,
) : MediaUrlContent(url, description, hash, dim, blurhash, uri, mimeType, thumbhash, authorPubKey, sizeBytes = sizeBytes)

@Immutable
class EncryptedMediaUrlVideo(
    url: String,
    description: String? = null,
    hash: String? = null,
    dim: DimensionTag? = null,
    uri: String? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    blurhash: String? = null,
    contentWarning: String? = null,
    mimeType: String? = null,
    val encryptionAlgo: String,
    val encryptionKey: ByteArray,
    val encryptionNonce: ByteArray,
    thumbhash: String? = null,
    authorPubKey: String? = null,
    sizeBytes: Long? = null,
) : MediaUrlVideo(url, description, hash, dim, uri, artworkUri, authorName, blurhash, contentWarning, mimeType, thumbhash, authorPubKey = authorPubKey, sizeBytes = sizeBytes)
