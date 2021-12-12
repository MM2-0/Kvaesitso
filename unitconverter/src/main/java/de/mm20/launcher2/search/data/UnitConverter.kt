package de.mm20.launcher2.search.data

import android.content.Context
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.UnitValue
import de.mm20.launcher2.unitconverter.converters.*

open class UnitConverter(
        val dimension: Dimension,
        val inputValue: UnitValue,
        val values: List<UnitValue>
) {
    companion object {

        suspend fun search(context: Context, query: String): UnitConverter? {
            if (!LauncherPreferences.instance.searchUnitConverter) return null
            if (!query.matches(Regex("[0-9,.:]+ [A-Za-z/²³°.]+")) && !query.matches(Regex("[0-9,.:]+ [A-Za-z/²³°.]+ >> [A-Za-z/²³°]+"))) return null
            val valueStr: String
            val unitStr: String
            val targetUnitStr: String?

            query.split(" ").also {
                valueStr = it.get(0)
                unitStr = it.get(1)
                targetUnitStr = it.getOrNull(3)
            }
            val value = valueStr.toDoubleOrNull() ?: valueStr.replace(',', '.').toDoubleOrNull()
            ?: return null

            val converters = listOf(
                    lazy { MassConverter(context) },
                    lazy { LengthConverter(context) },
                    lazy { CurrencyConverter() },
                    lazy { DataConverter(context) },
                    lazy { TimeConverter(context) },
                    lazy { VelocityConverter(context) },
                    lazy { AreaConverter(context) },
                    lazy { TemperatureConverter(context) }
            )
            for (conv in converters) {
                val converter = conv.value
                if (!converter.isValidUnit(unitStr)) continue
                if (targetUnitStr != null && !converter.isValidUnit(targetUnitStr)) continue
                return converter.convert(context, unitStr, value, targetUnitStr)
            }
            return null
        }
    }
}

