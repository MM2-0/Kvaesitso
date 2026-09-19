package de.mm20.launcher2.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalWindowSize

/**
 * Material window width size classes, per
 * https://m3.material.io/foundations/layout/applying-layout/window-size-classes
 *
 * Used to scale layouts (e.g. grid column counts) on larger windows, such as an
 * unfolded foldable, without introducing a full androidx.window/material3-adaptive
 * dependency for what is currently a single breakpoint decision.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

fun windowWidthClassOf(widthDp: Float): WindowWidthClass {
    return when {
        widthDp < 600f -> WindowWidthClass.Compact
        widthDp < 840f -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }
}

/**
 * Scales a base column count for a given window width class: the configured value is
 * the baseline for compact windows and doubled at medium/expanded width, so that
 * unfolding a foldable device (or otherwise widening the window) shows more items per
 * row.
 *
 * The single source of truth for that scaling factor — anything that needs to predict
 * or display it (e.g. a settings preview) should call this instead of re-deriving it.
 */
fun scaledColumnCount(base: Int, widthClass: WindowWidthClass): Int {
    return when (widthClass) {
        WindowWidthClass.Compact -> base
        WindowWidthClass.Medium, WindowWidthClass.Expanded -> base * 2
    }
}

/**
 * The width class of the current window (really: the current display — see
 * [LocalWindowSize]), e.g. to predict how [scaledColumnCount] will scale a base value
 * here.
 */
@Composable
fun currentWindowWidthClass(): WindowWidthClass {
    val widthDp = with(LocalDensity.current) { LocalWindowSize.current.width.toDp().value }
    return windowWidthClassOf(widthDp)
}

/**
 * The grid column count setting, scaled for the current window width. See
 * [scaledColumnCount].
 *
 * Only intended for grids that span the full window width. Width-capped surfaces
 * (bottom sheets, dialogs, previews) don't grow with the window and should read
 * [LocalGridSettings] directly instead.
 */
@Composable
fun scaledGridColumnCount(): Int {
    val columnCount = LocalGridSettings.current.columnCount
    return scaledColumnCount(columnCount, currentWindowWidthClass())
}
