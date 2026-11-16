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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawerSwipeCommitTest {
    private fun commit() = DrawerSwipeCommit(distanceThresholdPx = 100f, velocityThresholdPx = 400f)

    @Test
    fun aSmallDragDoesNotOpen() {
        val c = commit()
        assertFalse(c.drag(1f))
        assertFalse(c.drag(30f))
        assertFalse(c.release(velocityX = 50f))
    }

    @Test
    fun aDragPastTheThresholdOpensOnce() {
        val c = commit()
        assertFalse(c.drag(60f))
        assertTrue(c.drag(50f))
        assertFalse(c.drag(50f))
        assertFalse(c.release(velocityX = 1000f))
    }

    @Test
    fun draggingBackTakesTheDistanceBack() {
        val c = commit()
        c.drag(80f)
        c.drag(-80f)
        assertFalse(c.drag(80f))
        assertFalse(c.release(velocityX = 0f))
    }

    @Test
    fun draggingBackPastTheStartDoesNotBankDistance() {
        val c = commit()
        c.drag(-500f)
        assertTrue(c.drag(100f))
    }

    @Test
    fun anyDragClaimsTheGestureUntilReset() {
        val c = commit()
        assertFalse(c.claimed)
        c.drag(0f)
        assertTrue(c.claimed)
        c.reset()
        assertFalse(c.claimed)
    }

    @Test
    fun aFastFlickOpensOnRelease() {
        val c = commit()
        c.drag(10f)
        assertTrue(c.release(velocityX = 400f))
    }

    @Test
    fun aFlickBackDoesNotOpen() {
        val c = commit()
        c.drag(90f)
        assertFalse(c.release(velocityX = -1000f))
    }

    @Test
    fun resetStartsANewGesture() {
        val c = commit()
        c.drag(200f)
        c.reset()
        assertFalse(c.drag(60f))
        assertTrue(c.drag(40f))
    }
}
