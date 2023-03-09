package de.mm20.launcher2.ui.ktx

import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

const val TWO_PI_F = (2.0 * PI).toFloat()

/**
 * Converts the given pixel size to a Dp value based on the current density
 */
@Composable
fun Float.toDp(): Dp {
    return (this / LocalDensity.current.density).dp
}

// https://stackoverflow.com/a/68651222
val Float.Companion.radiansConverter
    get() = TwoWayConverter<Float, AnimationVector2D>({ rad ->
        AnimationVector2D(sin(rad), cos(rad))
    }, {
        (atan2(it.v1, it.v2) + TWO_PI_F) % TWO_PI_F
    })
