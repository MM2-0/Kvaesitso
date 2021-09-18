package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.UnitValue
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class Converter {

    abstract val dimension: Dimension

    open val standardUnits: List<MeasureUnit> = emptyList()

    /**
     * Returns true if a symbol is a valid unit of this converter
     */
    open suspend fun isValidUnit(symbol: String): Boolean {
        return standardUnits.any { it.symbol == symbol }
    }

    open suspend fun convert(context: Context, fromUnit: String, value: Double, toUnit: String?): UnitConverter {
        val results = mutableListOf<UnitValue>()
        val unit = standardUnits.first { it.symbol == fromUnit }
        if (toUnit == null) {
            for (targetUnit in standardUnits) {
                if (targetUnit.symbol == unit.symbol) continue
                val v = value * targetUnit.factor / unit.factor
                results += UnitValue(v, targetUnit.symbol, formatName(context, targetUnit, v), formatValue(context, unit, v))
            }
        } else {
            val targetUnit = standardUnits.first { it.symbol == toUnit }
            val v = value * targetUnit.factor / unit.factor
            results += UnitValue(v, targetUnit.symbol, formatName(context, targetUnit, v), formatValue(context, unit, v))
        }
        val inputValue = UnitValue(value, fromUnit, formatName(context, unit, value), formatValue(context, unit, value))
        return UnitConverter(dimension, inputValue, results)
    }

    open suspend fun formatName(context: Context, unit: MeasureUnit, value: Double): String {
        val resId = unit.nameResource
        val text = context.resources.getQuantityString(resId, value.roundToInt())
        return text
    }

    open suspend fun formatValue(context: Context, unit: MeasureUnit, value: Double): String {
        if (abs(value) > 1e5 || abs(value) < 1e-3) {
            return DecimalFormat("#.###E0").apply {
            }.format(value)
        }

        return DecimalFormat("#.###").apply {
        }.format(value)

    }
}