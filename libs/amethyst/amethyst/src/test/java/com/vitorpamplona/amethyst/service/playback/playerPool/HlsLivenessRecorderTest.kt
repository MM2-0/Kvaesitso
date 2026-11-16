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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsLivenessRecorderTest {
    @Test
    fun liveWindowRecordsLiveEvenBeforeReady() {
        // Live is recorded from any signal, allowOnDemand irrelevant.
        assertEquals(
            true,
            livenessVerdictToRecord(isLive = true, isDynamic = true, isSeekable = true, hasKnownDuration = false, known = null, allowOnDemand = false),
        )
    }

    @Test
    fun staticWindowBeforeReadyRecordsNothing() {
        // The fox/freespeech case: a geo-blocked stream serves a VOD-shaped placeholder that looks
        // static from a timeline event, but it errors before READY. allowOnDemand=false => record
        // nothing, so it is never learned as cacheable.
        assertNull(
            livenessVerdictToRecord(isLive = false, isDynamic = false, isSeekable = true, hasKnownDuration = true, known = null, allowOnDemand = false),
        )
    }

    @Test
    fun prematureTimelineOfLiveStreamRecordsNothing() {
        // A live stream's early timeline reports isLive=false, no duration, not seekable.
        assertNull(
            livenessVerdictToRecord(isLive = false, isDynamic = false, isSeekable = false, hasKnownDuration = false, known = null, allowOnDemand = true),
        )
    }

    @Test
    fun resolvedLiveWindowRecordsNothingAsOnDemand() {
        // Once resolved, a live window is dynamic — excluded from the on-demand path.
        assertNull(
            livenessVerdictToRecord(isLive = false, isDynamic = true, isSeekable = true, hasKnownDuration = true, known = null, allowOnDemand = true),
        )
    }

    @Test
    fun staticFiniteSeekableWindowAtReadyRecordsOnDemand() {
        assertEquals(
            false,
            livenessVerdictToRecord(isLive = false, isDynamic = false, isSeekable = true, hasKnownDuration = true, known = null, allowOnDemand = true),
        )
    }

    @Test
    fun knownLiveIsNeverDowngraded() {
        // Even if a later window momentarily looks static at READY, a URL already learned live stays live.
        assertNull(
            livenessVerdictToRecord(isLive = false, isDynamic = false, isSeekable = true, hasKnownDuration = true, known = true, allowOnDemand = true),
        )
    }

    @Test
    fun liveVerdictUpgradesAKnownOnDemand() {
        assertEquals(
            true,
            livenessVerdictToRecord(isLive = true, isDynamic = true, isSeekable = true, hasKnownDuration = false, known = false, allowOnDemand = true),
        )
    }
}
