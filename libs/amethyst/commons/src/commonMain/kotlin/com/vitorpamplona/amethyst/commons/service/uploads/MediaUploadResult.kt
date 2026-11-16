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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.amethyst.commons.service.image.BlurhashWrapper
import com.vitorpamplona.amethyst.commons.service.image.ThumbhashWrapper
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

data class MediaUploadResult(
    // A publicly accessible URL to the BUD-01 GET /<sha256> endpoint (optionally with a file extension)
    val url: String?,
    // The sha256 hash of the blob
    val sha256: HexKey? = null,
    // The size of the blob in bytes
    val size: Long? = null,
    // (optional) The MIME type of the blob
    val type: String? = null,
    // upload time
    val uploaded: Long? = null,
    // dimensions
    val dimension: DimensionTag? = null,
    // magnet link
    val magnet: String? = null,
    // info hash
    val infohash: String? = null,
    // ipfs link
    val ipfs: String? = null,
    // blurhash value for previews
    val blurHash: BlurhashWrapper? = null,
    // thumbhash value for previews (preferred over blurhash in the UI)
    val thumbHash: ThumbhashWrapper? = null,
) {
    fun mergeLocalMetadata(localMetadata: PreviewHashes?): MediaUploadResult =
        localMetadata?.let { hashes ->
            copy(
                dimension = hashes.dim ?: dimension,
                blurHash = hashes.blurhash ?: blurHash,
                thumbHash = hashes.thumbhash ?: thumbHash,
            )
        } ?: this
}
