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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tier rule from amethyst/plans/2026-08-31-portrait-sidebar-tier-rule.md:
 *
 *     dock  <=>  widthClass == Expanded  &&  width >= height  &&  height >= 600dp
 *
 * Sizes are window dp, width first. Every row of the spec's Behaviour table appears here.
 */
class ScreenLayoutTest {
    private fun assertStyle(
        expected: NavigationStyle,
        widthDp: Int,
        heightDp: Int,
    ) = assertEquals("${widthDp}x${heightDp}dp", expected, decideNavigationStyle(widthDp, heightDp))

    // ---- Behaviour table ----

    @Test
    fun phonePortraitKeepsTheBottomBar() = assertStyle(NavigationStyle.BOTTOM_BAR, 411, 923)

    @Test
    fun phoneLandscapeNoLongerDocks() = assertStyle(NavigationStyle.NAV_RAIL, 923, 411)

    @Test
    fun reporterTabletPortraitNoLongerDocks() = assertStyle(NavigationStyle.NAV_RAIL, 889, 1422)

    @Test
    fun wideTabletPortraitNoLongerDocks() = assertStyle(NavigationStyle.NAV_RAIL, 1201, 1920)

    @Test
    fun mediumTabletPortraitStillRails() = assertStyle(NavigationStyle.NAV_RAIL, 800, 1280)

    @Test
    fun tabletLandscapeStillDocks() = assertStyle(NavigationStyle.PERMANENT_DRAWER, 1422, 889)

    @Test
    fun mediumTabletLandscapeStillDocks() = assertStyle(NavigationStyle.PERMANENT_DRAWER, 1280, 800)

    @Test
    fun wideButShortLandscapeNoLongerDocks() = assertStyle(NavigationStyle.NAV_RAIL, 1200, 540)

    // ---- Boundaries ----

    @Test
    fun squareWindowAtTheWidthBreakpointDocks() = assertStyle(NavigationStyle.PERMANENT_DRAWER, 840, 840)

    @Test
    fun portraitByOneDpDoesNotDock() = assertStyle(NavigationStyle.NAV_RAIL, 840, 841)

    @Test
    fun exactlyAtTheHeightFloorDocks() = assertStyle(NavigationStyle.PERMANENT_DRAWER, 840, 600)

    @Test
    fun oneDpBelowTheHeightFloorDoesNotDock() = assertStyle(NavigationStyle.NAV_RAIL, 840, 599)

    @Test
    fun oneDpBelowTheWidthBreakpointRails() = assertStyle(NavigationStyle.NAV_RAIL, 839, 600)

    @Test
    fun compactWidthKeepsTheBottomBar() = assertStyle(NavigationStyle.BOTTOM_BAR, 599, 900)

    // The Medium breakpoint is inclusive: exactly 600dp is no longer Compact.
    @Test
    fun exactlyAtTheMediumBreakpointRails() = assertStyle(NavigationStyle.NAV_RAIL, 600, 900)

    // ---- Notification panel ----

    @Test
    fun panelHiddenJustBelowTheThreshold() = assertFalse(hasRoomForNotificationPanel(1199))

    @Test
    fun panelShownExactlyAtTheThreshold() = assertTrue(hasRoomForNotificationPanel(1200))

    /**
     * A landscape-short window: wide enough for the panel, too short for the dock. Rail plus
     * panel is a combination that has never shipped, so assert both halves together.
     */
    @Test
    fun shortLandscapeYieldsRailPlusPanel() {
        assertStyle(NavigationStyle.NAV_RAIL, 1200, 599)
        assertTrue(hasRoomForNotificationPanel(1200))
    }

    // ---- Folded notification strip in the pane fit ----

    @Test
    fun foldedStripReservesItsWidthSoTheCenterKeepsItsMinimum() {
        // Window 1000, drawer dragged to 480, panel folded to its 48dp strip: counting the strip,
        // the drawer must give up the 8dp the strip takes past the center's 480dp minimum.
        val fitted =
            fitPaneWidths(
                windowWidth = 1_000f,
                drawer = 480f,
                notifications = null,
                showDrawer = true,
                showNotifications = false,
                notificationStripWidth = NotificationPanelCollapsedWidth.value,
            )
        assertEquals(472f, fitted.drawer)
        // 1000 - 472 (drawer) - 48 (strip) = 480: the center pane keeps exactly its minimum.
        assertEquals(MIN_CENTER_PANE_DP, 1_000f - fitted.drawer - NotificationPanelCollapsedWidth.value)
    }

    @Test
    fun withoutTheStripTheSameFitNarrowsNothing() {
        // The strip is the only difference from the case above: uncounted, the drawer keeps the
        // full dragged width and the center would silently drop to 432dp.
        val fitted =
            fitPaneWidths(
                windowWidth = 1_000f,
                drawer = 480f,
                notifications = null,
                showDrawer = true,
                showNotifications = false,
            )
        assertEquals(480f, fitted.drawer)
    }
}
