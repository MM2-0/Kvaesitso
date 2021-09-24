package de.mm20.launcher2.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.calculator.CalculatorViewModel
import de.mm20.launcher2.ui.SectionDivider

@Composable
fun calculatorItem(): LazyListScope.() -> Unit {
    val calculator by viewModel<CalculatorViewModel>().calculator.observeAsState()
    return {
        calculator?.let {
            item {
                Card(
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {

                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                            Text(it.getBeatifiedTerm())
                        }
                        Text(
                            text = "= ${it.formattedString}",
                            style = MaterialTheme.typography.h1,
                            modifier = Modifier.align(Alignment.End),
                        )
                        if (it.term.matches(Regex("(0x|0b)?[0-9]+"))) {
                            Text(
                                it.formattedBinaryString,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 8.dp),
                            )
                            Text(
                                it.formattedHexString,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.End),
                            )
                            Text(
                                it.formattedOctString,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                }
            }
            SectionDivider()
        }
    }
}