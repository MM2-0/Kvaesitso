package de.mm20.launcher2.ui.ktx

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/**
 * Converts the given pixel size to a Dp value based on the current density
 */
@Composable
fun Float.toDp(): Dp {
    return (this / LocalDensity.current.density).dp
}
