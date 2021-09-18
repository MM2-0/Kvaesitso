package de.mm20.launcher2.ui.ktx

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Converts the given pixel size to a Dp value based on the current density
 */
@Composable
fun Int.toDp(): Dp {
    return (this / LocalDensity.current.density).dp
}