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
package com.vitorpamplona.amethyst.service.playback.diskCache

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpUtil
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Downloads the first [PREFETCH_BYTES] of the videos a feed is about to show into [videoCache], so
 * the player that opens one when its card is composed reads the start from disk instead of waiting
 * on the network for its first bytes and the container header.
 *
 * Only the head: an mp4's header and the buffer a player needs before it starts, not the whole
 * file. [WORKERS] downloads at a time, nearest the viewport first, so it never takes more than that
 * many connections from the video on screen. HLS is never sent here (its playlist and segments are
 * separate requests, and a live playlist must not be cached).
 *
 * Videos routed through Tor go one at a time even with [WORKERS] free: a Tor circuit often carries
 * only 1-2 Mbps, and two heads at once would slow the start of the video on screen, which shares it.
 *
 * A download keeps going when its card reaches the screen: that is when the player needs it, and
 * cancelling there would throw away the bytes just before they are read. Only a feed that leaves
 * (an empty list from every feed) stops the ones in flight.
 */
@OptIn(UnstableApi::class)
class VideoPrefetcher(
    // Read only on the download threads: opening the video cache reads its index from disk, and
    // the prefetcher is first reached from wherever a feed first calls it (the main thread, for a
    // feed that closes before it prefetched anything).
    private val videoCache: () -> VideoCache,
    private val upstream: (useProxy: Boolean) -> DataSource.Factory,
    private val scope: CoroutineScope,
) {
    /** A video to prefetch, and whether it must go through the Tor proxy, as the player would. */
    data class Video(
        val url: String,
        val useProxy: Boolean,
    )

    private val lock = Any()

    // Each feed's wanted list (feed order), keyed by the feed asking. Guarded by [lock].
    private val requests = LinkedHashMap<Any, List<Video>>()

    // URLs already tried, each with the time it may be tried again: never while it is in flight or
    // done, a few minutes after a failure (Tor still connecting, a host blip). Bounded LRU.
    // Guarded by [lock].
    private val attempted =
        object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > MAX_REMEMBERED
        }

    // Downloads in flight. Guarded by [lock].
    private val running = mutableSetOf<Job>()

    // How many of those go through Tor. Guarded by [lock].
    private var runningProxied = 0

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var workers: List<Job>? = null

    /** Replaces [owner]'s wanted list with [videos], nearest first; an empty list withdraws it. */
    fun prefetch(
        owner: Any,
        videos: List<Video>,
    ) {
        synchronized(lock) {
            requests.remove(owner)
            if (videos.isNotEmpty()) requests[owner] = videos
            if (requests.isEmpty()) running.forEach { it.cancel() }
            if (workers == null) workers = List(WORKERS) { scope.launch { work() } }
        }
        // Every worker is waiting on the same conflated channel, so wake one per slot.
        repeat(WORKERS) { wake.trySend(Unit) }
    }

    // The most recent feed's list first. Callers hold [lock].
    private fun wanted(): List<Video> =
        requests.values
            .reversed()
            .flatten()
            .distinctBy { it.url }

    private fun next(): Video? =
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            wanted()
                .firstOrNull { (attempted[it.url] ?: 0L) <= now && (!it.useProxy || runningProxied < MAX_PROXIED) }
                ?.also {
                    attempted[it.url] = Long.MAX_VALUE
                    if (it.useProxy) runningProxied++
                }
        }

    private suspend fun work() {
        for (signal in wake) {
            while (true) {
                val video = next() ?: break
                var failed = false
                val job = scope.launch { failed = !fetchHead(video) }
                synchronized(lock) { running.add(job) }
                job.join()
                synchronized(lock) {
                    running.remove(job)
                    if (video.useProxy) runningProxied--
                    when {
                        // Cancelled because the feed left: let a later visit retry it.
                        job.isCancelled -> attempted.remove(video.url)
                        failed -> attempted[video.url] = SystemClock.elapsedRealtime() + RETRY_FAILED_MS
                    }
                }
            }
        }
    }

    /** Caches the head of [video]; false when the download failed. */
    private suspend fun fetchHead(video: Video): Boolean {
        val cache = videoCache().simpleCache
        // Already there, from an earlier visit or a player. A partial head (a download cut short)
        // goes on: CacheWriter reads only the ranges still missing.
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(video.url))
        val head = if (length == C.LENGTH_UNSET.toLong()) PREFETCH_BYTES else minOf(PREFETCH_BYTES, length)
        if (cache.getCachedBytes(video.url, 0, head) >= head) return true

        val source =
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream(video.useProxy))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
        source.addTransferListener(ContentLengthRecorder(cache, video.url))

        val spec =
            DataSpec
                .Builder()
                .setUri(video.url)
                .setPosition(0)
                .setLength(PREFETCH_BYTES)
                .build()

        val start = SystemClock.elapsedRealtime()
        val writer = CacheWriter(source, spec, null, null)
        try {
            runInterruptible { writer.cache() }
            Log.d(PLAYBACK_DIAG_TAG) { "PREFETCH ${cache.getCachedBytes(video.url, 0, PREFETCH_BYTES)} bytes in ${SystemClock.elapsedRealtime() - start}ms ${video.url}" }
            return true
        } catch (e: CancellationException) {
            writer.cancel()
            throw e
        } catch (e: Exception) {
            Log.d(PLAYBACK_DIAG_TAG) { "PREFETCH failed ${video.url}: ${e.message}" }
            return false
        }
    }

    companion object {
        // An mp4's header plus what a player buffers before it starts (750 ms): a few seconds of
        // the ~0.4-0.7 Mbps clips most feed videos are, and still the start of an 8 Mbps 1080p one.
        const val PREFETCH_BYTES = 512L * 1024

        // Downloads at once. One fell behind a scroll: a head took 0.5-3 s on the feed's hosts.
        private const val WORKERS = 2

        // Of those, through Tor at once.
        private const val MAX_PROXIED = 1

        private const val MAX_REMEMBERED = 500

        private const val RETRY_FAILED_MS = 5 * 60 * 1000L
    }
}

/**
 * Stores the video's full length in [cache] when its server answers, before any byte is cached.
 *
 * [CacheDataSource] only learns a length from a request with no end, and a head is a bounded one, so
 * without this the player opens a prefetched video at an unknown length. Most mp4s play anyway, but
 * one ending in an `mdat` box of size 0 ("to the end of the file") then fails media3's sniffing with
 * `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`, and it keeps failing for as long as the head is cached.
 */
@OptIn(UnstableApi::class)
private class ContentLengthRecorder(
    private val cache: Cache,
    private val key: String,
) : TransferListener {
    override fun onTransferStart(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) {
        val length = fileLength(source.responseHeaders, dataSpec.length)
        if (length == C.LENGTH_UNSET.toLong()) return
        try {
            cache.applyContentMetadataMutations(key, ContentMetadataMutations.setContentLength(ContentMetadataMutations(), length))
        } catch (e: Cache.CacheException) {
            Log.w(PLAYBACK_DIAG_TAG, "PREFETCH could not store the length of $key", e)
        }
    }

    private fun fileLength(
        headers: Map<String, List<String>>,
        requested: Long,
    ): Long {
        val contentRange = headers["Content-Range"]?.firstOrNull()
        if (contentRange != null) return HttpUtil.getDocumentSize(contentRange)

        // More than was asked for can only be the whole file, from a server that ignored the range.
        // Anything else may be a slice from one that left Content-Range out, and a slice stored as
        // the length would end the video there, so it is better left unknown.
        val contentLength = HttpUtil.getContentLength(headers["Content-Length"]?.firstOrNull(), null)
        return if (contentLength > requested) contentLength else C.LENGTH_UNSET.toLong()
    }

    override fun onTransferInitializing(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) = Unit

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int,
    ) = Unit

    override fun onTransferEnd(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) = Unit
}
