package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.*

internal class TemperatureConverter(context: Context) : Converter {
    override val dimension = Dimension.Temperature

    val units = listOf(
        TemperatureMeasureUnit(
            context.getString(R.string.unit_degree_celsius_symbol),
            R.plurals.unit_degree_celsius,
            TemperatureUnit.DegreeCelsius
        ),
        TemperatureMeasureUnit(
            context.getString(R.string.unit_degree_fahrenheit_symbol),
            R.plurals.unit_degree_fahrenheit,
            TemperatureUnit.DegreeFahrenheit
        ),
        TemperatureMeasureUnit(
            context.getString(R.string.unit_kelvin_symbol),
            R.plurals.unit_kelvin,
            TemperatureUnit.Kelvin
        ),
        TemperatureMeasureUnit(
            context.getString(R.string.unit_planck_temperature_symbol),
            R.plurals.unit_planck_temperature,
            TemperatureUnit.Planck
        )
    )

    override suspend fun isValidUnit(symbol: String): Boolean {
        return units.any { it.symbol == symbol }
    }

    override suspend fun convert(
        context: Context,
        fromUnit: String,
        value: Double,
        toUnit: String?
    ): UnitConverter {
        val from = units.first { it.symbol == fromUnit }
        val to = toUnit?.let { unit -> units.first { it.symbol == unit } }

        val values = mutableListOf<UnitValue>()

        if (to != null) {
            val toValue = convertTemperature(value, from.unit, to.unit)

            values += UnitValue(
                value = value,
                symbol = toUnit,
                formattedName = ConverterUtils.formatName(context, to, toValue),
                formattedValue = ConverterUtils.formatValue(context, to, toValue),
            )
        } else {
            for (to in units) {
                if (to.symbol == from.symbol) continue
                val v = convertTemperature(value, from.unit, to.unit)
                values += UnitValue(
                    v,
                    to.symbol,
                    ConverterUtils.formatName(context, to, v),
                    ConverterUtils.formatValue(context, to, v)
                )
            }
        }
        return UnitConverter(
            dimension = Dimension.Temperature,
            inputValue = UnitValue(
                value = value,
                symbol = fromUnit,
                formattedName = ConverterUtils.formatName(context, from, value),
                formattedValue = ConverterUtils.formatValue(context, from, value),
            ),
            values = values
        )
    }

    private fun convertTemperature(
        value: Double,
        from: TemperatureUnit,
        to: TemperatureUnit
    ): Double {
        if (from == to) return value
        if (from == TemperatureUnit.Kelvin && to == TemperatureUnit.DegreeCelsius) {
            return value - 273.15
        }
        if (from == TemperatureUnit.DegreeCelsius && to == TemperatureUnit.Kelvin) {
            return value + 273.15
        }
        if (from === TemperatureUnit.DegreeCelsius && to == TemperatureUnit.DegreeFahrenheit) {
            return value * (9.0 / 5.0) + 32.0
        }
        if (from === TemperatureUnit.DegreeFahrenheit && to == TemperatureUnit.DegreeCelsius) {
            return (value - 32.0) * (5.0 / 9.0)
        }
        if (from === TemperatureUnit.Kelvin && to == TemperatureUnit.DegreeFahrenheit) {
            return (value - 273.15) * (9.0 / 5.0) + 32.0
        }
        if (from === TemperatureUnit.DegreeFahrenheit && to == TemperatureUnit.Kelvin) {
            return (value - 32.0) * (5.0 / 9.0) + 273.15
        }
        if (from === TemperatureUnit.Kelvin && to == TemperatureUnit.Planck) {
            return value * 1.416833e32
        }
        if (from === TemperatureUnit.Planck && to == TemperatureUnit.Kelvin) {
            return value / 1.416833e32
        }
        if (from === TemperatureUnit.DegreeCelsius && to == TemperatureUnit.Planck) {
            return (value - 273.15) * 1.416833e32
        }
        if (from === TemperatureUnit.Planck && to == TemperatureUnit.DegreeCelsius) {
            return value / 1.416833e32 + 273.15
        }
        if (from === TemperatureUnit.DegreeFahrenheit && to == TemperatureUnit.Planck) {
            return ((value - 32.0) * (5.0 / 9.0) + 273.15) * 1.416833e32
        }
        if (from === TemperatureUnit.Planck && to == TemperatureUnit.DegreeFahrenheit) {
            return ((value / 1.416833e32) - 273.15) * (9.0 / 5.0) + 32.0
        }

        
        throw IllegalArgumentException()
    }

    override suspend fun getSupportedUnits(): List<MeasureUnit> {
        return units
    }
}

data class TemperatureMeasureUnit(
    override val symbol: String,
    val nameResource: Int,
    val unit: TemperatureUnit
) : MeasureUnit {
    override fun formatName(context: Context, value: Double): String {
        return context.resources.getQuantityString(nameResource, value.toInt())
    }
}

enum class TemperatureUnit {
    DegreeCelsius,
    DegreeFahrenheit,
    Kelvin,
    Planck,
}