package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.R

internal class VolumeConverter(context: Context) : SimpleFactorConverter() {
    override val dimension: Dimension = Dimension.Volume

    override val standardUnits = listOf(
        // SI units
        MeasureUnitWithFactor(
            factor = 1.0,
            symbol = context.getString(R.string.unit_cubic_meter_symbol),
            nameResource = R.plurals.unit_cubic_meter
        ),
        MeasureUnitWithFactor(
            factor = 1_000_000.0,
            symbol = context.getString(R.string.unit_cubic_centimeter_symbol),
            nameResource = R.plurals.unit_cubic_centimeter
        ),
        MeasureUnitWithFactor(
            factor = 1_000_000_000.0,
            symbol = context.getString(R.string.unit_cubic_millimeter_symbol),
            nameResource = R.plurals.unit_cubic_millimeter
        ),
        // Other metric units
        MeasureUnitWithFactor(
            factor = 1000.0,
            symbol = context.getString(R.string.unit_liter_symbol),
            nameResource = R.plurals.unit_liter
        ),
        MeasureUnitWithFactor(
            factor = 1_000_000.0,
            symbol = context.getString(R.string.unit_milliliter_symbol),
            nameResource = R.plurals.unit_milliliter
        ),

        // Imperial geometric units
        MeasureUnitWithFactor(
            factor = 61023.74409473228,
            symbol = context.getString(R.string.unit_cubic_inch_symbol),
            nameResource = R.plurals.unit_cubic_inch
        ),
        MeasureUnitWithFactor(
            factor = 61023.74409473228 / 1728.0,
            symbol = context.getString(R.string.unit_cubic_foot_symbol),
            nameResource = R.plurals.unit_cubic_foot
        ),
        MeasureUnitWithFactor(
            factor = 61023.74409473228 / (1728.0 * 27.0),
            symbol = context.getString(R.string.unit_cubic_yard_symbol),
            nameResource = R.plurals.unit_cubic_yard
        ),

        // US fluid units
        MeasureUnitWithFactor(
            factor = 264.1720523581484,
            symbol = context.getString(R.string.unit_gallon_us_symbol),
            nameResource = R.plurals.unit_gallon_us
        ),
        MeasureUnitWithFactor(
            factor = 264.1720523581484 * 8,
            symbol = context.getString(R.string.unit_pint_us_symbol),
            nameResource = R.plurals.unit_pint_us
        ),
        MeasureUnitWithFactor(
            factor = 264.1720523581484 * 16,
            symbol = context.getString(R.string.unit_cup_symbol),
            nameResource = R.plurals.unit_cup
        ),
        MeasureUnitWithFactor(
            factor = 264.1720523581484 * 128,
            symbol = context.getString(R.string.unit_fluid_ounce_us_symbol),
            nameResource = R.plurals.unit_fluid_ounce_us
        ),
        MeasureUnitWithFactor(
            factor = 264.1720523581484 * 256,
            symbol = context.getString(R.string.unit_tablespoon_symbol),
            nameResource = R.plurals.unit_tablespoon
        ),
        MeasureUnitWithFactor(
            factor = 264.1720523581484 * 768,
            symbol = context.getString(R.string.unit_teaspoon_symbol),
            nameResource = R.plurals.unit_teaspoon
        ),

        // Imperial fluid units
        MeasureUnitWithFactor(
            factor = 219.9692482990878,
            symbol = context.getString(R.string.unit_gallon_imp_symbol),
            nameResource = R.plurals.unit_gallon_imp
        ),
        MeasureUnitWithFactor(
            factor = 219.969248299087 * 8,
            symbol = context.getString(R.string.unit_pint_imp_symbol),
            nameResource = R.plurals.unit_pint_imp
        ),
        MeasureUnitWithFactor(
            factor = 219.969248299087 * 128,
            symbol = context.getString(R.string.unit_fluid_ounce_imp_symbol),
            nameResource = R.plurals.unit_fluid_ounce_imp
        ),


    )
}