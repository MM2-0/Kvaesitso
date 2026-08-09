package de.mm20.launcher2.ui.locals

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
