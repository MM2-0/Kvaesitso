package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

data class EnterHomeTransition(
    val startBounds: IntRect? = null,
    val targetBounds: IntRect? = null,
    val icon: (@Composable (animVector: IntOffset, progress: () -> Float) -> Unit)? = null
)