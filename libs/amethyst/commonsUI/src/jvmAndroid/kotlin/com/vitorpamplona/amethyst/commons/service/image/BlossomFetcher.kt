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
import coil3.Uri
import coil3.annotation.ExperimentalCoilApi
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.CacheStrategy
import coil3.network.ConcurrentRequestStrategy
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import com.vitorpamplona.quartz.utils.startsWithIgnoreCase
import okhttp3.Call
import kotlin.coroutines.cancellation.CancellationException

/**
 * Loads a BUD-10 `blossom:` URI: from the disk cache under the blob's sha256 when it is there,
 * otherwise from the server [blossomServerResolver] finds for it.
 */
@Stable
class BlossomFetcher(
    private val options: Options,
    private val data: Uri,
    private val imageLoader: ImageLoader,
    private val blossomServerResolver: () -> BlossomServerResolver,
    private val networkFetcher: (url: String, diskCacheKey: String) -> Fetcher,
) : Fetcher {
    override suspend fun fetch(): FetchResult? =
        try {
            val uri = data.toString()
            val key = options.diskCacheKey ?: diskCacheKey(uri)

            // A blob already on disk is served without resolving: resolution may HEAD
            // servers and wait on relays, and fails outright offline, where the bytes are
            // still sitting in the cache. NetworkFetcher reads the snapshot under [key]
            // before it ever looks at the url.
            val onDisk = options.diskCachePolicy.readEnabled && imageLoader.diskCache?.openSnapshot(key)?.use { true } ?: false
            val fromDisk =
                if (onDisk) {
                    // Evicted between the check and the read: the fetcher would try the
                    // network with the unresolvable `blossom:` uri, so resolve after all.
                    try {
                        networkFetcher(uri, key).fetch()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                } else {
                    null
                }

            fromDisk ?: networkFetcher(blossomServerResolver().findServers(uri)?.serverUrl ?: uri, key).fetch()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        /**
         * Blossom blobs are content-addressed, so the sha256 names the bytes wherever they
         * were served from. Keying on it (instead of the resolved server URL) finds the
         * blob again after a restart, a probe flip or a different server answering.
         */
        fun diskCacheKey(uri: String): String = BlossomUri.parse(uri)?.let { "blossom:${it.sha256}" } ?: uri
    }

    @OptIn(ExperimentalCoilApi::class)
    class Factory(
        val blossomServerResolver: () -> BlossomServerResolver,
        val networkClient: (url: String) -> Call.Factory,
        // Shared with every other network-backed factory on this ImageLoader --
        // see the note in buildAmethystImageLoader(): the de-dupe only works when
        // all fetchers coordinate through the same instance.
        concurrentRequestStrategy: ConcurrentRequestStrategy,
        private val readAuth: BlossomReadAuthTokenProvider? = null,
        // A platform's wrapper around each network fetch (Android re-homes the cached file
        // for its ImageDecoder); the identity elsewhere.
        private val wrapFetcher: (fetcher: Fetcher, diskCacheKey: String) -> Fetcher = { fetcher, _ -> fetcher },
    ) : Fetcher.Factory<Uri> {
        private val cacheStrategyLazy = lazy { CacheStrategy.DEFAULT }
        private val connectivityCheckers = ConnectivityCheckers()

        private val concurrentRequestStrategyLazy = lazyOf(concurrentRequestStrategy)

        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            if (!isApplicable(data)) return null
            // Wrapped per resolved url (not per Factory) because the server the
            // blob actually lives on is only known once the resolver has run.
            return BlossomFetcher(options, data, imageLoader, blossomServerResolver) { url, diskCacheKey ->
                val keyedOptions = options.copy(diskCacheKey = diskCacheKey)
                readAuthAware(url, readAuth) { authHeader ->
                    NetworkFetcher(
                        url = url,
                        options = keyedOptions.withAuthHeader(authHeader),
                        networkClient = lazy { networkClient(url).asNetworkClient() },
                        diskCache = lazy { imageLoader.diskCache },
                        cacheStrategy = cacheStrategyLazy,
                        connectivityChecker = lazy { connectivityCheckers.get(options.context) },
                        concurrentRequestStrategy = concurrentRequestStrategyLazy,
                    )
                }.let { wrapFetcher(it, diskCacheKey) }
            }
        }

        private fun isApplicable(data: Uri): Boolean = data.scheme?.startsWithIgnoreCase("blossom", "BLOSSOM") == true
    }
}
