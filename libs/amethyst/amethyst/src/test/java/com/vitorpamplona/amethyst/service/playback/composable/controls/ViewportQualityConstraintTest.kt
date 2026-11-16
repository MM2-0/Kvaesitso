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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Writing trackSelectionParameters re-runs track selection and, for a pooled controller, crosses
 * an IPC boundary. onSizeChanged fires on layout, including passes that settle on the size the
 * player already has, so the guard is what keeps a scrolling feed from re-selecting tracks for
 * every player on every pass.
 */
class ViewportQualityConstraintTest {
    // The default viewport before anything is measured: media3 stores the "limited by physical
    // display size" default as an unconstrained MAX_VALUE pair, so the first real measurement
    // always has to be pushed.
    private val unset = Int.MAX_VALUE

    @Test
    fun pushesTheFirstMeasurement() {
        assertTrue(needsViewportUpdate(unset, unset, 1080, 1920))
    }

    @Test
    fun skipsALayoutPassThatSettlesOnTheSameSize() {
        assertFalse(needsViewportUpdate(1080, 1920, 1080, 1920))
    }

    @Test
    fun pushesWhenEitherDimensionChanges() {
        assertTrue(needsViewportUpdate(1080, 1920, 1080, 1000))
        assertTrue(needsViewportUpdate(1080, 1920, 540, 1920))
    }

    @Test
    fun ignoresAnUnmeasuredPlayer() {
        // Before layout the box reports zero. Declaring a zero viewport would describe an area no
        // rendition can fill, so the previous constraint stands until a real size arrives.
        assertFalse(needsViewportUpdate(unset, unset, 0, 0))
        assertFalse(needsViewportUpdate(1080, 1920, 1080, 0))
        assertFalse(needsViewportUpdate(1080, 1920, 0, 1920))
    }

    @Test
    fun leavesAnUncappedViewportAlone() {
        // ceiling 0 is the wifi case: the measured size is the viewport.
        assertEquals(1080 to 1920, clampViewportShortSide(1080, 1920, 0))
        // Already inside the cap.
        assertEquals(360 to 640, clampViewportShortSide(360, 640, METERED_MAX_SHORT_SIDE_PX))
    }

    @Test
    fun scalesAMeteredViewportDownByItsShortSide() {
        // A full-width portrait card on a 3x phone: the short side is what the cap addresses, and
        // the aspect has to survive so the viewport still describes the player's shape.
        val (w, h) = clampViewportShortSide(1080, 1920, METERED_MAX_SHORT_SIDE_PX)
        assertEquals(METERED_MAX_SHORT_SIDE_PX, w)
        assertEquals(854, h)

        // Landscape: height is the short side, so that is what gets pinned to the cap.
        val (lw, lh) = clampViewportShortSide(1920, 1080, METERED_MAX_SHORT_SIDE_PX)
        assertEquals(METERED_MAX_SHORT_SIDE_PX, lh)
        assertEquals(854, lw)
    }

    @Test
    fun neverRoundsAMeteredViewportBelowTheCap() {
        // Rounding down here would ask for 479 on the short side and drop a ladder whose rung sits
        // exactly at 480 — the cap is a floor for the chosen rung, not a target to undershoot.
        val (_, h) = clampViewportShortSide(481, 641, METERED_MAX_SHORT_SIDE_PX)
        assertTrue(h >= METERED_MAX_SHORT_SIDE_PX)
    }

    @Test
    fun ignoresAnUnmeasuredSizeWhenClamping() {
        assertEquals(0 to 0, clampViewportShortSide(0, 0, METERED_MAX_SHORT_SIDE_PX))
    }

    @Test
    fun ignoresANegativeSize() {
        assertFalse(needsViewportUpdate(1080, 1920, -1, -1))
    }
}
