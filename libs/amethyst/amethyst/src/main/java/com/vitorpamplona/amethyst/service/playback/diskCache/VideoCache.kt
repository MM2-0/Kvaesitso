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

import android.content.Context
import android.os.StatFs
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache.Companion.CACHE_SIZE_MAX_BYTES
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache.Companion.CACHE_SIZE_MIN_BYTES
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache.Companion.CACHE_SIZE_PERCENT
import com.vitorpamplona.quartz.utils.Log
import java.io.File

@Suppress("UnsafeOptInUsageError")
class VideoCache {
    companion object {
        // Target fraction of currently-available disk space.
        private const val CACHE_SIZE_PERCENT = 0.20

        // Hard cap so we never consume more than this even on large devices.
        private const val CACHE_SIZE_MAX_BYTES = 4L * 1024 * 1024 * 1024 // 4 GB

        // Floor so the cache is still useful on low-storage devices.
        private const val CACHE_SIZE_MIN_BYTES = 256L * 1024 * 1024 // 256 MB
    }

    lateinit var exoDatabaseProvider: StandaloneDatabaseProvider
    lateinit var simpleCache: SimpleCache

    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    fun initFileCache(
        context: Context,
        cachePath: File,
    ) {
        exoDatabaseProvider = StandaloneDatabaseProvider(context)

        val cacheSize = calculateCacheSize(cachePath)

        simpleCache =
            SimpleCache(
                cachePath,
                LeastRecentlyUsedCacheEvictor(cacheSize),
                exoDatabaseProvider,
            )
    }

    /**
     * Adaptive cache sizing: target [CACHE_SIZE_PERCENT] of currently-available
     * disk, clamped between [CACHE_SIZE_MIN_BYTES] and [CACHE_SIZE_MAX_BYTES].
     *
     * A Nostr timeline is append-only — users rarely rewatch older videos — so
     * LRU approximates FIFO here. That's actually fine as long as the budget is
     * large enough to cover a useful scroll window. With multi-rendition HLS
     * renditions at ~20-100 MB each, 1 GB held only a handful of videos and
     * evicted anything not visible on screen. 4 GB keeps roughly an order of
     * magnitude more without meaningfully impacting disk pressure on modern
     * devices (Android can reclaim cache dirs when storage gets tight).
     */
    private fun calculateCacheSize(cachePath: File): Long {
        cachePath.mkdirs()
        val statFs = runCatching { StatFs(cachePath.absolutePath) }.getOrNull()
        val availableBytes = statFs?.availableBytes ?: 0L
        val target = (availableBytes * CACHE_SIZE_PERCENT).toLong()
        val cacheSize = target.coerceIn(CACHE_SIZE_MIN_BYTES, CACHE_SIZE_MAX_BYTES)
        Log.d("VideoCache") {
            "VideoCache size: ${cacheSize / (1024 * 1024)} MB " +
                "(available: ${availableBytes / (1024 * 1024)} MB / " +
                "total: ${(statFs?.totalBytes ?: 0L) / (1024 * 1024)} MB)"
        }
        return cacheSize
    }

    // This method should be called when proxy setting changes.
    fun renewCacheFactory(dataSourceFactory: DataSource.Factory) {
        cacheDataSourceFactory =
            CacheDataSource
                .Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(dataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun get(dataSourceFactory: DataSource.Factory): CacheDataSource.Factory {
        // Renews the factory because OkHttpMight have changed.
        renewCacheFactory(dataSourceFactory)

        return cacheDataSourceFactory
    }
}
