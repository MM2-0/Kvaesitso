package de.mm20.launcher2.ui.launcher.search.unitconverter

import android.icu.text.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.CurrencyUnitConverter
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.ui.R
import java.util.Date

@Composable
fun UnitConverterItem(
    unitConverter: UnitConverter,
) {
    var showAll by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = unitConverter.inputValue.let { "${it.formattedValue} ${it.formattedName}" },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .size(48.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(getDimensionIcon(unitConverter.dimension)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Row {
            Column {
                for ((i, unit) in unitConverter.values.withIndex()) {
                    if (!showAll && i >= 5) break
                    Text(
                        text = unit.formattedValue,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .padding(start = 16.dp, bottom = 12.dp)
                    )
                }


            }
            Column {
                for ((i, unit) in unitConverter.values.withIndex()) {
                    if (!showAll && i >= 5) break
                    Text(
                        text = "${unit.formattedName} (${unit.symbol})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(end = 16.dp, bottom = 12.dp, start = 8.dp)
                    )
                }
            }

        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                var showDisclaimer by remember { mutableStateOf(false) }
                (unitConverter as? CurrencyUnitConverter)?.let {
                    val df = DateFormat.getDateInstance(DateFormat.SHORT)
                    Text(
                        text = "${df.format(Date(it.updateTimestamp))} • ",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = stringResource(id = R.string.disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            showDisclaimer = true
                        }
                    )
                    if (showDisclaimer) {
                        AlertDialog(
                            onDismissRequest = { showDisclaimer = false },
                            confirmButton = {
                                TextButton(onClick = { showDisclaimer = false }) {
                                    Text(text = stringResource(id = R.string.close))
                                }
                            },
                            title = { Text(stringResource(id = R.string.disclaimer)) },
                            text = {
                                Text(
                                    stringResource(
                                        id = R.string.disclaimer_currency_converter,
                                        df.format(Date(it.updateTimestamp))
                                    )
                                )
                            }
                        )
                    }
                }
            }
            if (!showAll && unitConverter.values.size > 5) {
                TextButton(
                    onClick = { showAll = true },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.show_all),
                    )
                }
            }
        }
    }
}
