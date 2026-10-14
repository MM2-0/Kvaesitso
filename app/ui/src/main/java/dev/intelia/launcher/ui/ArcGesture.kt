package dev.intelia.launcher.ui

import kotlin.math.hypot

/** Coordinates relative to LENS center, in dp. No Android side effects. */
internal class ArcGesture(private val centers: List<Pair<Float, Float>?>) {
    var selected: Int? = null
        private set
    private var armed = false
    private var ended = false
    fun move(x: Float, y: Float, pointers: Int = 1): Int? {
        if (ended) return null
        if (pointers != 1) { cancel(); return null }
        if (y <= -28f) armed = true
        selected = if (!armed || y > -28f || y < -150f) null else centers.indices
            .filter { centers[it] != null }
            .minByOrNull { index -> centers[index]!!.let { hypot(x - it.first, y - it.second) } }
            ?.takeIf { index -> centers[index]!!.let { hypot(x - it.first, y - it.second) <= 34f } }
        return selected
    }
    fun finish(): Int? {
        if (ended) return null
        ended = true
        return selected.also { selected = null }
    }
    fun cancel() { ended = true; selected = null }
}
