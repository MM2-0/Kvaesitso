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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

/**
 * The NIP-92 `imeta` description of an image uploaded to [url]: for a kind 0 `picture` or
 * `banner` (https://github.com/nostr-protocol/nips/pull/2494) or a NIP-68 picture post.
 *
 * Every field comes from [UploadResult.metadata], computed on the bytes that left this machine,
 * so hash, size, dimensions and blurhash all describe the same file.
 */
fun UploadResult.toPictureMeta(url: String): PictureMeta =
    PictureMeta(
        url = url,
        mimeType = metadata.mimeType,
        hash = metadata.sha256,
        size = metadata.size.takeIf { it in 1..Int.MAX_VALUE }?.toInt(),
        dimension =
            if (metadata.width != null && metadata.height != null && metadata.width > 0 && metadata.height > 0) {
                DimensionTag(metadata.width, metadata.height)
            } else {
                null
            },
        blurhash = metadata.blurhash,
        thumbhash = metadata.thumbhash,
    )
