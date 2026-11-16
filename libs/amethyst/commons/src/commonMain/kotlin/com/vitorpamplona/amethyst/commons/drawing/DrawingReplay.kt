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

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * When each point of a drawing reappears in its replay, and which frames the
 * replay is cut into.
 *
 * The replay keeps the hand's own speed inside a stroke but cuts the dead time:
 * a pause mid-stroke counts for at most [MAX_PAUSE_MS] and the gap between two
 * strokes for at most [MAX_GAP_MS]. A drawing that still runs longer than
 * [TARGET_MS] is played faster until it fits, so a feed never sits through a
 * minute of someone's sketching. It opens on [LEAD_MS] of blank paper and holds
 * the finished drawing for [HOLD_MS] before it loops.
 */
class DrawingReplay(
    val strokes: List<DrawingStroke>,
) {
    /** For each stroke, the replay time of each of its points, in ms. */
    private val pointTimes: Array<LongArray>

    /** Replay time at which the last point lands. */
    val endMs: Long

    init {
        var clock = LEAD_MS
        var previousEnd: Long? = null
        pointTimes =
            Array(strokes.size) { s ->
                val stroke = strokes[s]
                val times = LongArray(stroke.size)
                if (stroke.size > 0) {
                    previousEnd?.let { clock += (stroke.timeMs(0) - it).coerceIn(0, MAX_GAP_MS) }
                    var last = stroke.timeMs(0)
                    for (i in 0 until stroke.size) {
                        clock += (stroke.timeMs(i) - last).coerceIn(0, MAX_PAUSE_MS)
                        times[i] = clock
                        last = stroke.timeMs(i)
                    }
                    previousEnd = last
                }
                times
            }
        endMs = clock
    }

    /** Replay time between two frames: [FRAME_MS], stretched when the drawing is long. */
    val frameStepMs: Long = max(FRAME_MS, ((endMs - LEAD_MS) * FRAME_MS / TARGET_MS))

    /**
     * The replay times to render, one per frame: blank paper first, then every
     * [frameStepMs] until the last point, which always gets a frame of its own.
     */
    val frameTimes: LongArray =
        run {
            // A drawing of single taps lands every point at LEAD_MS: it still needs one frame to show them.
            val drawingFrames =
                when {
                    strokes.none { it.size > 0 } -> 0
                    endMs <= LEAD_MS -> 1
                    else -> ceil((endMs - LEAD_MS).toDouble() / frameStepMs).toInt()
                }
            LongArray(drawingFrames + 1) { k -> if (k == 0) 0 else min(LEAD_MS + k * frameStepMs, endMs) }
        }

    /**
     * How long each frame of [frameTimes] stays on screen, in GIF centiseconds:
     * the lead-in, then [FRAME_MS] each, and the last one holds for [HOLD_MS].
     * Always in real playback time, even when the replay was sped up.
     */
    fun frameDelayCs(frame: Int): Int {
        val base = if (frame == 0) LEAD_MS else FRAME_MS
        val hold = if (frame == frameTimes.lastIndex) HOLD_MS else 0
        return ((base + hold) / 10.0).roundToInt()
    }

    /** How many points of stroke [stroke] are on the paper at replay time [timeMs]. */
    fun visiblePoints(
        stroke: Int,
        timeMs: Long,
    ): Int {
        val times = pointTimes[stroke]
        // Binary search for the first point that lands after timeMs.
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= timeMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * The replay time on screen [playbackMs] into a loop, frame for frame what
     * the GIF shows at that moment: blank paper for [LEAD_MS], then one frame
     * every [FRAME_MS], then the finished drawing held until the loop restarts.
     */
    fun replayTimeAt(playbackMs: Long): Long {
        if (playbackMs < LEAD_MS) return frameTimes[0]
        val frame = 1 + ((playbackMs - LEAD_MS) / FRAME_MS).toInt()
        return if (frame >= frameTimes.size) endMs else frameTimes[frame]
    }

    /** Total playback length of one loop, in ms. */
    val durationMs: Long get() = (0 until frameTimes.size).sumOf { frameDelayCs(it) * 10L }

    companion object {
        const val LEAD_MS = 400L
        const val FRAME_MS = 50L
        const val TARGET_MS = 3200L
        const val HOLD_MS = 2500L
        const val MAX_GAP_MS = 300L
        const val MAX_PAUSE_MS = 150L
    }
}
