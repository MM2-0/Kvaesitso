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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsLivenessCacheTest {
    private val url = "https://nostr.download/abc.m3u8"

    @After
    fun tearDown() {
        HlsLivenessCache.clear()
    }

    @Test
    fun unknownUntilRecorded() {
        assertNull(HlsLivenessCache.verdict(url))
        // Unknown must NOT be treated as on-demand — that is what keeps a live stream from being
        // cached on its first play.
        assertFalse(HlsLivenessCache.isKnownOnDemand(url))
    }

    @Test
    fun recordsOnDemandVerdict() {
        HlsLivenessCache.record(url, isLive = false)
        assertTrue(HlsLivenessCache.isKnownOnDemand(url))
        assertEquals(false, HlsLivenessCache.verdict(url))
    }

    @Test
    fun liveIsNeverTreatedAsOnDemand() {
        HlsLivenessCache.record(url, isLive = true)
        assertFalse(HlsLivenessCache.isKnownOnDemand(url))
        assertEquals(true, HlsLivenessCache.verdict(url))
    }

    @Test
    fun latestVerdictWins() {
        HlsLivenessCache.record(url, isLive = true)
        HlsLivenessCache.record(url, isLive = false)
        assertTrue(HlsLivenessCache.isKnownOnDemand(url))
    }
}
