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
package com.vitorpamplona.amethyst.commons.ui.components

/**
 * One drawer-opening swipe on a tab pager, standing in for the drawer's own drag: Material3's
 * `DrawerState` can't be dragged from outside, so [zonedDrawerSwipe] can only call `open()`.
 * The distance dragged towards the drawer adds up (dragging back takes it away again), and the
 * drawer opens once it passes [distanceThresholdPx], or on release after a flick of at least
 * [velocityThresholdPx].
 */
internal class DrawerSwipeCommit(
    private val distanceThresholdPx: Float,
    private val velocityThresholdPx: Float,
) {
    /** The gesture belongs to the drawer: the pager should see none of it. */
    var claimed = false
        private set
    private var dragged = 0f
    private var committed = false

    fun reset() {
        claimed = false
        dragged = 0f
        committed = false
    }

    /** Claims the gesture and adds [dx] (positive towards the drawer); true exactly once, when the drag commits. */
    fun drag(dx: Float): Boolean {
        claimed = true
        if (committed) return false
        dragged = (dragged + dx).coerceAtLeast(0f)
        committed = dragged >= distanceThresholdPx
        return committed
    }

    /** True if the gesture ends in a flick towards the drawer that didn't already commit. */
    fun release(velocityX: Float): Boolean {
        if (committed) return false
        committed = velocityX >= velocityThresholdPx
        return committed
    }
}
