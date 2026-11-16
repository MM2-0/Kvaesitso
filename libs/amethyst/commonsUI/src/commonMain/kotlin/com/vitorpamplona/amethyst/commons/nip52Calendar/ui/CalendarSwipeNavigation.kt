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
package com.vitorpamplona.amethyst.commons.nip52Calendar.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Swipe-to-navigate gesture for calendar surfaces. Horizontal drag past the threshold fires
 * [onSwipeLeft] (next period) or [onSwipeRight] (previous period). The threshold is in pixels
 * — we accumulate raw drag deltas because [detectHorizontalDragGestures]' final-velocity callback
 * isn't surfaced here; a positional threshold gives the user predictable, latch-style behaviour
 * comparable to the previous/next arrows in [CalendarNavigationHeader].
 *
 * The `key` lets a host that swaps state (week → next week, day → next day) restart the gesture
 * detector so a long sequence of partial drags doesn't accumulate across navigations.
 */
fun Modifier.calendarSwipeNavigation(
    key: Any?,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    thresholdPx: Float = 120f,
): Modifier =
    this.pointerInput(key) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onDragEnd = {
                if (totalDrag <= -thresholdPx) {
                    onSwipeLeft()
                } else if (totalDrag >= thresholdPx) {
                    onSwipeRight()
                }
                totalDrag = 0f
            },
            onDragCancel = { totalDrag = 0f },
        ) { _, dragAmount ->
            totalDrag += dragAmount
        }
    }
