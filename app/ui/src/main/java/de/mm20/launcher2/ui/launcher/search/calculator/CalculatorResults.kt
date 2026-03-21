package de.mm20.launcher2.ui.launcher.search.calculator

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.ui.launcher.search.common.list.ListItemSurface

fun LazyGridScope.CalculatorResults(
    calculator: List<Calculator>,
    reverse: Boolean,
) {
    if (calculator.isNotEmpty()) {
        item(
            key = "calculator",
            span = {
                GridItemSpan(maxLineSpan)
            }
        ) {
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