package de.mm20.launcher2.ui.ktx

import android.content.Context
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.PI
import de.mm20.launcher2.ui.R
import java.text.DecimalFormat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Converts the given pixel size to a Dp value based on the current density
 */
@Composable
fun Float.toDp(): Dp {
    return (this / LocalDensity.current.density).dp
}

// https://stackoverflow.com/a/68651222
val Float.Companion.DegreesConverter
    get() = TwoWayConverter<Float, AnimationVector2D>({
        val rad = it * Float.PI / 180f
        AnimationVector2D(sin(rad), cos(rad))
    }, {
        (atan2(it.v1, it.v2) * 180f / Float.PI + 360f) % 360f
    })
