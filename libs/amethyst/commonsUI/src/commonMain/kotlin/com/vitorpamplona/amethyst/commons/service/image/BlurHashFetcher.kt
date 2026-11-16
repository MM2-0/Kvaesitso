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
package com.vitorpamplona.amethyst.commons.service.image

import androidx.compose.runtime.Stable
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.blurhash.BlurHashDecoder

@Stable
class BlurHashFetcher(
    private val options: Options,
    private val data: BlurhashWrapper,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val hash = data.blurhash

        val platformImage = BlurHashDecoder.decodeKeepAspectRatio(hash, 25) ?: return null

        return ImageFetchResult(
            image = platformImage.toCoilImage(),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    object Factory : Fetcher.Factory<BlurhashWrapper> {
        override fun create(
            data: BlurhashWrapper,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = BlurHashFetcher(options, data)
    }

    object BKeyer : Keyer<BlurhashWrapper> {
        override fun key(
            data: BlurhashWrapper,
            options: Options,
        ): String = data.blurhash
    }
}
