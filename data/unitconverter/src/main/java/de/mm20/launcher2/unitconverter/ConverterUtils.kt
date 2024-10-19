package de.mm20.launcher2.unitconverter

import android.content.Context
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

internal object ConverterUtils {

    fun formatName(context: Context, unit: MeasureUnit, value: Double): String {
        return unit.formatName(context, value)
    }

    fun formatValue(context: Context, unit: MeasureUnit, value: Double): String {
        if (abs(value) > 1e5 || abs(value) < 1e-3) {
            return DecimalFormat("#.###E0").apply {
            }.format(value)
        }

        return DecimalFormat("#.###").apply {
        }.format(value)

    }

}