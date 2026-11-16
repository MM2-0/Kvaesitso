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
package com.vitorpamplona.amethyst.commons.service

import androidx.collection.LruCache
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable data class OnlineCheckResult(
    val timeInSecs: Long,
    val online: Boolean,
)

/**
 * The last five minutes of stream-URL reachability checks. Reading it never touches the network,
 * so feeds can rank by it; the checks that fill it are the platform's ([OnlineChecker] on the JVM).
 */
object OnlineStatusCache {
    val cache = LruCache<String, OnlineCheckResult>(100)

    /** A fresh answer for [url], or null when there is none within five minutes. */
    fun fresh(url: String): OnlineCheckResult? = cache.get(url)?.takeIf { it.timeInSecs > TimeUtils.fiveMinutesAgo() }

    fun record(
        url: String,
        online: Boolean,
    ) {
        cache.put(url, OnlineCheckResult(TimeUtils.now(), online))
    }

    fun isCachedAndOffline(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return fresh(url)?.online == false
    }

    fun isOnlineCached(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return fresh(url)?.online == true
    }

    fun resetIfOfflineToRetry(url: String) {
        val cached = cache.get(url)
        if (cached != null && !cached.online) {
            cache.remove(url)
        }
    }
}
