package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.R

internal class AreaConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Area

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    0.000001,
                    context.getString(R.string.unit_sqkilometer_symbol),
                    R.plurals.unit_sqkilometer
            ),
            MeasureUnitWithFactor(
                    0.0001,
                    context.getString(R.string.unit_hectare_symbol),
                    R.plurals.unit_hectare
            ),
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(R.string.unit_sqmeter_symbol),
                    R.plurals.unit_sqmeter
            ),
            MeasureUnitWithFactor(
                    10000.0,
                    context.getString(R.string.unit_sqcentimeter_symbol),
                    R.plurals.unit_sqcentimeter
            ),
            MeasureUnitWithFactor(
                    1000000.0,
                    context.getString(R.string.unit_sqmillimeter_symbol),
                    R.plurals.unit_sqmillimeter
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54),
                    context.getString(R.string.unit_sqinch_symbol),
                    R.plurals.unit_sqinch
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54 * 144),
                    context.getString(R.string.unit_sqfoot_symbol),
                    R.plurals.unit_sqfoot
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54 * 1296),
                    context.getString(R.string.unit_sqyard_symbol),
                    R.plurals.unit_sqyard
            ),
            MeasureUnitWithFactor(
                    100 * 100 / (2.54 * 2.54 * 144 * 43560),
                    context.getString(R.string.unit_acre_symbol),
                    R.plurals.unit_acre
            )
    )
}