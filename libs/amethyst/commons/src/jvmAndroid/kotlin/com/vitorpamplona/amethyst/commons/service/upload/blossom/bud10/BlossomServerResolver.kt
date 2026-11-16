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
package com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.richtext.mimeTypeMap
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.firstNotNullOrNullAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BlossomServerResolver(
    val loggedInUsers: () -> List<HexKey>,
    val blossomServers: (Set<Address>) -> List<Flow<BlossomServersEvent>>,
    val httpClientBuilder: IRoleBasedHttpClientBuilder,
    val useLocalBlossomCache: () -> Boolean = { false },
    val localCacheProbe: LocalBlossomCacheProbe? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val blossomHitCache: ServerHeadCache = ServerHeadCache()
    val uriToUrlCache = LruCache<String, BlossomUriServer>(200)

    // Unresolvable URIs, with when they failed. Without this every retry — each ExoPlayer
    // load attempt blocks a loader thread on it — paid the full resolution timeout again.
    private val missCache = LruCache<String, Long>(200)

    // One resolution per URI at a time: the feed preview, Coil and the player asking for the
    // same `blossom:` URI together share it instead of each firing its own HEADs and relay
    // subscriptions. Runs in [scope] so a caller that goes away doesn't cancel it for the rest.
    private val inFlight = ConcurrentHashMap<String, Deferred<BlossomUriServer?>>()

    class BlossomUriServer(
        val uri: BlossomUri,
        val serverUrl: String,
    )

    fun cachedFindServer(uriStr: String): BlossomUriServer? = uriToUrlCache[uriStr]

    // Bumped by [clearCaches]. A resolution that started before a clear (e.g. it picked the
    // local cache just before the cache went down) must not write its stale answer back.
    private val generation = AtomicInteger(0)

    /** Forgets every resolution, e.g. when the local cache comes up or goes down. */
    fun clearCaches() {
        generation.incrementAndGet()
        uriToUrlCache.evictAll()
        missCache.evictAll()
        blossomHitCache.cache.evictAll()
    }

    suspend fun findServers(uriStr: String): BlossomUriServer? {
        uriToUrlCache[uriStr]?.let { return it }
        missCache[uriStr]?.let { failedAt ->
            if (TimeUtils.nowMillis() - failedAt < MISS_TTL_MS) return null
        }

        val resolution =
            inFlight.computeIfAbsent(uriStr) {
                // Confined to Dispatchers.IO: this is reached from Compose
                // `produceState`/`LaunchedEffect` (RichTextViewer, MarmotGroupIconDisplay),
                // which run on the main dispatcher. The pre-suspension work here —
                // BlossomUri parsing, LruCache lookups, the local-cache probe's client
                // build, and the server-list flow setup — must stay off the UI thread.
                scope.async(Dispatchers.IO) {
                    val startedAt = generation.get()
                    try {
                        val result = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { findServersInner(uriStr) }
                        // Cleared while resolving: answer the waiting callers, remember nothing.
                        if (startedAt == generation.get()) {
                            if (result != null) {
                                uriToUrlCache.put(uriStr, result)
                            } else {
                                missCache.put(uriStr, TimeUtils.nowMillis())
                            }
                        }
                        result
                    } finally {
                        inFlight.remove(uriStr)
                    }
                }
            }

        return resolution.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun findServersInner(uriStr: String): BlossomUriServer? {
        val uri = BlossomUri.parse(uriStr) ?: return null

        val expectedMimeType = mimeTypeMap[uri.extension]

        // Same rule as the HTTP-level bridge, which Tor-routed clients don't carry: media the
        // user sends through Tor must not be handed to the local cache, which would fetch it
        // from the origin outside Tor.
        if (useLocalBlossomCache() && !isTorRouted(uri, expectedMimeType) && localCacheProbe?.isAvailable() == true) {
            return BlossomUriServer(uri, uri.toLocalCacheUrl(LocalBlossomCacheProbe.LOCAL_CACHE_BASE))
        }
        val filename = uri.filename()

        if (uri.servers.isNotEmpty()) {
            // Bounded well under RESOLVE_TIMEOUT_MS so a hint server that drops packets
            // leaves time for the author's own server list below.
            val workingUrl = firstWorkingUrl(uri.servers, filename, expectedMimeType, uri.size, XS_TIMEOUT_MS)
            if (workingUrl != null) {
                return BlossomUriServer(uri, workingUrl)
            }
        }

        val blossomServerConfigNeeded = mutableSetOf<Address>()

        uri.authors.forEach {
            if (it.isValid()) {
                blossomServerConfigNeeded.add(BlossomServersEvent.createAddress(it))
            }
        }

        loggedInUsers().forEach {
            blossomServerConfigNeeded.add(BlossomServersEvent.createAddress(it))
        }

        val flows =
            blossomServers(blossomServerConfigNeeded)
                .map { blossomServerFlow ->
                    blossomServerFlow.transformLatest {
                        val servers = it.servers()
                        if (servers.isNotEmpty()) {
                            firstWorkingUrl(servers, filename, expectedMimeType, uri.size)?.let { serverUrl ->
                                emit(serverUrl)
                            }
                        }
                    }
                }.toTypedArray()

        if (flows.isNotEmpty()) {
            val serverResult = merge(*flows).first()
            return BlossomUriServer(uri, serverResult)
        }

        return null
    }

    private suspend fun firstWorkingUrl(
        servers: List<String>,
        filename: String,
        expectedMimeType: String?,
        expectedSize: Long?,
        timeoutMs: Long = RESOLVE_TIMEOUT_MS,
    ): String? =
        firstNotNullOrNullAsync(servers, timeoutMs) {
            blossomHitCache.urlIfServerHasFile(it, filename, expectedMimeType, expectedSize) { url ->
                client(url, expectedMimeType)
            }
        }

    /** Whether this blob's media type would be fetched through Tor from a regular (clearnet) server. */
    private fun isTorRouted(
        uri: BlossomUri,
        mimeType: String?,
    ): Boolean = client(uri.toServerUrl() ?: "https://blossom.invalid/${uri.filename()}", mimeType).proxy != null

    fun client(
        url: String,
        mimeType: String?,
    ): OkHttpClient =
        when {
            mimeType == null -> httpClientBuilder.okHttpClientForPreview(url)
            mimeType.startsWith("audio/") || mimeType.startsWith("video/") -> httpClientBuilder.okHttpClientForVideo(url)
            mimeType.startsWith("image/") -> httpClientBuilder.okHttpClientForImage(url)
            else -> httpClientBuilder.okHttpClientForPreview(url)
        }

    fun canResolve(scheme: String) = scheme.equals(SCHEME, ignoreCase = true)

    companion object {
        const val SCHEME = "blossom"

        private const val RESOLVE_TIMEOUT_MS = 10_000L
        private const val XS_TIMEOUT_MS = 4_000L
        private const val MISS_TTL_MS = 30_000L
    }
}
