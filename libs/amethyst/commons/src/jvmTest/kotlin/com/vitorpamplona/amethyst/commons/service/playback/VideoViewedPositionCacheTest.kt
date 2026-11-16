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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoViewedPositionCacheTest {
    @Test
    fun keepsTheLastPositionOfEachVideo() {
        VideoViewedPositionCache.add("https://example.com/a.mp4", 12_000)
        VideoViewedPositionCache.add("https://example.com/a.mp4", 15_500)
        assertEquals(15_500, VideoViewedPositionCache.get("https://example.com/a.mp4"))
        assertNull(VideoViewedPositionCache.get("https://example.com/never-played.mp4"))
    }

    @Test
    fun forgetsTheLeastRecentlyUsedPastAHundredVideos() {
        VideoViewedPositionCache.add("https://example.com/first.mp4", 1_000)
        repeat(100) { VideoViewedPositionCache.add("https://example.com/v$it.mp4", 2_000) }
        assertNull(VideoViewedPositionCache.get("https://example.com/first.mp4"))
        assertEquals(2_000, VideoViewedPositionCache.get("https://example.com/v99.mp4"))
    }

    @Test
    fun restoresOnlyWhenMoreThanFiveSecondsAway() {
        assertFalse(VideoViewedPositionCache.shouldRestore(currentMs = 10_000, savedMs = 14_000))
        assertTrue(VideoViewedPositionCache.shouldRestore(currentMs = 0, savedMs = 30_000))
        assertTrue(VideoViewedPositionCache.shouldRestore(currentMs = 60_000, savedMs = 2_000))
    }
}
