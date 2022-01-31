package de.mm20.launcher2.ui.ktx

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
fun Dp.toPixels(): Float {
    return value * LocalDensity.current.density
}