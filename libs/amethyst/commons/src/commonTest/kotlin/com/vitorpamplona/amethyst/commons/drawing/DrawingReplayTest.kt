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
package com.vitorpamplona.amethyst.commons.drawing

import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay.Companion.FRAME_MS
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay.Companion.HOLD_MS
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay.Companion.LEAD_MS
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay.Companion.MAX_GAP_MS
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay.Companion.MAX_PAUSE_MS
import com.vitorpamplona.amethyst.commons.drawing.DrawingReplay.Companion.TARGET_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawingReplayTest {
    private fun stroke(vararg timesMs: Long) =
        DrawingStroke(DrawingTool.BRUSH, 0xFF000000.toInt(), 0.02f, false, 1).apply {
            timesMs.forEachIndexed { i, t -> add(i * 0.01f, 0.5f, t, DrawingStroke.NO_PRESSURE) }
        }

    @Test
    fun keepsTheHandsSpeedButCutsLongPausesAndGaps() {
        // 100 ms of drawing, a 5 s pause mid-stroke, then a 10 s break before the next stroke.
        val first = stroke(1_000, 1_050, 1_100, 6_100)
        val second = stroke(16_100, 16_120)
        val replay = DrawingReplay(listOf(first, second))

        assertEquals(LEAD_MS + 100 + MAX_PAUSE_MS + MAX_GAP_MS + 20, replay.endMs)
        // At the end of the first stroke's quick part, three points are down and the paused one is not.
        assertEquals(3, replay.visiblePoints(0, LEAD_MS + 100))
        assertEquals(4, replay.visiblePoints(0, LEAD_MS + 100 + MAX_PAUSE_MS))
        assertEquals(0, replay.visiblePoints(1, LEAD_MS + 100 + MAX_PAUSE_MS))
        assertEquals(2, replay.visiblePoints(1, replay.endMs))
    }

    @Test
    fun opensOnBlankPaperAndEndsOnTheFinishedDrawing() {
        val replay = DrawingReplay(listOf(stroke(0, 200, 400, 600)))
        assertEquals(0L, replay.frameTimes.first())
        assertEquals(0, replay.visiblePoints(0, replay.frameTimes.first()))
        assertEquals(replay.endMs, replay.frameTimes.last())
        assertEquals(4, replay.visiblePoints(0, replay.frameTimes.last()))
        assertEquals((LEAD_MS / 10).toInt(), replay.frameDelayCs(0))
        assertEquals(((FRAME_MS + HOLD_MS) / 10).toInt(), replay.frameDelayCs(replay.frameTimes.lastIndex))
    }

    @Test
    fun aLongDrawingIsSpedUpToFitTheTarget() {
        // Two minutes of continuous drawing, a point every 20 ms.
        val long = stroke(*LongArray(6_000) { it * 20L })
        val replay = DrawingReplay(listOf(long))

        assertTrue(replay.frameStepMs > FRAME_MS)
        val playbackMs = (replay.frameTimes.size - 1) * FRAME_MS
        assertTrue(playbackMs in TARGET_MS - FRAME_MS..TARGET_MS + 2 * FRAME_MS, "replay plays for $playbackMs ms")
    }

    @Test
    fun aShortDrawingPlaysInRealTime() {
        val replay = DrawingReplay(listOf(stroke(0, 100, 200, 300, 400, 500)))
        assertEquals(FRAME_MS, replay.frameStepMs)
        assertEquals(1 + 500 / FRAME_MS.toInt(), replay.frameTimes.size)
    }

    @Test
    fun aPreviewShowsTheSameFramesAsTheGifAtTheSameMoment() {
        val replay = DrawingReplay(listOf(stroke(*LongArray(6_000) { it * 20L })))
        // The GIF holds frame k for frameDelayCs(k); playing those delays back must land on frameTimes[k].
        var playback = 0L
        for (k in replay.frameTimes.indices) {
            assertEquals(replay.frameTimes[k], replay.replayTimeAt(playback), "frame $k")
            playback += replay.frameDelayCs(k) * 10L
        }
        assertEquals(replay.endMs, replay.replayTimeAt(replay.durationMs - 1))
    }

    @Test
    fun aSingleTapStillGetsAFrameThatShowsIt() {
        val replay = DrawingReplay(listOf(stroke(1_000)))
        assertEquals(2, replay.frameTimes.size)
        assertEquals(0, replay.visiblePoints(0, replay.frameTimes.first()))
        assertEquals(1, replay.visiblePoints(0, replay.frameTimes.last()))
        assertEquals(replay.endMs, replay.replayTimeAt(replay.durationMs - 1))
    }

    @Test
    fun nothingDrawnIsOneBlankFrame() {
        val replay = DrawingReplay(emptyList())
        assertEquals(1, replay.frameTimes.size)
    }
}
