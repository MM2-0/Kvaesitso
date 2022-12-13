package de.mm20.launcher2.search.data

import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.unitconverter.Dimension
import de.mm20.launcher2.unitconverter.UnitValue

open class UnitConverter(
    val dimension: Dimension,
    val inputValue: UnitValue,
    val values: List<UnitValue>
): Searchable
