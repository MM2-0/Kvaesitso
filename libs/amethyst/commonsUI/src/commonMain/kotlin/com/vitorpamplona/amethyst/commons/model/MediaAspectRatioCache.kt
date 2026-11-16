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
package com.vitorpamplona.amethyst.commons.model

import androidx.collection.LruCache
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

interface MutableMediaAspectRatioCache {
    fun get(url: String): Float?

    fun add(
        url: String,
        width: Int,
        height: Int,
    )

    /** A size a player decoded and displays: it wins over declared and estimated ones. */
    fun addMeasured(
        url: String,
        width: Int,
        height: Int,
    )
}

/**
 * Aspect ratios keyed by media URL, learned from imeta `dim` tags up front or from the decoder once
 * a first frame lands.
 *
 * Entries are snapshot state, so a composable that calls [get] **during composition** recomposes
 * when the real dimensions arrive later. That matters because players and image loaders only report
 * size after the first frame decodes: a caller that sized itself off a plain cache miss would stay
 * wrong for the whole visit and only look right the *next* time the media is opened. Note this only
 * works for reads made in composition — a read from inside `remember { }` is cached by `remember`
 * itself and won't pick the update up.
 *
 * A video player's report is kept apart as *measured* ([addMeasured], [measured]): it is the shape
 * the player draws, so a `dim` that the uploader got wrong (the stored size of an anamorphic video,
 * or one that ignored rotation) must not overwrite it, and video renderers size from it first
 * ([videoRatio]).
 */
object MediaAspectRatioCache : MutableMediaAspectRatioCache {
    private class Entry(
        val ratio: Float,
        val measured: Boolean,
    )

    private val cache = LruCache<String, MutableState<Entry?>>(1000)

    // get-then-put has to be atomic, so the compound op is guarded even though LruCache is itself
    // thread-safe. A miss still stores a slot: that empty slot is what the caller observes until
    // add() fills it in.
    private val lock = KmpLock()

    private fun entry(url: String): MutableState<Entry?> = lock.withLock { cache.get(url) ?: mutableStateOf<Entry?>(null).also { cache.put(url, it) } }

    override fun get(url: String): Float? = entry(url).value?.ratio

    /** The ratio a video player reported for [url], or null before it has played. */
    fun measured(url: String): Float? = entry(url).value?.takeIf { it.measured }?.ratio

    /**
     * The shape to give the video at [url]: what its player measured, else what its event
     * [declared] (imeta `dim`), else any estimate on file (a poster's shape).
     */
    fun videoRatio(
        url: String,
        declared: Float?,
    ): Float? = measured(url) ?: declared ?: get(url)

    // Both sides are checked, not just the divisor: a zero width divides cleanly to 0f, and 0f is
    // just as unusable downstream as a division by zero — `Modifier.aspectRatio` throws on it. A
    // reported size that cannot be laid out is stored as no size at all, leaving the entry empty
    // for a later, better report to fill.
    override fun add(
        url: String,
        width: Int,
        height: Int,
    ) {
        if (width > 0 && height > 1) store(url, width.toFloat() / height.toFloat(), measured = false)
    }

    override fun addMeasured(
        url: String,
        width: Int,
        height: Int,
    ) {
        if (width > 0 && height > 1) store(url, width.toFloat() / height.toFloat(), measured = true)
    }

    /** Stores a ratio a video player reported directly (it may account for non-square pixels). */
    fun addMeasuredRatio(
        url: String,
        ratio: Float,
    ) {
        if (ratio > 0f && ratio.isFinite()) store(url, ratio, measured = true)
    }

    // A declared or estimated size never replaces a measured one.
    private fun store(
        url: String,
        ratio: Float,
        measured: Boolean,
    ) {
        val slot = entry(url)
        lock.withLock {
            if (measured || slot.value?.measured != true) slot.value = Entry(ratio, measured)
        }
    }
}
