package de.mm20.launcher2.ui.launcher.search.unitconverter

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.CurrencyUnitConverter
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.TextButtonWithTrailingIconContentPadding
import de.mm20.launcher2.ui.launcher.search.common.list.ListItemSurface
import de.mm20.launcher2.unitconverter.Dimension
import java.util.Date
import kotlin.math.min

fun LazyListScope.UnitConverterResults(
    converters: List<UnitConverter>,
    truncate: Boolean,
    onShowAll: () -> Unit,
    reverse: Boolean,
) {
    if (converters.isNotEmpty()) {
        val converter = converters.first()
        item(
            key = "converter-header",
        ) {
            ListItemSurface(
                isFirst = true,
                reverse = reverse,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = converter.inputValue.let { "${it.formattedValue} ${it.formattedName}" },
                            style = MaterialTheme.typography.titleLarge,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                        if (converter is CurrencyUnitConverter) {
                            var showDisclaimer by remember { mutableStateOf(false) }
                            val df = DateFormat.getDateInstance(DateFormat.SHORT)
                            Row(
                                modifier = Modifier
                                    .padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${df.format(Date(converter.updateTimestamp))} • ",
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
                            }
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
                                                df.format(Date(converter.updateTimestamp))
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = getDimensionIcon(converter.dimension),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(
                                top = 20.dp,
                                bottom = 20.dp,
                                start = 16.dp,
                                end = 18.dp
                            )
                            .size(24.dp)
                    )
                }
            }
        }
        val count = if (truncate) min(5, converter.values.size) else converter.values.size
        items(
            count,
            key = { "converter-${converter.values[it].symbol}" }
        ) {
            val value = converter.values[it]
            ListItemSurface(
                isLast = it == converter.values.lastIndex,
                reverse = reverse,
            ) {
                Column {
                    val clipboardManager = LocalClipboardManager.current
                    Row(
                        modifier = Modifier
                            .clickable {
                                clipboardManager.setText(buildAnnotatedString {
                                    append(value.value.toString())
                                    append(" ")
                                    append(value.symbol)
                                })
                            }
                            .padding(
                                start = 16.dp,
                                end = 12.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.widthIn(min = 48.dp),
                            text = value.formattedValue,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            text = value.formattedName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.shapes.extraSmall,
                                )
                                .height(36.dp)
                                .widthIn(min = 36.dp)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                textAlign = TextAlign.Center,
                                text = value.symbol,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (truncate && converter.values.size > 5) {
            item(
                key = "converter-footer"
            ) {
                ListItemSurface(
                    isLast = true,
                    reverse = reverse,
                ) {
                    TextButton(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(4.dp),
                        onClick = onShowAll,
                        contentPadding = ButtonDefaults.TextButtonWithTrailingIconContentPadding,
                    ) {
                        Text(stringResource(R.string.unit_converter_show_all))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            null,
                            modifier = Modifier
                                .padding(start = ButtonDefaults.IconSpacing)
                                .size(ButtonDefaults.IconSize)
                        )
                    }
                }
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