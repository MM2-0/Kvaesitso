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
    val (value, unit) = if (imperialUnits) {
        val asFeet = this * 3.28084f
        val isMiles = asFeet >= 5280f
        val value =
            if (isMiles) DecimalFormat().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
                .format(asFeet / 5280f)
            else asFeet.roundToInt().toString()

        val unit = context.getString(
            if (isMiles) R.string.unit_mile_symbol
            else R.string.unit_foot_symbol
        )

        value to unit
    } else {
        val isK = this >= 1000f
        val value =
            if (isK) DecimalFormat().apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
                .format(this / 1000f)
            else this.roundToInt().toString()

        val unit = context.getString(
            if (isK) R.string.unit_kilometer_symbol
            else R.string.unit_meter_symbol
        )

        value to unit
    }

    return "$value $unit"
}
