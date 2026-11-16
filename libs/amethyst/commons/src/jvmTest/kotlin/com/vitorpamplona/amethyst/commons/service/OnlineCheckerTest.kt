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

import com.vitorpamplona.quartz.utils.TimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [OnlineChecker]'s cache predicates — the part that decides, without touching the network, whether a
 * media URL is known-good, known-bad, or unknown.
 *
 * These decide whether the UI shows a player or a "this is offline" placeholder, and a stale entry
 * being trusted is the difference between a video that plays and one that never gets retried. Nothing
 * here had coverage while the object sat in `amethyst/`.
 *
 * The suspend `isOnline` probe is deliberately not exercised: it needs a real OkHttp round trip and
 * `commons` has no MockWebServer, so there is no honest way to drive it from here.
 */
class OnlineCheckerTest {
    private val url = "https://example.com/video.mp4"

    @Before
    fun clearSharedCache() {
        // OnlineChecker is an object, so its LruCache outlives each test.
        OnlineChecker.checkOnlineCache.evictAll()
    }

    private fun seed(
        online: Boolean,
        ageSeconds: Long,
    ) {
        OnlineChecker.checkOnlineCache.put(url, OnlineCheckResult(TimeUtils.now() - ageSeconds, online))
    }

    @Test
    fun anUnknownUrlIsNeitherOnlineNorKnownOffline() {
        assertFalse("nothing cached, so not known online", OnlineChecker.isOnlineCached(url))
        assertFalse("and not known offline either", OnlineChecker.isCachedAndOffline(url))
    }

    @Test
    fun aFreshOnlineEntryReadsOnline() {
        seed(online = true, ageSeconds = 10)

        assertTrue(OnlineChecker.isOnlineCached(url))
        assertFalse("an online entry is not 'cached and offline'", OnlineChecker.isCachedAndOffline(url))
    }

    @Test
    fun aFreshOfflineEntryReadsOffline() {
        seed(online = false, ageSeconds = 10)

        assertTrue(OnlineChecker.isCachedAndOffline(url))
        assertFalse("and must not read as online", OnlineChecker.isOnlineCached(url))
    }

    /**
     * The five-minute TTL in both directions. Trusting a stale *online* entry shows a player for
     * something that has since gone; trusting a stale *offline* one never retries a URL that came back.
     */
    @Test
    fun anEntryOlderThanFiveMinutesIsTrustedForNothing() {
        seed(online = true, ageSeconds = 301)
        assertFalse("a stale online entry is no longer online", OnlineChecker.isOnlineCached(url))

        seed(online = false, ageSeconds = 301)
        assertFalse("and a stale offline entry no longer counts as known-offline", OnlineChecker.isCachedAndOffline(url))
    }

    @Test
    fun anEntryJustInsideFiveMinutesIsStillTrusted() {
        seed(online = true, ageSeconds = 290)

        assertTrue(OnlineChecker.isOnlineCached(url))
    }

    /** Retry is for failures only: dropping a good entry would refetch every URL that already worked. */
    @Test
    fun resetIfOfflineToRetryDropsOnlyTheOfflineEntries() {
        seed(online = false, ageSeconds = 10)
        OnlineChecker.resetIfOfflineToRetry(url)
        assertFalse("the offline entry is gone, so the next check refetches", OnlineChecker.isCachedAndOffline(url))

        seed(online = true, ageSeconds = 10)
        OnlineChecker.resetIfOfflineToRetry(url)
        assertTrue("the online entry survived", OnlineChecker.isOnlineCached(url))
    }

    @Test
    fun aBlankOrNullUrlIsNeverOnline() {
        assertFalse(OnlineChecker.isOnlineCached(null))
        assertFalse(OnlineChecker.isOnlineCached("   "))
        assertFalse(OnlineChecker.isCachedAndOffline(null))
        assertFalse(OnlineChecker.isCachedAndOffline("   "))
    }
}
