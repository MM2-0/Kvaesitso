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
package com.vitorpamplona.amethyst.commons.drss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrssChaptersTest {
    private val notes =
        """
        A talk about cities.

        00:00:00 - Intro
        00:01:08 - On urban and suburban design
        [21:55] Parking
        1:02:03 Outro
        Not a chapter 12:00
        99:99 nope
        """.trimIndent()

    @Test
    fun readsTimestampedLinesInOrder() {
        val chapters = DrssChapters.parse(notes)
        assertEquals(listOf(0L, 68L, 1315L, 3723L), chapters.map { it.seconds })
        assertEquals("On urban and suburban design", chapters[1].title)
        assertEquals("Parking", chapters[2].title)
    }

    @Test
    fun previousRestartsTheChapterUnlessJustStarted() {
        val chapters = DrssChapters.parse(notes)
        assertEquals(68L, DrssChapters.previousStart(chapters, 200))
        assertEquals(0L, DrssChapters.previousStart(chapters, 69))
        assertEquals(0L, DrssChapters.previousStart(chapters, 1))
    }

    @Test
    fun nextGoesToTheFollowingChapterAndStopsAtTheLast() {
        val chapters = DrssChapters.parse(notes)
        assertEquals(1315L, DrssChapters.nextStart(chapters, 68))
        assertNull(DrssChapters.nextStart(chapters, 4000))
    }
}

class DrssClipWatchTest {
    @Test
    fun pausesAtTheEndOnceStarted() {
        val watch = DrssClipWatch(10_000L..20_000L)
        // The sample right after the seek still shows the old position, paused.
        assertEquals(DrssClipWatch.Step.KEEP, watch.onSample(500_000, false))
        assertEquals(DrssClipWatch.Step.KEEP, watch.onSample(10_100, true))
        assertEquals(DrssClipWatch.Step.KEEP, watch.onSample(15_000, true))
        assertEquals(DrssClipWatch.Step.PAUSE, watch.onSample(20_400, true))
    }

    @Test
    fun endsWhenTheListenerPausesOrSeeksAway() {
        val paused = DrssClipWatch(10_000L..20_000L)
        paused.onSample(10_000, true)
        assertEquals(DrssClipWatch.Step.DONE, paused.onSample(12_000, false))

        val seeked = DrssClipWatch(10_000L..20_000L)
        seeked.onSample(10_000, true)
        assertEquals(DrssClipWatch.Step.DONE, seeked.onSample(300_000, true))
    }
}

class DrssListenRatesTest {
    @Test
    fun keepsOnlyOfferedSpeedsAndForgetsTheDefault() {
        val rates = DrssListenRates()
        rates.set("feed", 1.5f)
        rates.set("other", 3f)
        assertEquals(1.5f, rates.of("feed"))
        assertEquals(1f, rates.of("other"))
        rates.set("feed", 1f)
        assertEquals(emptyMap(), rates.flow.value)
    }
}
