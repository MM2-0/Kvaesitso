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
package com.vitorpamplona.amethyst.service.playback.playerPool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMediaSourceFactoryRoutingTest {
    @Test
    fun flaggedLiveAlwaysBypasses() {
        // kind:30311 live activity, regardless of anything else.
        assertTrue(shouldBypassCache(isFlaggedLive = true, isHls = true, isKnownOnDemand = false))
        assertTrue(shouldBypassCache(isFlaggedLive = true, isHls = false, isKnownOnDemand = true))
    }

    @Test
    fun progressiveMediaIsCached() {
        // mp4 and friends: never HLS, so never live-ambiguous.
        assertFalse(shouldBypassCache(isFlaggedLive = false, isHls = false, isKnownOnDemand = false))
    }

    @Test
    fun unclassifiedHlsBypasses() {
        // First play of an HLS URL: we have not learned live vs on-demand yet, so bypass (safe —
        // a live stream is never cached even once).
        assertTrue(shouldBypassCache(isFlaggedLive = false, isHls = true, isKnownOnDemand = false))
    }

    @Test
    fun learnedOnDemandHlsIsCached() {
        // Second play of a proven VOD HLS: cache it (static playlist, immutable segments).
        assertFalse(shouldBypassCache(isFlaggedLive = false, isHls = true, isKnownOnDemand = true))
    }
}
