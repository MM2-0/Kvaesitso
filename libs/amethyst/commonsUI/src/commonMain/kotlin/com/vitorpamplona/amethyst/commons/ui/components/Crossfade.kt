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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.collection.mutableScatterMapOf
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.fastForEach

/**
 * Whether decorative animations run. The app turns them off in performance mode by providing
 * `false` near its root; [CrossfadeIfEnabled] follows it unless told otherwise.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/**
 * [Crossfade][MyCrossfade] between [targetState]s when [enabled], a plain swap otherwise. By default
 * [enabled] follows [LocalAnimationsEnabled]. The animated path defers building its transition until
 * the target first changes; see [DeferredCrossfade].
 */
@Composable
fun <T> CrossfadeIfEnabled(
    targetState: T,
    enabled: Boolean = LocalAnimationsEnabled.current,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    label: String = "Crossfade",
    content: @Composable (T) -> Unit,
) {
    if (enabled) {
        DeferredCrossfade(targetState, modifier, contentAlignment, animationSpec, label, content)
    } else {
        Box(modifier, contentAlignment) {
            content(targetState)
        }
    }
}

/** Latches the first time a crossfade's target moves off the value it was composed with. */
private class ChangeLatch {
    var changed = false
}

/**
 * A [MyCrossfade] that does not build its [androidx.compose.animation.core.Transition] until there
 * is something to animate.
 *
 * `updateTransition` allocates a transition, its animation list and its seeking state on *first
 * composition*, even though first composition has nothing to cross-fade — target and initial state
 * are the same value. In a feed that is waste: every card scrolled in builds a transition per
 * animated element, and during a scroll essentially none of them run, because the underlying counts
 * and icons do not change in the second a card is on screen.
 *
 * So the plain content renders until the target actually moves. At that point the transition is
 * built seeded at the *original* value via [MutableTransitionState] and immediately re-targeted at
 * the new one, so the first real change still animates exactly as before; every later change
 * animates through the now-live transition normally.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun <T> DeferredCrossfade(
    targetState: T,
    modifier: Modifier,
    contentAlignment: Alignment,
    animationSpec: FiniteAnimationSpec<Float>,
    label: String,
    content: @Composable (T) -> Unit,
) {
    val initial = remember { targetState }
    val latch = remember { ChangeLatch() }
    if (targetState != initial) latch.changed = true

    if (!latch.changed) {
        Box(modifier, contentAlignment) {
            content(targetState)
        }
    } else {
        val transitionState = remember { MutableTransitionState(initial) }
        transitionState.targetState = targetState
        val transition = rememberTransition(transitionState, label)
        transition.MyCrossfade(modifier, contentAlignment, animationSpec, content = content)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> MyCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    label: String = "Crossfade",
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label)
    transition.MyCrossfade(modifier, contentAlignment, animationSpec, content = content)
}

@ExperimentalAnimationApi
@Composable
fun <T> Transition<T>.MyCrossfade(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    contentKey: (targetState: T) -> Any? = { it },
    content: @Composable (targetState: T) -> Unit,
) {
    val currentlyVisible = remember { mutableStateListOf<T>().apply { add(currentState) } }
    val contentMap = remember { mutableScatterMapOf<T, @Composable () -> Unit>() }
    // If not animating, just display the current state
    if (currentState == targetState && (currentlyVisible.size != 1 || currentlyVisible[0] != targetState)) {
        // Remove all the intermediate items from the list once the animation is finished.
        currentlyVisible.removeAll { it != targetState }
        contentMap.clear()
    }

    if (targetState !in contentMap) {
        // Replace target with the same key if any
        val replacementId =
            currentlyVisible.indexOfFirst { contentKey(it) == contentKey(targetState) }
        if (replacementId == -1) {
            currentlyVisible.add(targetState)
        } else {
            currentlyVisible[replacementId] = targetState
        }
        contentMap.clear()
        currentlyVisible.fastForEach { stateForContent ->
            contentMap[stateForContent] = {
                val alpha by
                    animateFloat(transitionSpec = { animationSpec }) {
                        if (it == stateForContent) 1f else 0f
                    }
                Box(Modifier.graphicsLayer { this.alpha = alpha }, contentAlignment) { content(stateForContent) }
            }
        }
    }

    Box(modifier, contentAlignment) {
        currentlyVisible.fastForEach { key(contentKey(it)) { contentMap[it]?.invoke() } }
    }
}
