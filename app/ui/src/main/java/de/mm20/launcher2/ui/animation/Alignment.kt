package de.mm20.launcher2.ui.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment

@Composable
fun animateHorizontalAlignmentAsState(
    targetAlignment: Alignment.Horizontal,
    animationSpec: AnimationSpec<Float> = tween()
): State<BiasAlignment.Horizontal> {
    val bias by animateFloatAsState(
        targetValue = when (targetAlignment) {
            Alignment.Start -> -1f
            Alignment.End -> 1f
            else -> 0f
        },
        animationSpec = animationSpec
    )
    return remember { derivedStateOf { BiasAlignment.Horizontal(bias) } }
}