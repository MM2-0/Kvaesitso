package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.ConverterUtils
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.UnitValue
import kotlin.math.roundToInt

/**
 * A converter for units that can converted into each other by simple multiplication with a constant factor
 */
internal abstract class SimpleFactorConverter: Converter {
    open val standardUnits: List<MeasureUnitWithFactor> = emptyList()

    /**
     * Returns true if a symbol is a valid unit of this converter
     */
    override suspend fun isValidUnit(symbol: String): Boolean {
        return standardUnits.any { it.symbol == symbol }
    }

    override suspend fun convert(context: Context, fromUnit: String, value: Double, toUnit: String?): UnitConverter {
        val results = mutableListOf<UnitValue>()
        val unit = standardUnits.first { it.symbol == fromUnit }
        if (toUnit == null) {
            for (targetUnit in standardUnits) {
                if (targetUnit.symbol == unit.symbol) continue
                val v = value * targetUnit.factor / unit.factor
                results += UnitValue(v, targetUnit.symbol, ConverterUtils.formatName(context, targetUnit, v), ConverterUtils.formatValue(context, unit, v))
            }
        } else {
            val targetUnit = standardUnits.first { it.symbol == toUnit }
            val v = value * targetUnit.factor / unit.factor
            results += UnitValue(v, targetUnit.symbol, ConverterUtils.formatName(context, targetUnit, v), ConverterUtils.formatValue(context, unit, v))
        }
        val inputValue = UnitValue(value, fromUnit, ConverterUtils.formatName(context, unit, value), ConverterUtils.formatValue(context, unit, value))
        return UnitConverter(dimension, inputValue, results)
    }

    override suspend fun getSupportedUnits(): List<MeasureUnit> {
        return standardUnits
    }
}


data class MeasureUnitWithFactor(
    val factor: Double,
    override val symbol: String,
    val nameResource: Int
): MeasureUnit {
    override fun formatName(context: Context, value: Double): String {
        return context.resources.getQuantityString(nameResource, value.roundToInt())
    }
}