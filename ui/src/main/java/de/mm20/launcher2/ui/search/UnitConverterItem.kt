package de.mm20.launcher2.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.unitconverter.Dimension

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
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getDimensionIcon(unitConverter.dimension),
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
                    val text = AnnotatedString.Builder().apply {
                        append(" ${unit.formattedName} (${unit.symbol})")
                    }.toAnnotatedString()
                    Text(
                        text = "${unit.formattedName} (${unit.symbol})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(end = 16.dp, bottom = 12.dp, start = 8.dp)
                    )
                }
            }

        }
        if (!showAll && unitConverter.values.size > 5) {
            TextButton(
                onClick = { showAll = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 12.dp)
            ) {
                Text(text = stringResource(id = R.string.unit_converter_show_all))
            }
        }
    }
}

fun getDimensionIcon(dimension: Dimension): ImageVector {
    return when (dimension) {
        Dimension.Mass -> Icons.Rounded.FitnessCenter
        Dimension.Length -> Icons.Rounded.Straighten
        Dimension.Velocity -> Icons.Rounded.Speed
        Dimension.Volume -> TODO()
        Dimension.Area -> Icons.Rounded.SquareFoot
        Dimension.Currency -> Icons.Rounded.Toll
        Dimension.Data -> Icons.Rounded.Storage
        Dimension.Bitrate -> TODO()
        Dimension.Pressure -> TODO()
        Dimension.Energy -> Icons.Rounded.Bolt
        Dimension.Frequency -> TODO()
        Dimension.Temperature -> Icons.Rounded.Thermostat
        Dimension.Time -> Icons.Rounded.Schedule
    }
}