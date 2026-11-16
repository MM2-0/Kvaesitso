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

/**
 * Plays a clip's [range] (milliseconds) the way drss.io's clip player does: once the player has
 * started in the range, it is paused at the range's end. The watch ends there, at a pause, and at
 * a seek out of the range, so the player is the listener's again.
 */
class DrssClipWatch(
    val range: LongRange,
) {
    private var started = false

    enum class Step { KEEP, PAUSE, DONE }

    /** What to do on a playhead sample at [positionMillis]. */
    fun onSample(
        positionMillis: Long,
        isPlaying: Boolean,
    ): Step {
        val inRange = positionMillis >= range.first - TOLERANCE_MS && positionMillis <= range.last + TOLERANCE_MS
        if (!started) {
            // The seek and the play land a moment after they are asked for.
            if (isPlaying && inRange) started = true
            return Step.KEEP
        }
        return when {
            !isPlaying || !inRange -> Step.DONE
            positionMillis >= range.last -> Step.PAUSE
            else -> Step.KEEP
        }
    }

    companion object {
        /** How far a sample may land outside the range and still count as in it: players report about once a second. */
        const val TOLERANCE_MS = 1_500L
    }
}
