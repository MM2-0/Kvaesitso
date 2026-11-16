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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Shared state for the disappearing top / bottom bar chrome.
 *
 * Both offsets are negative-or-zero. 0 = fully visible; -limit = fully hidden.
 *
 * Limits are updated by the layout pass once it measures the bar slots. The nested-scroll
 * connection reads both limits and offsets to clamp movement to the visible travel range.
 */
@Stable
class DisappearingBarState(
    initialTopHeightOffset: Float = 0f,
    initialBottomHeightOffset: Float = 0f,
) {
    private var _topHeightOffset by mutableFloatStateOf(initialTopHeightOffset)
    private var _bottomHeightOffset by mutableFloatStateOf(initialBottomHeightOffset)

    /**
     * Latches that record whether each bar has been driven all the way to its hidden edge by real
     * scrolling since it was last fully in view. The settle reads them to tell a deliberate hide —
     * where the content has scrolled at least a full bar height, so snapping the bar fully hidden
     * leaves content (not a blank band) in the slot it vacates — apart from a small near-top
     * collapse, where snapping hidden would expose the background because the content hasn't
     * scrolled far enough to fill the bar's slot.
     */
    private var topReachedHiddenEdge = false
    private var bottomReachedHiddenEdge = false

    var topHeightOffset: Float
        get() = _topHeightOffset
        set(value) {
            _topHeightOffset = value
            updateLatch(value, topHeightLimit) { topReachedHiddenEdge = it }
        }

    var bottomHeightOffset: Float
        get() = _bottomHeightOffset
        set(value) {
            _bottomHeightOffset = value
            updateLatch(value, bottomHeightLimit) { bottomReachedHiddenEdge = it }
        }

    private inline fun updateLatch(
        offset: Float,
        limit: Float,
        set: (Boolean) -> Unit,
    ) {
        if (limit <= 0f) return
        if (offset >= 0f) {
            set(false)
        } else if (offset <= -limit) {
            set(true)
        }
    }

    var topHeightLimit: Float = 0f
        set(value) {
            field = value
            if (topHeightOffset < -value) topHeightOffset = -value
        }

    var bottomHeightLimit: Float = 0f
        set(value) {
            field = value
            if (bottomHeightOffset < -value) bottomHeightOffset = -value
        }

    val topCollapsedFraction: Float
        get() = if (topHeightLimit <= 0f) 0f else (-topHeightOffset / topHeightLimit).coerceIn(0f, 1f)

    val bottomCollapsedFraction: Float
        get() = if (bottomHeightLimit <= 0f) 0f else (-bottomHeightOffset / bottomHeightLimit).coerceIn(0f, 1f)

    /**
     * Snaps both bars to the nearest edge (fully shown or fully hidden).
     *
     * If [initialVelocityY] is non-zero the spring continues the fling's motion rather than
     * starting from rest, avoiding the "extra animation at the end of the fling" feel. The
     * velocity is in content-space (negative = hide direction, positive = reveal direction).
     */
    suspend fun settleToNearestEdge(initialVelocityY: Float = 0f) {
        coroutineScope {
            launch { settleOne({ topHeightOffset }, topHeightLimit, topReachedHiddenEdge, initialVelocityY) { topHeightOffset = it } }
            launch { settleOne({ bottomHeightOffset }, bottomHeightLimit, bottomReachedHiddenEdge, initialVelocityY) { bottomHeightOffset = it } }
        }
    }

    /**
     * Animates both bars back to the fully visible resting state. Used on lifecycle resume.
     */
    suspend fun resetToVisible() {
        coroutineScope {
            launch { animateOne({ topHeightOffset }, 0f, 0f, topHeightLimit) { topHeightOffset = it } }
            launch { animateOne({ bottomHeightOffset }, 0f, 0f, bottomHeightLimit) { bottomHeightOffset = it } }
        }
    }

    private suspend fun settleOne(
        get: () -> Float,
        limit: Float,
        canFullyHide: Boolean,
        initialVelocityY: Float,
        set: (Float) -> Unit,
    ) {
        if (limit <= 0f) return
        val current = get()
        if (current >= 0f || current <= -limit) return

        // Decide target edge from position by default, but let a strong velocity bias it.
        val positionBiasToHide = -current > limit / 2f
        val target =
            when {
                // Snapping to the hidden edge is only safe once the bar has actually been scrolled
                // there (canFullyHide): the content has then moved at least a full bar height and
                // fills the slot the bar vacates. From a small near-top collapse it hasn't, so
                // snapping hidden would open a blank band — settle back into view instead.
                !canFullyHide -> 0f
                initialVelocityY < -VELOCITY_BIAS_THRESHOLD -> -limit
                initialVelocityY > VELOCITY_BIAS_THRESHOLD -> 0f
                positionBiasToHide -> -limit
                else -> 0f
            }
        animateOne(get, target, initialVelocityY, limit, set)
    }

    private suspend fun animateOne(
        get: () -> Float,
        target: Float,
        initialVelocity: Float,
        limit: Float,
        set: (Float) -> Unit,
    ) {
        val start = get()
        if (start == target && initialVelocity == 0f) return
        Animatable(start)
            .apply {
                // Clamp to the visible travel range. A critically-damped spring still crosses its
                // target once when given an initial velocity in the target's direction, so without
                // these bounds a fast reveal fling would push the offset past 0 (or past -limit on
                // a hide) and render the bar overshooting its resting edge before springing back.
                // Hitting a bound ends the animation at the edge — a crisp settle with no rebound.
                if (limit > 0f) updateBounds(lowerBound = -limit, upperBound = 0f)
            }.animateTo(
                targetValue = target,
                animationSpec = SETTLE_SPRING,
                initialVelocity = initialVelocity,
            ) {
                set(value)
            }
    }

    companion object {
        private const val VELOCITY_BIAS_THRESHOLD = 200f

        // Bounce-free so the chrome never wobbles past its edge, but stiff enough to feel like a
        // quick native snap rather than a slow float once the finger lifts. Overshoot from a strong
        // initial velocity is still caught by the bounds in animateOne, so a higher stiffness here
        // only affects how briskly the bar resolves to its edge.
        private val SETTLE_SPRING =
            spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )

        val Saver: Saver<DisappearingBarState, *> =
            Saver(
                save = { listOf(it.topHeightOffset, it.bottomHeightOffset) },
                restore = { DisappearingBarState(it[0], it[1]) },
            )
    }
}

@Composable
fun rememberDisappearingBarState(): DisappearingBarState = rememberSaveable(saver = DisappearingBarState.Saver) { DisappearingBarState() }
