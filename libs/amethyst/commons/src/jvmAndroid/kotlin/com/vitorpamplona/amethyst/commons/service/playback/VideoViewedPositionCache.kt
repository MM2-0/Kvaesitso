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
package com.vitorpamplona.amethyst.commons.service.playback

import kotlin.math.abs

interface MutableVideoViewedPositionCache {
    fun get(uri: String): Long?

    fun add(
        uri: String,
        position: Long,
    )
}

/**
 * Where each recently watched video was, in milliseconds, by URL: every player saves its position
 * when it pauses, stops or seeks, and a player that loads one of these videos again seeks back to
 * it, so a video scrolled away from and back to carries on near where it was. Memory only, the
 * last [MAX_ENTRIES] videos. Shared by Android's and the desktop's players.
 */
object VideoViewedPositionCache : MutableVideoViewedPositionCache {
    private const val MAX_ENTRIES = 100

    /** How far a player must be from the saved position before it seeks there (see [shouldRestore]). */
    const val RESTORE_THRESHOLD_MS = 5_000L

    private val cachedPosition =
        object : LinkedHashMap<String, Long>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > MAX_ENTRIES
        }

    override fun add(
        uri: String,
        position: Long,
    ) {
        synchronized(cachedPosition) { cachedPosition[uri] = position }
    }

    override fun get(uri: String): Long? = synchronized(cachedPosition) { cachedPosition[uri] }

    /**
     * Whether a player now at [currentMs] should seek to the [savedMs] it left: only when meaningfully
     * far, so a resume does not re-seek (and flush its buffer) for a position it is already near.
     */
    fun shouldRestore(
        currentMs: Long,
        savedMs: Long,
    ): Boolean = abs(currentMs - savedMs) > RESTORE_THRESHOLD_MS
}
