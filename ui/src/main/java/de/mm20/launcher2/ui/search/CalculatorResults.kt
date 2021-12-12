package de.mm20.launcher2.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.calculator.CalculatorViewModel
import de.mm20.launcher2.ui.component.SectionDivider
import org.koin.androidx.compose.getViewModel

@Composable
fun calculatorItem(): LazyListScope.() -> Unit {
    val viewModel: CalculatorViewModel = getViewModel()
    val calculator by viewModel.calculator.observeAsState()
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