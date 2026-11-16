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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the app shell presents its top-level navigation for the current window size. */
enum class NavigationStyle {
    /** Compact windows (phones): bottom navigation bar + modal drawer. */
    BOTTOM_BAR,

    /**
     * Every non-Compact window that does not dock — portrait tablets and unfolded foldables at
     * any width, plus short landscape windows: a left navigation rail replaces the bottom bar
     * and the drawer stays modal behind the rail's avatar button.
     */
    NAV_RAIL,

    /** Wide, landscape, tall windows (landscape tablets, desktop): the drawer docks permanently on the left. */
    PERMANENT_DRAWER,
}

/**
 * The shell layout decisions for the current window, published once per window size change
 * through [LocalScreenLayout] so every screen, bar and panel agrees on the same tier.
 */
@Immutable
data class ScreenLayoutSpec(
    val navigationStyle: NavigationStyle,
    val hasRoomForNotificationPanel: Boolean,
) {
    /**
     * True on the rail and permanent-drawer tiers. Large screens hide the bottom bar and pin
     * the top/bottom chrome (no disappearing bars on scroll).
     */
    val isLargeScreen: Boolean get() = navigationStyle != NavigationStyle.BOTTOM_BAR

    companion object {
        val Phone = ScreenLayoutSpec(NavigationStyle.BOTTOM_BAR, hasRoomForNotificationPanel = false)
    }
}

val LocalScreenLayout = compositionLocalOf { ScreenLayoutSpec.Phone }

/**
 * Material 3's window width classes: Compact below 600dp, Medium below 840dp, Expanded from
 * there. Inlined rather than taken from `material3-window-size-class`, which only the Android
 * app depends on.
 */
private const val MEDIUM_WIDTH_DP = 600
private const val EXPANDED_WIDTH_DP = 840

/**
 * Minimum window width for the docked notification panel: a leading navigation pane, a
 * readable center pane and the panel ([NotificationPanelWidth]) only coexist comfortably from
 * a landscape-tablet-sized window up. Sized against the widest leading pane, the permanent
 * drawer ([PermanentDrawerWidth]); the rail is narrower, so a railed window that clears this
 * gets a roomier center pane rather than a tighter one.
 */
private const val NOTIFICATION_PANEL_MIN_WINDOW_DP = 1200

/** Material's Compact / Medium / Expanded width buckets, applied to whatever width is measured. */
enum class WidthClass { Compact, Medium, Expanded }

/**
 * Buckets [width] with the same 600/840 dp breakpoints the shell uses for the window. Pass a
 * pane's own width (e.g. `BoxWithConstraints.maxWidth`) to size a screen by the space it gets.
 */
fun widthClassOf(width: Dp): WidthClass =
    when {
        width.value < MEDIUM_WIDTH_DP -> WidthClass.Compact
        width.value < EXPANDED_WIDTH_DP -> WidthClass.Medium
        else -> WidthClass.Expanded
    }

val PermanentDrawerWidth = 300.dp

val NotificationPanelWidth = 360.dp

/** The folded notification panel's strip: just the badged bell, no feed. */
val NotificationPanelCollapsedWidth = 48.dp

/** How far the user can drag the docked drawer, in dp. */
val DrawerWidthRange = 220f..480f

/** How far the user can drag the docked notification panel, in dp. */
val NotificationPanelWidthRange = 280f..640f

/** The least the center pane keeps when the side panes are dragged wide, in dp. */
const val MIN_CENTER_PANE_DP = 480f

/** The side panes' widths as drawn, in dp; 0 for a pane that is not shown. */
data class FittedPaneWidths(
    val drawer: Float,
    val notifications: Float,
)

/**
 * The widths the docked drawer and notification panel are drawn at in a window [windowWidth] dp
 * wide: what the user dragged them to ([drawer], [notifications], null for the default), within
 * their ranges, and narrowed (the panel first) when together they would leave the center pane
 * under [MIN_CENTER_PANE_DP]. The user's choice is kept as is, so a wider window gives it back.
 *
 * [notificationStripWidth] is the folded panel's fixed strip, when it shows instead of the panel:
 * it cannot narrow, so it reserves its room before the panes fit — without it the center pane
 * would drop below its minimum by exactly the strip's width.
 */
fun fitPaneWidths(
    windowWidth: Float,
    drawer: Float?,
    notifications: Float?,
    showDrawer: Boolean,
    showNotifications: Boolean,
    notificationStripWidth: Float = 0f,
): FittedPaneWidths {
    var drawerWidth = if (showDrawer) (drawer ?: PermanentDrawerWidth.value).coerceIn(DrawerWidthRange) else 0f
    var panelWidth = if (showNotifications) (notifications ?: NotificationPanelWidth.value).coerceIn(NotificationPanelWidthRange) else 0f

    var excess = drawerWidth + panelWidth + notificationStripWidth + MIN_CENTER_PANE_DP - windowWidth
    if (excess > 0 && showNotifications) {
        val narrowed = maxOf(NotificationPanelWidthRange.start, panelWidth - excess)
        excess -= panelWidth - narrowed
        panelWidth = narrowed
    }
    if (excess > 0 && showDrawer) {
        drawerWidth = maxOf(DrawerWidthRange.start, drawerWidth - excess)
    }
    return FittedPaneWidths(drawerWidth, panelWidth)
}

/**
 * Maximum width of a screen's content column inside a wide center pane. Every NavHost
 * destination is wrapped in [CappedScreenContent] (via the builders in NavigationEffects),
 * so the whole screen — top bar, tabs, feed, settings rows — shares one centered reading
 * column instead of stretching across the pane. Screens that genuinely need the full pane
 * (Messages' two-pane split, the embedded browser surfaces) opt out at registration.
 */
val FeedContentMaxWidth = 600.dp

/**
 * Minimum window height for the docked drawer. Higher than Material's 480dp Compact/Medium
 * height boundary on purpose: the permanent drawer's own header — banner, avatar, status
 * editor, follower counts — fills most of a ~540dp column before the first navigation row, so
 * below this the rail shows more of the menu than the dock does.
 */
private const val DOCK_MIN_WINDOW_HEIGHT_DP = 600

/**
 * The navigation tier for a window of this shape.
 *
 * The dock is not a width decision. A tablet is past the Expanded breakpoint in both
 * orientations, so keying on width alone pins 300dp of menu open in portrait with no closed
 * state to fall back on (issue #4024). It docks only when the window is wide, landscape, and
 * tall enough for the drawer's own content to be usable; everything else that is not Compact
 * falls through to the rail, which pairs with the existing swipe-in modal drawer.
 *
 * A square window counts as landscape and docks; `Configuration.ORIENTATION_LANDSCAPE`
 * breaks that tie the other way, so the two disagree at exactly width == height.
 */
fun decideNavigationStyle(
    windowWidthDp: Int,
    windowHeightDp: Int,
): NavigationStyle =
    when {
        windowWidthDp >= EXPANDED_WIDTH_DP &&
            windowWidthDp >= windowHeightDp &&
            windowHeightDp >= DOCK_MIN_WINDOW_HEIGHT_DP -> NavigationStyle.PERMANENT_DRAWER
        windowWidthDp >= MEDIUM_WIDTH_DP -> NavigationStyle.NAV_RAIL
        else -> NavigationStyle.BOTTOM_BAR
    }

/**
 * Whether the window is wide enough to dock the notification feed beside the content.
 *
 * Deliberately not keyed on [NavigationStyle]: a wide portrait window now gets the rail, and
 * gating on the dock would strip a panel it has today.
 */
fun hasRoomForNotificationPanel(windowWidthDp: Int): Boolean = windowWidthDp >= NOTIFICATION_PANEL_MIN_WINDOW_DP

/**
 * Centers a destination's content at [FeedContentMaxWidth]. The outer box paints the theme
 * background so the gutters match the screens' own surfaces; on Compact windows the cap is
 * wider than the pane and this is a visual no-op. A wheel over the gutters scrolls the column.
 */
@Composable
fun CappedScreenContent(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .gutterWheelScrollsColumn(FeedContentMaxWidth),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .widthIn(max = FeedContentMaxWidth)
                .fillMaxSize(),
        ) {
            content()
        }
    }
}

/**
 * The layout tier for a window of [windowWidthDp] x [windowHeightDp]. Each front end reads
 * its own window size (Android: `Configuration.screenWidthDp`) and asks here, so the tier
 * rule lives in one place.
 */
@Composable
fun rememberScreenLayoutSpec(
    windowWidthDp: Int,
    windowHeightDp: Int,
): ScreenLayoutSpec =
    remember(windowWidthDp, windowHeightDp) {
        ScreenLayoutSpec(
            navigationStyle = decideNavigationStyle(windowWidthDp, windowHeightDp),
            hasRoomForNotificationPanel = hasRoomForNotificationPanel(windowWidthDp),
        )
    }
