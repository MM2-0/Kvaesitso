package dev.intelia.launcher.ui

import kotlin.math.roundToInt

/** Geometry only. A valid rectangle does not imply touch access above SystemUI. */
internal object PulseAnchorGeometry {
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** User calibration is independent of OEM cutout bounding rectangles. */
    fun calibrated(display: Bounds, x: Float, y: Float, widthDp: Float, heightDp: Float, density: Float): Bounds? {
        if (listOf(x, y, widthDp, heightDp, density).any { !it.isFinite() } ||
            x !in 0f..1f || y !in 0f..1f || density <= 0 || widthDp < 48 || heightDp < 48 ||
            display.width <= 0 || display.height <= 0) return null
        val width = (widthDp * density).roundToInt().coerceAtMost(display.width)
        val height = (heightDp * density).roundToInt().coerceAtMost(display.height / 3)
        if (width < (48 * density).toInt() || height < (48 * density).toInt()) return null
        val left = (display.left + x * display.width - width / 2f).roundToInt().coerceIn(display.left, display.right - width)
        val top = (display.top + y * display.height - height / 2f).roundToInt().coerceIn(display.top, display.bottom - height)
        return Bounds(left, top, left + width, top + height)
    }

    fun anchor(display: Bounds, cutout: Bounds?, statusBottom: Int, target: Int, belowStatus: Boolean): Bounds? {
        if (display.width <= 0 || display.height <= 0 || target <= 0) return null
        val width = minOf(target, display.width)
        val minTop = if (belowStatus) maxOf(display.top, statusBottom) else display.top
        if (display.bottom - minTop < target) return null
        val center = cutout?.let { it.left + it.width / 2 } ?: (display.left + display.width / 2)
        val left = (center - width / 2).coerceIn(display.left, display.right - width)
        val preferredTop = if (belowStatus) minTop else cutout?.top ?: display.top
        val top = preferredTop.coerceIn(minTop, display.bottom - target)
        return Bounds(left, top, left + width, top + target)
    }
}
