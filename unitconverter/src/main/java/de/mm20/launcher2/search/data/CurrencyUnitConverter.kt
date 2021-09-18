package de.mm20.launcher2.search.data

import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.UnitValue

class CurrencyUnitConverter(dimension: Dimension, inputValue: UnitValue, values: List<UnitValue>, val updateTimestamp: Long)
    : UnitConverter(dimension, inputValue, values)