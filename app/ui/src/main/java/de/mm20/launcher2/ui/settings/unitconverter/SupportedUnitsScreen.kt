package de.mm20.launcher2.ui.settings.unitconverter

import android.icu.util.Currency
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.unitconverter.MeasureUnit
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import de.mm20.launcher2.unitconverter.converters.CurrencyConverter
import de.mm20.launcher2.unitconverter.converters.SimpleFactorConverter
import de.mm20.launcher2.unitconverter.converters.TemperatureConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.androidx.compose.inject
import kotlin.coroutines.CoroutineContext

@Composable
fun SupportedUnitsScreen() {
    val scope = rememberCoroutineScope()
    val settings: UnitConverterSettings by inject()
    val repository: UnitConverterRepository by inject()

    val currencies = settings.currencies.collectAsState(initial = false).value

    PreferenceScreen(title = "Supported units") {
        for (converter in repository.availableConverters(currencies)) {
            item {
                val units = buildString {
                    when (converter) {
                        is SimpleFactorConverter -> {
                            converter.standardUnits.forEachIndexed { index, unit ->
                                if (index > 0) append(", ")
                                append(pluralStringResource(unit.nameResource, 1))
                                append(" (${unit.symbol})")
                            }
                        }

                        is TemperatureConverter -> {
                            converter.units.forEachIndexed { index, unit ->
                                if (index > 0) append(", ")
                                append(pluralStringResource(unit.nameResource, 1))
                                append(" (${unit.symbol})")
                            }

                        }
                        is CurrencyConverter -> {
                            scope.launch {
                                val abbreviations = converter.getAbbreviations()
                                abbreviations.forEachIndexed { index, unit ->
                                    if (index > 0) append(", ")
                                    append(Currency.getInstance(unit)?.displayName ?: unit)
                                    append(" ($unit)")
                                }
                            }
                        }
                    }
                }
                Preference(
                    title = converter.dimension.name,
                    summary = units
                )
            }
        }

    }
}