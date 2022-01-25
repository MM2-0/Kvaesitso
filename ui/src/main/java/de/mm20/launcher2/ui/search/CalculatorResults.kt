package de.mm20.launcher2.ui.search

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.component.SectionDivider
import de.mm20.launcher2.ui.launcher.search.SearchVM

@Composable
fun calculatorItem(): LazyListScope.() -> Unit {
    val viewModel: SearchVM by viewModel()
    val calculator by viewModel.calculatorResult.observeAsState(null)
    return {
        calculator?.let {
            item {
                Card(
                    elevation = 0.dp
                ) {
                    CalculatorItem(it)
                }
            }
            SectionDivider()
        }
    }
}