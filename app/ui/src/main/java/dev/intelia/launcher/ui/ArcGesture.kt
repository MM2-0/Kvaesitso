package dev.intelia.launcher.ui

import kotlin.math.hypot

/** Coordinates relative to LENS center, in dp. No Android side effects. */
internal class ArcGesture(private val centers: List<Pair<Float, Float>?>) {
    var selected: Int? = null
        private set
    private var armed = false
    private var ended = false
    private var previous = 0f to 0f
    fun move(x: Float, y: Float, pointers: Int = 1): Int? {
        if (ended) return null
        if (pointers != 1) { cancel(); return null }
        if (y <= -28f) armed = true
        val available = centers.indices.filter { centers[it] != null }
        val outside = y > -28f || y < -280f || available.none { kotlin.math.abs(x - centers[it]!!.first) <= 96f }
        val direct = available.minByOrNull { centers[it]!!.let { (cx, cy) -> hypot(x - cx, y - cy) } }
            ?.takeIf { centers[it]!!.let { (cx, cy) -> hypot(x - cx, y - cy) <= 38f } }
        // Sampled fast drags can cross the entire icon between two pointer events.
        val crossed = available.minByOrNull { segmentDistance(centers[it]!!, previous, x to y) }
            ?.takeIf { segmentDistance(centers[it]!!, previous, x to y) <= 30f }
        selected = if (!armed || outside) null else direct ?: selected?.takeIf {
            centers[it]?.let { (cx, cy) -> hypot(x - cx, y - cy) <= 150f } == true
        } ?: crossed
        previous = x to y
        return selected
    }
    fun finish(): Int? {
        if (ended) return null
        ended = true
        return selected.also { selected = null }
    }
    fun cancel() { ended = true; selected = null }
}

private fun segmentDistance(point: Pair<Float, Float>, start: Pair<Float, Float>, end: Pair<Float, Float>): Float {
    val dx = end.first - start.first; val dy = end.second - start.second
    val length = dx * dx + dy * dy
    val t = if (length == 0f) 0f else (((point.first - start.first) * dx + (point.second - start.second) * dy) / length).coerceIn(0f, 1f)
    return hypot(point.first - start.first - t * dx, point.second - start.second - t * dy)
}

internal fun lensSwipeSearch(x: Float, y: Float): Boolean = y <= -48f && kotlin.math.abs(x) <= maxOf(72f, -y * .7f)
