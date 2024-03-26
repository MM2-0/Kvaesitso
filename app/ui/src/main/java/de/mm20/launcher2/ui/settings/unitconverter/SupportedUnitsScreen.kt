package de.mm20.launcher2.ui.settings.unitconverter

import android.icu.util.Currency
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.launcher.search.unitconverter.getIcon
import de.mm20.launcher2.unitconverter.converters.CurrencyConverter
import de.mm20.launcher2.unitconverter.converters.SimpleFactorConverter
import de.mm20.launcher2.unitconverter.converters.TemperatureConverter
import org.koin.androidx.compose.inject

@Composable
fun SupportedUnitsScreen() {
    val settings: UnitConverterSettings by inject()
    val viewModel: SupportedUnitsScreenVM = viewModel()
    val loading by viewModel.loading

    val currenciesEnabled by settings.currencies.collectAsState(initial = false)

    LaunchedEffect(currenciesEnabled) {
        viewModel.loadCurrencies()
    }

    PreferenceScreen(title = stringResource(R.string.preference_search_supportedunits)) {
        if (loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        for (converter in viewModel.convertersList.value) {
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
                            viewModel.currenciesList.value.forEachIndexed { index, currency ->
                                if (index > 0) append(", ")
                                append(Currency.getInstance(currency)?.displayName ?: currency)
                                append(" ($currency)")
                            }
                        }
                    }
                }
                Preference(
                    title = stringResource(converter.dimension.resource),
                    icon = converter.dimension.getIcon(),
                    summary = units
                )
            }
        }
    }
}