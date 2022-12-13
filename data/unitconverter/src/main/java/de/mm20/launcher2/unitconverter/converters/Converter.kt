package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.Dimension

interface Converter {
    val dimension: Dimension

    suspend fun isValidUnit(symbol: String): Boolean

    suspend fun convert(
        context: Context,
        fromUnit: String,
        value: Double,
        toUnit: String?
    ): UnitConverter
}

