package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

data class HomeTransitionParams(
    val targetBounds: Rect,
    val icon: (@Composable () -> Unit)? = null
)