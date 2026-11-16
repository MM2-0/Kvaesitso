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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Scroll-linked connection that hides/reveals the top and bottom bars together.
 *
 * Philosophy: the bars never consume scroll input. They simply ride along with the
 * content — their offset changes at the same rate as the scroll, so the user keeps full
 * control of the list with their finger. This mirrors the behaviour of Twitter, Instagram,
 * Bluesky, etc., where content scrolling is never delayed by the chrome.
 *
 *  - onPostScroll reads `consumed.y + available.y` (the total scroll attempt that entered
 *    the nested-scroll chain) and updates the bar offsets. Using the sum means the bars
 *    also respond to overscroll attempts at the list edges.
 *  - Pure overscroll (`consumed.y == 0`) from the fully-visible state is ignored. This
 *    prevents short, non-scrollable lists from hiding the bars purely on an overscroll
 *    gesture — which would leave blank padding at the top and bottom. Once the bars have
 *    started moving (list is clearly scrollable), edge overscroll keeps affecting them.
 *  - onPostFling snaps a mid-way bar to the nearest edge, using the fling's remaining
 *    velocity as the spring's initial velocity so the settle feels continuous. No velocity
 *    is returned upward to avoid phantom scrolls on parent containers.
 *  - Both hiding and revealing track the finger 1:1. The bar offset must equal the content's
 *    scroll offset so the bar's bottom edge stays glued to the first item's top edge; any
 *    asymmetry (e.g. a damped reveal) leaves the bar lagging behind the content and opens a
 *    blank band between the bar and the first item when the list returns to the top.
 *
 *    Making the reveal a *deliberate* gesture — so the tiny reverse drag a finger makes when it
 *    catches/stops a fast scroll doesn't pop the chrome back — is handled without breaking that
 *    1:1 invariant: a partial reveal that doesn't cross the halfway point is snapped back to the
 *    hidden edge by [DisappearingBarState.settleToNearestEdge] on fling/lift, and the binary OS
 *    status bar is debounced by the show/hide hysteresis in the scaffold.
 */
class DisappearingBarNestedScroll(
    private val state: DisappearingBarState,
    private val canScroll: () -> Boolean,
    private val reverseLayout: Boolean,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!canScroll()) return Offset.Zero
        val totalY = consumed.y + available.y
        // Dead-zone: ignore sub-pixel jitter so the bars (and the binary status-bar toggle that
        // tracks their collapse fraction) don't twitch on scroll noise. Kept symmetric and tiny so
        // it never makes the reveal lag the content enough to open a visible gap.
        if (abs(totalY) < MIN_SCROLL_DELTA) return Offset.Zero

        // If the list did not consume any scroll and the bars are fully visible, treat
        // this as a non-scrollable list and keep the bars in place. Without this, a tiny
        // feed would hide its chrome purely from overscroll gestures, leaving two empty
        // bands at the top and bottom where the bars used to be.
        val isPureOverscroll = consumed.y == 0f
        val barsFullyVisible = state.topHeightOffset == 0f && state.bottomHeightOffset == 0f
        if (isPureOverscroll && barsFullyVisible) return Offset.Zero

        val deltaY = if (reverseLayout) -totalY else totalY
        applyDelta(deltaY)
        // Never consume: the content scrolls freely while the bars slide along.
        return Offset.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        if (canScroll()) {
            val velocityY = if (reverseLayout) -available.y else available.y
            state.settleToNearestEdge(initialVelocityY = velocityY)
        }
        return Velocity.Zero
    }

    private fun applyDelta(deltaY: Float) {
        val topLimit = state.topHeightLimit
        val bottomLimit = state.bottomHeightLimit
        // 1:1 in both directions: the bar offset mirrors the content scroll so the bar stays glued
        // to the first item. Deliberate reveal is enforced on settle, not by damping the delta here.
        state.topHeightOffset = (state.topHeightOffset + deltaY).coerceIn(-topLimit, 0f)
        state.bottomHeightOffset = (state.bottomHeightOffset + deltaY).coerceIn(-bottomLimit, 0f)
    }

    companion object {
        /**
         * Sub-pixel dead-zone: scroll attempts smaller than this are ignored so jitter doesn't nudge
         * the bars. Tiny on purpose — large enough to swallow fractional noise, small enough that the
         * bar offset never measurably lags the content scroll.
         */
        const val MIN_SCROLL_DELTA = 0.5f
    }
}
