package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.R

class AreaConverter(context: Context) : Converter() {
    override val dimension = Dimension.Area

    override val standardUnits = listOf(
            MeasureUnit(
                    0.000001,
                    context.getString(R.string.unit_sqkilometer_symbol),
                    R.plurals.unit_sqkilometer
            ),
            MeasureUnit(
                    0.0001,
                    context.getString(R.string.unit_hectare_symbol),
                    R.plurals.unit_hectare
            ),
            MeasureUnit(
                    1.0,
                    context.getString(R.string.unit_sqmeter_symbol),
                    R.plurals.unit_sqmeter
            ),
            MeasureUnit(
                    10000.0,
                    context.getString(R.string.unit_sqcentimeter_symbol),
                    R.plurals.unit_sqcentimeter
            ),
            MeasureUnit(
                    1000000.0,
                    context.getString(R.string.unit_sqmillimeter_symbol),
                    R.plurals.unit_sqmillimeter
            ),
            MeasureUnit(
                    100 * 100 / (2.54 * 2.54),
                    context.getString(R.string.unit_sqinch_symbol),
                    R.plurals.unit_sqinch
            ),
            MeasureUnit(
                    100 * 100 / (2.54 * 2.54 * 144),
                    context.getString(R.string.unit_sqfoot_symbol),
                    R.plurals.unit_sqfoot
            ),
            MeasureUnit(
                    100 * 100 / (2.54 * 2.54 * 1296),
                    context.getString(R.string.unit_sqyard_symbol),
                    R.plurals.unit_sqyard
            ),
            MeasureUnit(
                    100 * 100 / (2.54 * 2.54 * 144 * 43560),
                    context.getString(R.string.unit_acre_symbol),
                    R.plurals.unit_acre
            )
    )
}