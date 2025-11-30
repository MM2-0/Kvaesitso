package de.mm20.launcher2.ui.utils

import android.content.Context
import android.icu.number.NumberFormatter
import android.icu.number.Precision
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.ui.R
import java.text.NumberFormat
import java.util.Locale


internal fun formatDistance(
    context: Context,
    meters: Float,
    measurementSystem: MeasurementSystem,
): String {
    when (measurementSystem) {
        MeasurementSystem.UnitedStates -> {
            val feet = meters * 3.28084f

            if (feet >= 2640) {
                return formatMiles(context, feet / 5280f)
            }
            return formatFeet(context, feet)
        }

        MeasurementSystem.UnitedKingdom -> {
            val yards = meters * 1.09361f

            if (yards >= 880) {
                return formatMiles(context, yards / 1760f)
            }
            return formatYards(context, yards)
        }

        else -> {
            if (meters >= 1000) {
                return formatKilometers(context, meters / 1000f)
            }
            return formatMeters(context, meters)
        }
    }
}

private fun formatMeters(
    context: Context,
    meters: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(0))
            .format(Measure(meters, MeasureUnit.METER)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 0
        }.format(meters)
        return "$formatted ${context.getString(R.string.unit_meter_symbol)}"
    }
}

private fun formatKilometers(
    context: Context,
    kilometers: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(1))
            .format(Measure(kilometers, MeasureUnit.KILOMETER)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 1
        }.format(kilometers)
        return "$formatted ${context.getString(R.string.unit_kilometer_symbol)}"
    }
}


private fun formatFeet(
    context: Context,
    feet: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(0))
            .format(Measure(feet, MeasureUnit.FOOT)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 0
        }.format(feet)
        return "$formatted ${context.getString(R.string.unit_foot_symbol)}"
    }
}

private fun formatYards(
    context: Context,
    yards: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(0))
            .format(Measure(yards, MeasureUnit.YARD)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 0
        }.format(yards)
        return "$formatted ${context.getString(R.string.unit_yard_symbol)}"
    }
}

private fun formatMiles(
    context: Context,
    miles: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(1))
            .format(Measure(miles, MeasureUnit.MILE)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 1
        }.format(miles)
        return "$formatted ${context.getString(R.string.unit_mile_symbol)}"
    }
}

internal fun formatSpeed(
    context: Context,
    metersPerSecond: Float,
    measurementSystem: MeasurementSystem,
): String {
    if (measurementSystem == MeasurementSystem.UnitedStates) {
        return formatMpH(context, metersPerSecond * 2.2369f)
    } else {
        return formatKmH(context, metersPerSecond * 3.6f)
    }
}

private fun formatKmH(
    context: Context,
    kmH: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(0))
            .format(Measure(kmH, MeasureUnit.KILOMETER_PER_HOUR)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 0
        }.format(kmH)
        return "$formatted ${context.getString(R.string.unit_kilometer_per_hour_symbol)}"
    }
}

private fun formatMpH(
    context: Context,
    mph: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(0))
            .format(Measure(mph, MeasureUnit.MILE_PER_HOUR)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 0
        }.format(mph)
        return "$formatted ${context.getString(R.string.unit_mile_per_hour_symbol)}"
    }
}

internal fun formatTemperature(
    context: Context,
    kelvins: Float,
    measurementSystem: MeasurementSystem,
): String {
    if (measurementSystem == MeasurementSystem.UnitedStates) {
        return formatFahrenheit(context, kelvins * 9f / 5f - 459.67f)
    } else {
        return formatCelsius(context, kelvins + -273.15f)
    }
}

private fun formatCelsius(
    context: Context,
    celsius: Float,
): String {
    val formatted = NumberFormat.getInstance().apply {
        maximumFractionDigits = 0
    }.format(celsius)
    return "$formatted°"
}

private fun formatFahrenheit(
    context: Context,
    fahrenheit: Float,
): String {
    val formatted = NumberFormat.getInstance().apply {
        maximumFractionDigits = 0
    }.format(fahrenheit)
    return "$formatted°"

}

internal fun formatPercent(
    percent: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(0))
            .format(Measure(percent, MeasureUnit.PERCENT)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 0
        }.format(percent)
        return "$formatted %"
    }
}

internal fun formatPrecipitation(
    context: Context,
    millimeters: Float,
    measurementSystem: MeasurementSystem,
): String {
    if (measurementSystem == MeasurementSystem.UnitedStates) {
        return formatInches(context, millimeters / 25.4f)
    } else {
        return formatMillimeters(context, millimeters)
    }
}

private fun formatMillimeters(
    context: Context,
    millimeters: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(0))
            .format(Measure(millimeters, MeasureUnit.MILLIMETER)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 0
        }.format(millimeters)
        return "$formatted ${context.getString(R.string.unit_millimeter_symbol)}"
    }
}

private fun formatInches(
    context: Context,
    inches: Float,
): String {
    if (isAtLeastApiLevel(30)) {
        return NumberFormatter
            .withLocale(
                Locale.getDefault()
            ).unitWidth(NumberFormatter.UnitWidth.NARROW)
            .precision(Precision.maxFraction(1))
            .format(Measure(inches, MeasureUnit.INCH)).toString()
    } else {
        val formatted = NumberFormat.getInstance().apply {
            maximumFractionDigits = 1
        }.format(inches)
        return "$formatted ${context.getString(R.string.unit_inch_symbol)}"
    }
}