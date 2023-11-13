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

fun Float.roundToString(): String = this.roundToInt().toString()

fun Float.metersToLocalizedString(context: Context, imperialUnits: Boolean): String {
    val decimalFormat =
        DecimalFormat().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }

    val (value, unit) = if (imperialUnits) {
        // yee haw
        val asFeet = this * 3.28084f
        val isYards = asFeet >= 3f
        val isMiles = asFeet >= 5280f
        val value =
            if (isMiles) decimalFormat.format(asFeet / 5280f)
            else if (isYards) (asFeet / 3f).roundToString()
            else asFeet.roundToString()

        val unit = context.getString(
            if (isMiles) R.string.unit_mile_symbol
            else if (isYards) R.string.unit_yard_symbol
            else R.string.unit_foot_symbol
        )

        value to unit
    } else {
        val isKm = this >= 1000f
        val value =
            if (isKm) decimalFormat.format(this / 1000f)
            else this.roundToString()

        val unit = context.getString(
            if (isKm) R.string.unit_kilometer_symbol
            else R.string.unit_meter_symbol
        )

        value to unit
    }

    return "$value $unit"
}

// https://stackoverflow.com/a/68651222
val Float.Companion.DegreesConverter
    get() = TwoWayConverter<Float, AnimationVector2D>({
        val rad = it * Float.PI / 180f
        AnimationVector2D(sin(rad), cos(rad))
    }, {
        (atan2(it.v1, it.v2) * 180f / Float.PI + 360f) % 360f
    })
