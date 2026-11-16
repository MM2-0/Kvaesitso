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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarNestedScroll
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DisappearingBarNestedScrollTest {
    private fun state(
        topLimit: Float = 100f,
        bottomLimit: Float = 50f,
    ) = DisappearingBarState().apply {
        topHeightLimit = topLimit
        bottomHeightLimit = bottomLimit
    }

    private fun nsc(
        state: DisappearingBarState,
        canScroll: Boolean = true,
        reverseLayout: Boolean = false,
    ) = DisappearingBarNestedScroll(
        state = state,
        canScroll = { canScroll },
        reverseLayout = reverseLayout,
    )

    @Test
    fun `onPostScroll does not consume any delta`() {
        val state = state()
        val connection = nsc(state)

        val consumed = connection.onPostScroll(Offset(0f, -20f), Offset(0f, 0f), NestedScrollSource.UserInput)

        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `scrolling content up hides both bars by the total delta`() {
        val state = state(topLimit = 100f, bottomLimit = 50f)
        val connection = nsc(state)

        connection.onPostScroll(Offset(0f, -30f), Offset(0f, 0f), NestedScrollSource.UserInput)

        assertEquals(-30f, state.topHeightOffset)
        assertEquals(-30f, state.bottomHeightOffset)
    }

    @Test
    fun `bars clamp at their individual limits`() {
        val state = state(topLimit = 100f, bottomLimit = 50f)
        val connection = nsc(state)

        connection.onPostScroll(Offset(0f, -200f), Offset(0f, 0f), NestedScrollSource.UserInput)

        assertEquals(-100f, state.topHeightOffset)
        assertEquals(-50f, state.bottomHeightOffset)
    }

    @Test
    fun `scrolling content down reveals both bars from a hidden state`() {
        val state = state(topLimit = 100f, bottomLimit = 50f)
        state.topHeightOffset = -100f
        state.bottomHeightOffset = -50f
        val connection = nsc(state)

        // Reveal tracks the finger 1:1, so a 30px drag reveals 30px.
        connection.onPostScroll(Offset(0f, 30f), Offset(0f, 0f), NestedScrollSource.UserInput)

        assertEquals(-70f, state.topHeightOffset)
        assertEquals(-20f, state.bottomHeightOffset)
    }

    @Test
    fun `uses consumed plus available so the bars see the whole scroll attempt`() {
        val state = state()
        state.topHeightOffset = -50f
        state.bottomHeightOffset = -50f
        val connection = nsc(state)

        // The list consumed 20px of a 40px reveal drag; 20 more was left as overscroll.
        // The bars should move by the total 40, not just one of the halves.
        connection.onPostScroll(Offset(0f, 20f), Offset(0f, 20f), NestedScrollSource.UserInput)

        assertEquals(-10f, state.topHeightOffset)
        assertEquals(-10f, state.bottomHeightOffset)
    }

    @Test
    fun `revealing tracks the finger 1 to 1, matching the hide rate so the bar stays glued to content`() {
        // Hiding a 40px drag moves the bars the full 40px...
        val hiding = state(topLimit = 100f, bottomLimit = 100f)
        nsc(hiding).onPostScroll(Offset(0f, -40f), Offset(0f, 0f), NestedScrollSource.UserInput)
        assertEquals(-40f, hiding.topHeightOffset)

        // ...and revealing the same 40px from fully hidden brings back the full 40px, so when the
        // list returns to the top the bar is fully revealed with no blank band beneath it.
        val revealing = state(topLimit = 100f, bottomLimit = 100f)
        revealing.topHeightOffset = -100f
        revealing.bottomHeightOffset = -100f
        nsc(revealing).onPostScroll(Offset(0f, 40f), Offset(0f, 0f), NestedScrollSource.UserInput)
        assertEquals(-60f, revealing.topHeightOffset)
    }

    @Test
    fun `pure overscroll from fully-visible state leaves the bars alone`() {
        // A short list that cannot scroll: the LazyColumn consumes nothing and the whole
        // drag comes through as `available`. We should not hide the bars from this, or we
        // end up with two empty bands where the chrome used to be.
        val state = state()
        val connection = nsc(state)

        connection.onPostScroll(Offset(0f, 0f), Offset(0f, -80f), NestedScrollSource.UserInput)

        assertEquals(0f, state.topHeightOffset)
        assertEquals(0f, state.bottomHeightOffset)
    }

    @Test
    fun `pure overscroll still moves bars once they are already partially hidden`() {
        // Scrollable list that reached an edge: the list stops consuming but the user keeps
        // flinging. Because the bars are already mid-hide we know the list was scrolling,
        // so overscroll keeps feeding the bar motion — matching the "bars ride along"
        // philosophy.
        val state = state(topLimit = 100f, bottomLimit = 50f)
        state.topHeightOffset = -20f
        state.bottomHeightOffset = -20f
        val connection = nsc(state)

        connection.onPostScroll(Offset(0f, 0f), Offset(0f, -40f), NestedScrollSource.UserInput)

        assertEquals(-60f, state.topHeightOffset)
        assertEquals(-50f, state.bottomHeightOffset)
    }

    @Test
    fun `canScroll returning false freezes the bars`() {
        val state = state()
        val connection = nsc(state, canScroll = false)

        connection.onPostScroll(Offset(0f, -100f), Offset(0f, 0f), NestedScrollSource.UserInput)

        assertEquals(0f, state.topHeightOffset)
        assertEquals(0f, state.bottomHeightOffset)
    }

    @Test
    fun `reverseLayout inverts the delta sign`() {
        val state = state()
        val connection = nsc(state, reverseLayout = true)

        // In reverse layout, a positive Y is a "hide" direction
        connection.onPostScroll(Offset(0f, 20f), Offset(0f, 0f), NestedScrollSource.UserInput)

        assertEquals(-20f, state.topHeightOffset)
        assertEquals(-20f, state.bottomHeightOffset)
    }

    @Test
    fun `bar offsets never exceed zero when revealing`() {
        val state = state()
        // Start from a mid-hidden position
        state.topHeightOffset = -10f
        state.bottomHeightOffset = -10f
        val connection = nsc(state)

        connection.onPostScroll(Offset(0f, 100f), Offset(0f, 0f), NestedScrollSource.UserInput)

        assertEquals(0f, state.topHeightOffset)
        assertEquals(0f, state.bottomHeightOffset)
    }

    @Test
    fun `setting topHeightLimit smaller than current offset clamps it in`() {
        val state = state(topLimit = 100f)
        state.topHeightOffset = -80f

        state.topHeightLimit = 40f

        assertEquals(-40f, state.topHeightOffset)
    }

    @Test
    fun `onPostFling swallows all velocity so parents don't get a phantom fling`() =
        runTest {
            val state = state()
            val connection = nsc(state)

            val remaining =
                connection.onPostFling(
                    consumed = Velocity(0f, 1000f),
                    available = Velocity(0f, 200f),
                )

            assertEquals(Velocity.Zero, remaining)
        }
}
