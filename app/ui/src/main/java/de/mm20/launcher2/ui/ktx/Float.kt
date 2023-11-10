package de.mm20.launcher2.ui.ktx

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * Converts the given pixel size to a Dp value based on the current density
 */
@Composable
fun Float.toDp(): Dp {
    return (this / LocalDensity.current.density).dp
}

fun Float.metersToLocalizedString(context: Context, imperialUnits: Boolean): String {
    val decimalFormat = DecimalFormat().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }

    val (value, unit) = if (imperialUnits) {
        // yee haw
        val asFeet = this * 3.28084f
        val isYards = asFeet >= 3f
        val isMiles = asFeet >= 5280f
        val value =
            if (isMiles) decimalFormat.format(asFeet / 5280f)
            else if (isYards) decimalFormat.format(asFeet / 3f)
            else asFeet.roundToInt().toString()

        val unit = context.getString(
            if (isMiles) R.string.unit_mile_symbol
            else if (isYards) R.string.unit_yard_symbol
            else R.string.unit_foot_symbol
        )

        value to unit
    } else {
        val isK = this >= 1000f
        val value =
            if (isK) decimalFormat.format(this / 1000f)
            else this.roundToInt().toString()

        val unit = context.getString(
            if (isK) R.string.unit_kilometer_symbol
            else R.string.unit_meter_symbol
        )

        value to unit
    }

    return "$value $unit"
}
