package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.R

internal class LengthConverter(context: Context) : SimpleFactorConverter() {
    override val dimension = Dimension.Length

    override val standardUnits = listOf(
            MeasureUnitWithFactor(
                    1.0,
                    context.getString(R.string.unit_meter_symbol),
                    R.plurals.unit_meter
            ),
            MeasureUnitWithFactor(
                    0.001,
                    context.getString(R.string.unit_kilometer_symbol),
                    R.plurals.unit_kilometer
            ),
            MeasureUnitWithFactor(
                    10.0,
                    context.getString(R.string.unit_decimeter_symbol),
                    R.plurals.unit_decimeter
            ),
            MeasureUnitWithFactor(
                    100.0,
                    context.getString(R.string.unit_centimeter_symbol),
                    R.plurals.unit_centimeter
            ),
            MeasureUnitWithFactor(
                    1000.0,
                    context.getString(R.string.unit_millimeter_symbol),
                    R.plurals.unit_millimeter
            ),
            MeasureUnitWithFactor(
                    1000_000.0,
                    context.getString(R.string.unit_nanometer_symbol),
                    R.plurals.unit_nanometer
            ),
            MeasureUnitWithFactor(
                    1_000_000_000.0,
                    context.getString(R.string.unit_picometer_symbol),
                    R.plurals.unit_picometer
            ),
            MeasureUnitWithFactor(
                    100 / 2.54,
                    context.getString(R.string.unit_inch_symbol),
                    R.plurals.unit_inch
            ),
            MeasureUnitWithFactor(
                    100 / (2.54 * 12),
                    context.getString(R.string.unit_foot_symbol),
                    R.plurals.unit_foot
            ),
            MeasureUnitWithFactor(
                    100 / (2.54 * 12 * 3),
                    context.getString(R.string.unit_yard_symbol),
                    R.plurals.unit_yard
            ),
            MeasureUnitWithFactor(
                    100 / (2.54 * 12 * 3 * 1760),
                    context.getString(R.string.unit_mile_symbol),
                    R.plurals.unit_mile
            ),
            MeasureUnitWithFactor(
                    1 / 1852.0,
                    context.getString(R.string.unit_nautic_mile_symbol),
                    R.plurals.unit_nautic_mile
            ),
            MeasureUnitWithFactor(
                    1.616255e-35,
                    context.getString(R.string.unit_planck_distance_symbol),
                    R.plurals.unit_planck_distance
            )
    )

}