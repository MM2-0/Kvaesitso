package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.R

class DataConverter(context: Context) : Converter() {
    override val dimension = Dimension.Data

    override val standardUnits = listOf(
            MeasureUnit(
                    1.0,
                    context.getString(R.string.unit_byte_symbol),
                    R.plurals.unit_byte
            ),
            MeasureUnit(
                    0.001,
                    context.getString(R.string.unit_kilobyte_symbol),
                    R.plurals.unit_kilobyte
            ),
            MeasureUnit(
                    0.000001,
                    context.getString(R.string.unit_megabyte_symbol),
                    R.plurals.unit_megabyte
            ),
            MeasureUnit(
                    0.000000001,
                    context.getString(R.string.unit_gigabyte_symbol),
                    R.plurals.unit_gigabyte
            ),
            MeasureUnit(
                    0.000000000001,
                    context.getString(R.string.unit_terabyte_symbol),
                    R.plurals.unit_terabyte
            ),
            MeasureUnit(
                    1.0 / 1024,
                    context.getString(R.string.unit_kibibyte_symbol),
                    R.plurals.unit_kibibyte
            ),
            MeasureUnit(
                    1.0 / (1024 * 1024),
                    context.getString(R.string.unit_mebibyte_symbol),
                    R.plurals.unit_mebibyte
            ),
            MeasureUnit(
                    1.0 / (1024 * 1024 * 1024),
                    context.getString(R.string.unit_gibibyte_symbol),
                    R.plurals.unit_gibibyte
            ),
            MeasureUnit(
                    1.0 / (1024.0 * 1024 * 1024 * 1024),
                    context.getString(R.string.unit_tebibyte_symbol),
                    R.plurals.unit_tebibyte
            ),
            MeasureUnit(
                    8.0,
                    context.getString(R.string.unit_bit_symbol),
                    R.plurals.unit_bit
            ),
            MeasureUnit(
                    8.0 / 1000,
                    context.getString(R.string.unit_kilobit_symbol),
                    R.plurals.unit_kilobit
            ),
            MeasureUnit(
                    8.0 / 1000000,
                    context.getString(R.string.unit_megabit_symbol),
                    R.plurals.unit_megabit
            ),
            MeasureUnit(
                    8.0 / 1000000000,
                    context.getString(R.string.unit_gigabit_symbol),
                    R.plurals.unit_gigabit
            ),
            MeasureUnit(
                    8.0 / 1000000000000,
                    context.getString(R.string.unit_terabit_symbol),
                    R.plurals.unit_terabit
            )
    )

}