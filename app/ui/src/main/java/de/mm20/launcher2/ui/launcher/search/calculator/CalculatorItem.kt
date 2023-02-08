package de.mm20.launcher2.ui.launcher.search.calculator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.Calculator

@Composable
fun CalculatorItem(
    calculator: Calculator
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {

        Text(
            text = calculator.getBeatifiedTerm(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = "= ${calculator.formattedString}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.End),
        )
        if (calculator.term.matches(Regex("(0x|0b)?[0-9]+"))) {
            Text(
                calculator.formattedBinaryString,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
            )
            Text(
                calculator.formattedHexString,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.align(Alignment.End),
            )
            Text(
                calculator.formattedOctString,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}