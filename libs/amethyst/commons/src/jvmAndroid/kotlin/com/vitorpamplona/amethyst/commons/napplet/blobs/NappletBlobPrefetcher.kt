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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Warms the shared [NappletBlobCache] with a manifest's blobs ahead of launch, so opening a napplet /
 * nSite is instant (every file already verified on disk). Called from the browse/feed cards when they
 * render. Each blob is downloaded once (in-flight + on-disk de-duplicated), verified against the
 * manifest hash, and stored content-addressed — exactly what the on-device host serves.
 */
object NappletBlobPrefetcher {
    private const val MAX_PARALLEL = 5

    // A card runs because it scrolled by, not because the user asked, and its manifest is a
    // stranger's: it may list any number of files and servers. So a card warms only the start of a
    // site (its entry document first) and stops past a byte budget; a file too big for a prefetch is
    // fetched when the site is opened. All cards share one download gate and one budget per window,
    // so a note quoting a dozen sites costs no more than one.
    const val MAX_FILES_PER_CARD = 16
    const val MAX_SERVERS = 4
    const val MAX_PREFETCH_BLOB_BYTES = 4L * 1024 * 1024
    const val MAX_CARD_BYTES = 8L * 1024 * 1024
    const val MAX_WINDOW_BYTES = 64L * 1024 * 1024
    private const val WINDOW_MS = 10L * 60 * 1000

    private val gate = Semaphore(MAX_PARALLEL)
    private val windowStart = AtomicLong(0)
    private val windowBytes = AtomicLong(0)

    // Clearnet and the current Tor port, with room for a change of port mid-launch.
    private const val MAX_CLIENTS = 3

    // The whole cache is walked to trim it: not after every card, at most this often.
    private const val TRIM_INTERVAL_MS = 10L * 60 * 1000

    // De-dupes blobs already being fetched for the same site by another visible card in this process.
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    // One client per route, for the life of the process: cards reuse its connections (over Tor, its
    // circuits and TLS sessions) instead of building a client, pool and dispatcher each.
    private val clients = ConcurrentHashMap<Int, OkHttpClient>()

    private val lastTrim = AtomicLong(0)

    /**
     * Downloads every not-yet-cached blob in [paths] from [servers] (Tor-routed via [proxyPort]) into
     * the site's partition of the cache under [cacheDir]. Suspends on [Dispatchers.IO] and stops,
     * downloads included, when the card scrolls out of composition.
     */
    suspend fun prefetch(
        paths: List<PathTag>,
        servers: List<String>,
        cacheDir: File,
        proxyPort: Int,
        // The site's cache partition: NappletBlobCache.partitionOf, as its launch computes it.
        partition: String,
    ) {
        val allowed = prefetchServers(servers)
        if (paths.isEmpty() || allowed.isEmpty()) return

        withContext(Dispatchers.IO) {
            val cache = NappletBlobCache(NappletBlobCache.dirFor(cacheDir)).forSite(partition)
            val client = clients.getOrPut(proxyPort) { NappletBlobHttp.client(proxyPort, publicOnly = true) }
            // Tor's port changes when it restarts (desktop picks one each time): keep the client of
            // the route in use, not one per port ever seen.
            if (clients.size > MAX_CLIENTS) clients.keys.removeIf { it != proxyPort }
            if (prefetchWith(paths, allowed, cache, client, partition)) trimNowAndThen(cache)
        }
    }

    /** The servers a card may prefetch from: https only (a stranger's note says where), and only the first few. */
    internal fun prefetchServers(servers: List<String>): List<String> = servers.filter { it.toHttpUrlOrNull()?.scheme == "https" }.take(MAX_SERVERS)

    /** The files a card warms: the entry document first, then the rest in manifest order, at most [MAX_FILES_PER_CARD]. */
    internal fun plan(paths: List<PathTag>): List<PathTag> =
        paths
            .sortedByDescending { it.path == "/" || it.path == "/index.html" }
            .distinctBy { it.hash.lowercase() }
            .take(MAX_FILES_PER_CARD)

    /** Whether it downloaded anything. Stops once the card's or the window's byte budget is spent. */
    internal suspend fun prefetchWith(
        paths: List<PathTag>,
        servers: List<String>,
        cache: NappletBlobCache,
        client: OkHttpClient,
        partition: String,
        now: () -> Long = System::currentTimeMillis,
    ): Boolean {
        val cardBytes = AtomicLong(0)
        val downloaded =
            coroutineScope {
                plan(paths)
                    .map { path ->
                        async {
                            gate.withPermit {
                                fetchOne(
                                    path.hash.lowercase(),
                                    servers,
                                    cache,
                                    client,
                                    partition,
                                    hasRoom = { cardBytes.get() < MAX_CARD_BYTES && windowHasRoom(now()) },
                                    onReceived = { bytes ->
                                        cardBytes.addAndGet(bytes)
                                        windowBytes.addAndGet(bytes)
                                    },
                                )
                            }
                        }
                    }.awaitAll()
            }
        return downloaded.any { it }
    }

    private fun windowHasRoom(now: Long): Boolean {
        val start = windowStart.get()
        if (now - start >= WINDOW_MS && windowStart.compareAndSet(start, now)) windowBytes.set(0)
        return windowBytes.get() < MAX_WINDOW_BYTES
    }

    /** Forgets the window's spending (tests). */
    internal fun resetBudget() {
        windowStart.set(0)
        windowBytes.set(0)
    }

    private fun trimNowAndThen(cache: NappletBlobCache) {
        val now = System.currentTimeMillis()
        val last = lastTrim.get()
        if (now - last >= TRIM_INTERVAL_MS && lastTrim.compareAndSet(last, now)) {
            cache.trimToSize(NappletBlobCache.DEFAULT_MAX_BYTES)
        }
    }

    /**
     * Whether it stored [hash]. Every server it tries is a download the budget pays for: [hasRoom] is
     * asked before each, and [onReceived] told what each brought in, kept or not, so a server that
     * answers with junk or too much spends the budget like one that answers right.
     */
    private suspend fun fetchOne(
        hash: String,
        servers: List<String>,
        cache: NappletBlobCache,
        client: OkHttpClient,
        partition: String,
        hasRoom: () -> Boolean,
        onReceived: (Long) -> Unit,
    ): Boolean {
        val key = "$partition|$hash"
        if (cache.has(hash) || !inFlight.add(key)) return false
        try {
            for (url in StaticSiteResolver.candidateUrls(servers, hash)) {
                if (!hasRoom()) return false
                val download = NappletBlobHttp.downloadCounted(client, url, MAX_PREFETCH_BLOB_BYTES)
                onReceived(download.received)
                val bytes = download.bytes ?: continue
                if (StaticSiteResolver.verify(bytes, hash)) {
                    cache.putVerified(hash, bytes)
                    return true
                }
            }
            return false
        } finally {
            inFlight.remove(key)
        }
    }
}
