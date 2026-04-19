package de.mm20.launcher2.ui.launcher.search.calculator

import androidx.compose.foundation.lazy.LazyListScope
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.ui.launcher.search.common.list.ListItemSurface

fun LazyListScope.CalculatorResults(
    calculator: List<Calculator>,
    reverse: Boolean,
) {
    if (calculator.isNotEmpty()) {
        item(key = "calculator") {
            ListItemSurface(
                isFirst = true,
                isLast = true,
                reverse = reverse,
            ) {
                CalculatorItem(calculator = calculator.first())
            }
        }
    }
}