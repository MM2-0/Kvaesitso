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

import androidx.collection.LruCache

/**
 * Remembers, per media URL, whether ExoPlayer determined a stream to be live once it parsed the
 * playlist. This is how the cache tells a live HLS FAST channel from an immutable on-demand HLS
 * rendition — a distinction the `.m3u8` URL alone cannot make.
 *
 * A `.m3u8` cannot be classified before it is fetched, so the very first play of a URL is unknown
 * and routed conservatively to the non-caching source (see CustomMediaSourceFactory). Once the
 * timeline reports liveness (recorded by HlsLivenessRecorder), a *subsequent* play of the same URL
 * routes correctly: a known on-demand stream is cached, a known live stream never is.
 *
 * A URL is permanently one or the other, and the recorder overwrites with the latest verdict on
 * every timeline change, so a transient early value self-corrects. Backed by a bounded, thread-safe
 * [LruCache] (same convention as [com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache]) so a long
 * feed session over many distinct URLs can't grow it without bound — an evicted entry just relearns
 * on its next play, which is one uncached play, the same negligible cost as a process restart.
 */
object HlsLivenessCache {
    // url -> isLive. Absent = not yet learned.
    private val verdicts = LruCache<String, Boolean>(1000)

    fun record(
        url: String,
        isLive: Boolean,
    ) {
        verdicts.put(url, isLive)
    }

    /** True only when we have positively learned this URL is on-demand (safe to cache). */
    fun isKnownOnDemand(url: String): Boolean = verdicts.get(url) == false

    /** The learned verdict, or null if this URL has not been classified yet. */
    fun verdict(url: String): Boolean? = verdicts.get(url)

    fun clear() {
        verdicts.evictAll()
    }
}
