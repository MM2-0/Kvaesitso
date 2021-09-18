package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.R

class TimeConverter(context: Context) : Converter() {
    override val dimension = Dimension.Time

    override val standardUnits = listOf(
            MeasureUnit(
                    1.0,
                    context.getString(R.string.unit_second_symbol),
                    R.plurals.unit_second
            ),
            MeasureUnit(
                    1000.0,
                    context.getString(R.string.unit_millisecond_symbol),
                    R.plurals.unit_millisecond
            ),
            MeasureUnit(
                    1.0 / 60,
                    context.getString(R.string.unit_minute_symbol),
                    R.plurals.unit_minute
            ),
            MeasureUnit(
                    1.0 / (60 * 60),
                    context.getString(R.string.unit_hour_symbol),
                    R.plurals.unit_hour
            ),
            MeasureUnit(
                    1.0 / (60 * 60 * 24),
                    context.getString(R.string.unit_day_symbol),
                    R.plurals.unit_day
            ),
            MeasureUnit(
                    1.0 / (60 * 60 * 24 * 365),
                    context.getString(R.string.unit_year_symbol),
                    R.plurals.unit_year
            )
    )

}