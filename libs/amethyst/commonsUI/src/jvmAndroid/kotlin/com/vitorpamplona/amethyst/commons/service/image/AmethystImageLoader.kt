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

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.ConcurrentRequestStrategy
import coil3.network.ConnectivityChecker
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.Options
import coil3.size.Precision
import coil3.util.Logger
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.commons.sno.SnoFetcher
import okhttp3.Call

/**
 * The Coil image loader both apps use: the inline image schemes (base64, blurhash, thumbhash,
 * sno), `blossom:` URIs resolved to the server that holds the blob, and HTTP(S) through
 * [callFactory], so each URL gets its own Tor decision. Network fetches share one
 * [DeDupeConcurrentRequestStrategy] and sign the BUD-01 retry with [readAuth] when a gated
 * Blossom host answers 401.
 *
 * A platform adds what only it has: its decoders ([decoders], registered first, so they win),
 * fetchers for its own data types ([fetchers]), and [wrapNetworkFetcher] around each network
 * fetch (Android re-homes the cached file for its ImageDecoder).
 */
@OptIn(ExperimentalCoilApi::class)
fun buildAmethystImageLoader(
    context: PlatformContext,
    diskCache: () -> DiskCache?,
    memoryCache: () -> MemoryCache?,
    callFactory: (url: String) -> Call.Factory,
    blossomServerResolver: (() -> BlossomServerResolver)?,
    readAuth: BlossomReadAuthTokenProvider?,
    logger: Logger? = null,
    wrapNetworkFetcher: (fetcher: Fetcher, diskCacheKey: String) -> Fetcher = { fetcher, _ -> fetcher },
    decoders: ComponentRegistry.Builder.() -> Unit = {},
    fetchers: ComponentRegistry.Builder.(ConcurrentRequestStrategy) -> Unit = {},
): ImageLoader {
    // ONE strategy for the whole ImageLoader. DeDupeConcurrentRequestStrategy coordinates through
    // a map of in-flight fetches that it owns, so it only works when every fetcher shares the
    // same instance: a fresh one per request never sees anybody else's fetch. Shared by every
    // network-backed factory, so the same key requested through any of them (a feed image and a
    // profile picture with the same URL) collapses onto one download. `blossom:` blobs are keyed
    // by their sha256 instead (see BlossomFetcher.diskCacheKey).
    val concurrentRequests = DeDupeConcurrentRequestStrategy()

    return ImageLoader
        .Builder(context)
        .diskCache(diskCache)
        .memoryCache(memoryCache)
        .precision(Precision.INEXACT)
        .logger(logger)
        .components {
            decoders()
            add(Base64Fetcher.Factory)
            add(BlurHashFetcher.Factory)
            add(SnoFetcher.Factory)
            add(ThumbHashFetcher.Factory)
            blossomServerResolver?.let {
                add(BlossomFetcher.Factory(it, callFactory, concurrentRequests, readAuth, wrapNetworkFetcher))
            }
            fetchers(concurrentRequests)
            add(Base64Fetcher.BKeyer)
            add(BlurHashFetcher.BKeyer)
            add(SnoFetcher.SKeyer)
            add(ThumbHashFetcher.TKeyer)
            add(OkHttpFactory(callFactory, concurrentRequests, readAuth, wrapNetworkFetcher))
        }.build()
}

/**
 * Coil's OkHttp network fetcher, with the client a function of the URL so Tor and non-Tor
 * clients can be used, and the BUD-01 read-auth retry.
 */
@OptIn(ExperimentalCoilApi::class)
class OkHttpFactory(
    val networkClient: (url: String) -> Call.Factory,
    concurrentRequestStrategy: ConcurrentRequestStrategy,
    private val readAuth: BlossomReadAuthTokenProvider? = null,
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

        val url = data.toString()

        // The key the wrapper gets is the one NetworkFetcher derives for the same request
        // (`options.diskCacheKey ?: url`).
        return wrapFetcher(
            readAuthAware(url, readAuth) { authHeader ->
                NetworkFetcher(
                    url = url,
                    options = options.withAuthHeader(authHeader),
                    networkClient = lazy { networkClient(url).asNetworkClient() },
                    diskCache = lazy { imageLoader.diskCache },
                    cacheStrategy = cacheStrategyLazy,
                    connectivityChecker = lazy { connectivityCheckers.get(options.context) },
                    concurrentRequestStrategy = concurrentRequestStrategyLazy,
                )
            },
            options.diskCacheKey ?: url,
        )
    }

    private fun isApplicable(data: Uri): Boolean = data.scheme == "http" || data.scheme == "https"
}

/** One [ConnectivityChecker] per context (there is one app context), not one per fetch. */
@OptIn(ExperimentalCoilApi::class)
internal class ConnectivityCheckers {
    @Volatile private var last: Pair<PlatformContext, ConnectivityChecker>? = null

    fun get(context: PlatformContext): ConnectivityChecker =
        last?.takeIf { it.first === context }?.second
            ?: ConnectivityChecker(context).also { last = context to it }
}
