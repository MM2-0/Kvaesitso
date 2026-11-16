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
package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.runtime.BroadcastFrameClock
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisappearingBarStateTest {
    private fun state(
        topLimit: Float = 100f,
        bottomLimit: Float = 50f,
    ) = DisappearingBarState().apply {
        topHeightLimit = topLimit
        bottomHeightLimit = bottomLimit
    }

    /**
     * Runs [block] (a settle/reset animation) under a manually-stepped frame clock, sampling both
     * offsets after every frame. Returns the highest (closest-to-or-past zero) value each offset
     * reached — a bar overshooting its fully-visible resting edge briefly pushes its offset above 0.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.peakOffsetsDuring(
        state: DisappearingBarState,
        block: suspend () -> Unit,
    ): Pair<Float, Float> {
        val clock = BroadcastFrameClock()
        var maxTop = state.topHeightOffset
        var maxBottom = state.bottomHeightOffset

        val job = launch(clock) { block() }
        runCurrent()

        var frameNanos = 0L
        val frameStep = 16_000_000L
        // Hard cap so a regression that never settles fails the test instead of hanging it.
        repeat(2_000) {
            if (!job.isActive) return@repeat
            frameNanos += frameStep
            clock.sendFrame(frameNanos)
            runCurrent()
            maxTop = maxOf(maxTop, state.topHeightOffset)
            maxBottom = maxOf(maxBottom, state.bottomHeightOffset)
        }
        assertTrue("animation did not settle within the frame budget", !job.isActive)
        return maxTop to maxBottom
    }

    @Test
    fun `a fast reveal fling settles at the visible edge without overshooting`() =
        runTest {
            val state = state(topLimit = 100f, bottomLimit = 50f)
            // Mid-collapse, as a reveal fling leaves the bars when the list hits the top edge.
            // Settling toward the visible edge (0) with a strong reveal velocity is what made the
            // critically-damped spring shoot past 0 before the fix clamped it.
            state.topHeightOffset = -40f
            state.bottomHeightOffset = -20f

            val (peakTop, peakBottom) =
                peakOffsetsDuring(state) {
                    state.settleToNearestEdge(initialVelocityY = 12000f)
                }

            // Lands exactly on the visible edge...
            assertEquals(0f, state.topHeightOffset, 0.01f)
            assertEquals(0f, state.bottomHeightOffset, 0.01f)
            // ...and never travels past it on the way there (no slide-down-then-back wobble).
            assertTrue("top bar overshot to $peakTop", peakTop <= 0.5f)
            assertTrue("bottom bar overshot to $peakBottom", peakBottom <= 0.5f)
        }

    @Test
    fun `a partial collapse that never reached the hidden edge settles back into view`() =
        runTest {
            // The bar is past the halfway point but was only ever scrolled here from the top — it
            // never reached -limit, so the content hasn't moved a full bar height. Snapping it fully
            // hidden would expose a blank band, so it must settle back to visible instead.
            val state = state(topLimit = 100f, bottomLimit = 50f)
            state.topHeightOffset = -80f

            peakOffsetsDuring(state) { state.settleToNearestEdge() }

            assertEquals(0f, state.topHeightOffset, 0.01f)
        }

    @Test
    fun `a small reveal after fully hiding settles back to hidden, not into view`() =
        runTest {
            // Drive the bar to its hidden edge first (content has now scrolled a full bar height),
            // then nudge it back a little — the small reverse drag a finger makes catching a scroll.
            // Because it genuinely reached the hidden edge, snapping back hidden leaves no gap, so a
            // sub-halfway reveal must not pop the chrome back open.
            val state = state(topLimit = 100f, bottomLimit = 50f)
            state.topHeightOffset = -100f
            state.topHeightOffset = -90f

            peakOffsetsDuring(state) { state.settleToNearestEdge() }

            assertEquals(-100f, state.topHeightOffset, 0.01f)
        }

    @Test
    fun `returning fully into view re-arms the gap guard so the next near-top collapse settles open`() =
        runTest {
            // Hide fully (arms the latch), come all the way back to visible (disarms it), then do a
            // small near-top collapse again. It must settle open, proving the latch resets at 0.
            val state = state(topLimit = 100f, bottomLimit = 50f)
            state.topHeightOffset = -100f
            state.topHeightOffset = 0f
            state.topHeightOffset = -80f

            peakOffsetsDuring(state) { state.settleToNearestEdge() }

            assertEquals(0f, state.topHeightOffset, 0.01f)
        }
}
