package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.R

class MassConverter(context: Context): Converter() {
    override val dimension = Dimension.Mass

    override val standardUnits = listOf(
            MeasureUnit(
                    1.0,
                    context.getString(R.string.unit_kilogram_symbol),
                    R.plurals.unit_kilogram
            ),
            MeasureUnit(
                    1000.0,
                    context.getString(R.string.unit_gram_symbol),
                    R.plurals.unit_gram
            ),
            MeasureUnit(
                    0.001,
                    context.getString(R.string.unit_metric_ton_symbol),
                    R.plurals.unit_metric_ton
            ),
            MeasureUnit(
                    1000.0 / (453.59237 * 2240.0),
                    context.getString(R.string.unit_long_ton_symbol),
                    R.plurals.unit_long_ton
            ),
            MeasureUnit(
                    1000.0 / (453.59237 * 14.0),
                    context.getString(R.string.unit_stone_symbol),
                    R.plurals.unit_stone
            ),
            MeasureUnit(
                    1000.0 / 453.59237,
                    context.getString(R.string.unit_pound_symbol),
                    R.plurals.unit_pound
            ),
            MeasureUnit(
                    16.0 * 1000.0 / 453.59237,
                    context.getString(R.string.unit_ounce_symbol),
                    R.plurals.unit_ounce
            ),
            MeasureUnit(
                    1000.0 / (453.59237 * 2000.0),
                    context.getString(R.string.unit_short_ton_symbol),
                    R.plurals.unit_short_ton
            )
    )

}