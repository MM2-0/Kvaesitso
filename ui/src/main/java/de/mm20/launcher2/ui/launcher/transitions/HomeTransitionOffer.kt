package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

fun interface HomeTransitionOffer {
    fun accept(targetBounds: Rect, icon: @Composable () -> Unit)
}