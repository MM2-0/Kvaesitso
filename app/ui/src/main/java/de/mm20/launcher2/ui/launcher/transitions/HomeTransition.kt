package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

data class HomeTransition(
    val startBounds: Rect,
    val targetBounds: Rect,
    val icon: (@Composable (animVector: Offset, progress: () -> Float) -> Unit)? = null
)