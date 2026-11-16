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
package com.vitorpamplona.amethyst.service.images

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.network.CacheStrategy
import coil3.network.ConcurrentRequestStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.http.LocalBlossomCacheRedirectInterceptor.Companion.PROFILE_PICTURE
import com.vitorpamplona.amethyst.commons.service.http.LocalBlossomMediaCallFactory
import com.vitorpamplona.amethyst.commons.service.image.readAuthAware
import com.vitorpamplona.amethyst.commons.service.image.withAuthHeader
import com.vitorpamplona.amethyst.commons.ui.components.ProfilePictureUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coil Fetcher for profile picture thumbnails.
 *
 * Composables pass [ProfilePictureUrl] as the AsyncImage model. Coil routes here by type.
 *
 * - Cache hit: returns a tiny ~5KB JPEG from the thumbnail disk cache.
 * - Cache miss: passes the network result straight through to Coil (zero overhead),
 *   then generates the thumbnail in the background from Coil's disk cache for next time.
 *
 * Full-size display (zoomable dialog) uses the raw URL string, bypassing this fetcher.
 */
class ProfilePictureFetcher(
    private val url: String,
    private val thumbnailCache: ThumbnailDiskCache,
    private val networkFetcher: Fetcher,
    private val diskCacheLazy: Lazy<DiskCache?>,
    private val backgroundScope: CoroutineScope,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        // Fast path: return pre-resized thumbnail (~5KB read + decode)
        val bitmap = thumbnailCache.load(url)
        if (bitmap != null) {
            return ImageFetchResult(
                image = bitmap.asImage(true),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        }

        // Cache miss: let Coil's normal pipeline handle download + decode
        val result =
            try {
                networkFetcher.fetch() ?: return null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return null
            }

        // Generate thumbnail in background from Coil's disk cache for next time
        backgroundScope.launch {
            val diskCache = diskCacheLazy.value ?: return@launch
            diskCache.openSnapshot(url)?.use { snapshot ->
                thumbnailCache.generateFromFile(url, snapshot.data.toFile())
            }
        }

        return result
    }

    @OptIn(ExperimentalCoilApi::class)
    class Factory(
        private val thumbnailCache: ThumbnailDiskCache,
        private val networkClient: (url: String) -> Call.Factory,
        private val backgroundScope: CoroutineScope,
        // Shared with every other network-backed factory on this ImageLoader --
        // see the note in buildAmethystImageLoader(). Avatars repeat constantly
        // down a feed, so this is where a per-request instance cost the most:
        // every row holding the same author's picture downloaded it again.
        concurrentRequestStrategy: ConcurrentRequestStrategy,
        private val readAuth: BlossomReadAuthTokenProvider? = null,
    ) : Fetcher.Factory<ProfilePictureUrl> {
        private val cacheStrategyLazy = lazy { CacheStrategy.DEFAULT }
        private val connectivityCheckerLazy = singleParameterLazy(::ConnectivityChecker)
        private val concurrentRequestStrategyLazy = lazyOf(concurrentRequestStrategy)

        override fun create(
            data: ProfilePictureUrl,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            val diskCacheLazy = lazy { imageLoader.diskCache }

            val netFetcher =
                readAuthAware(data.url, readAuth) { authHeader ->
                    NetworkFetcher(
                        url = data.url,
                        options = options.withAuthHeader(authHeader),
                        // Marked so the local Blossom cache bridge can honour "profile pictures only".
                        networkClient = lazy { LocalBlossomMediaCallFactory(networkClient(data.url), PROFILE_PICTURE).asNetworkClient() },
                        diskCache = diskCacheLazy,
                        cacheStrategy = cacheStrategyLazy,
                        connectivityChecker = lazy { connectivityCheckerLazy.get(options.context) },
                        concurrentRequestStrategy = concurrentRequestStrategyLazy,
                    )
                }.onSystemFileSystem(options.diskCacheKey ?: data.url)

            return ProfilePictureFetcher(
                data.url,
                thumbnailCache,
                netFetcher,
                diskCacheLazy,
                backgroundScope,
            )
        }
    }

    object BKeyer : Keyer<ProfilePictureUrl> {
        override fun key(
            data: ProfilePictureUrl,
            options: Options,
        ): String = "profilepic_thumb_${data.url}"
    }
}
