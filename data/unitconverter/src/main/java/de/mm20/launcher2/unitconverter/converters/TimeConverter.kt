package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.R

internal class TimeConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Time

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(R.string.unit_second_symbol),
                    R.plurals.unit_second
            ),
            MeasureUnitWithFactor(
                    1000.0,
                    context.getString(R.string.unit_millisecond_symbol),
                    R.plurals.unit_millisecond
            ),
            MeasureUnitWithFactor(
                    1.0 / 60,
                    context.getString(R.string.unit_minute_symbol),
                    R.plurals.unit_minute
            ),
            MeasureUnitWithFactor(
                    1.0 / (60 * 60),
                    context.getString(R.string.unit_hour_symbol),
                    R.plurals.unit_hour
            ),
            MeasureUnitWithFactor(
                    1.0 / (60 * 60 * 24),
                    context.getString(R.string.unit_day_symbol),
                    R.plurals.unit_day
            ),
            MeasureUnitWithFactor(
                    1.0 / 31_557_600,
                    context.getString(R.string.unit_year_symbol),
                    R.plurals.unit_year
            )
    )

}