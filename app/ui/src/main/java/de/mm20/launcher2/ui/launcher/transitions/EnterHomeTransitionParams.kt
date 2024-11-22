package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

@Stable
data class EnterHomeTransitionParams(
    val targetBounds: IntRect,
    val icon: (@Composable (animVector: IntOffset, progress: () -> Float) -> Unit)? = null
)