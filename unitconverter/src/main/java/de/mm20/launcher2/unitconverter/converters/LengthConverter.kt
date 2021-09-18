package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.R

class LengthConverter(context: Context) : Converter() {
    override val dimension = Dimension.Length

    override val standardUnits = listOf(
            MeasureUnit(
                    1.0,
                    context.getString(R.string.unit_meter_symbol),
                    R.plurals.unit_meter
            ),
            MeasureUnit(
                    0.001,
                    context.getString(R.string.unit_kilometer_symbol),
                    R.plurals.unit_kilometer
            ),
            MeasureUnit(
                    100.0,
                    context.getString(R.string.unit_centimeter_symbol),
                    R.plurals.unit_centimeter
            ),
            MeasureUnit(
                    1000.0,
                    context.getString(R.string.unit_millimeter_symbol),
                    R.plurals.unit_millimeter
            ),
            MeasureUnit(
                    100 / 2.54,
                    context.getString(R.string.unit_inch_symbol),
                    R.plurals.unit_inch
            ),
            MeasureUnit(
                    100 / (2.54 * 12),
                    context.getString(R.string.unit_foot_symbol),
                    R.plurals.unit_foot
            ),
            MeasureUnit(
                    100 / (2.54 * 12 * 3),
                    context.getString(R.string.unit_yard_symbol),
                    R.plurals.unit_yard
            ),
            MeasureUnit(
                    100 / (2.54 * 12 * 3 * 1760),
                    context.getString(R.string.unit_mile_symbol),
                    R.plurals.unit_mile
            ),
            MeasureUnit(
                    1 / 1852.0,
                    context.getString(R.string.unit_nautic_mile_symbol),
                    R.plurals.unit_nautic_mile
            )
    )

}