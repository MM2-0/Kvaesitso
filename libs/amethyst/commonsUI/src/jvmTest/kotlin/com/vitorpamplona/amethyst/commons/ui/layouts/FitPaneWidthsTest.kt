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

import org.junit.Assert.assertEquals
import org.junit.Test

class FitPaneWidthsTest {
    @Test
    fun neverDraggedUsesTheDefaults() {
        assertEquals(FittedPaneWidths(300f, 360f), fitPaneWidths(1600f, null, null, showDrawer = true, showNotifications = true))
    }

    @Test
    fun aDraggedWidthIsKeptWhenTheWindowHasRoom() {
        assertEquals(FittedPaneWidths(400f, 500f), fitPaneWidths(1800f, 400f, 500f, showDrawer = true, showNotifications = true))
    }

    @Test
    fun widthsStayWithinTheirRanges() {
        assertEquals(FittedPaneWidths(220f, 640f), fitPaneWidths(3000f, 10f, 2000f, showDrawer = true, showNotifications = true))
    }

    @Test
    fun aNarrowWindowNarrowsThePanelFirstToKeepTheCenter() {
        // 400 + 500 + 480 = 1380: 180 too many for a 1200 window, all taken from the panel.
        assertEquals(FittedPaneWidths(400f, 320f), fitPaneWidths(1200f, 400f, 500f, showDrawer = true, showNotifications = true))
    }

    @Test
    fun thenTheDrawer() {
        // The panel bottoms out at 280; the drawer gives the rest.
        assertEquals(FittedPaneWidths(340f, 280f), fitPaneWidths(1100f, 400f, 500f, showDrawer = true, showNotifications = true))
    }

    @Test
    fun aHiddenPaneTakesNoWidth() {
        assertEquals(FittedPaneWidths(0f, 360f), fitPaneWidths(1300f, 400f, null, showDrawer = false, showNotifications = true))
        assertEquals(FittedPaneWidths(400f, 0f), fitPaneWidths(1300f, 400f, 500f, showDrawer = true, showNotifications = false))
    }
}
