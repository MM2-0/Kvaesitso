package de.mm20.launcher2.ui.settings.unitconverter

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import de.mm20.launcher2.unitconverter.converters.Converter
import de.mm20.launcher2.unitconverter.converters.CurrencyConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SupportedUnitsScreenVM: ViewModel(), KoinComponent {
    val loading = mutableStateOf(false)
    val convertersList = mutableStateOf(emptyList<Converter>())
    val currenciesList = mutableStateOf(emptyList<String>())

    private val settings: UnitConverterSettings by inject()
    private val repository: UnitConverterRepository by inject()

    fun loadCurrencies() {
        loading.value = true
        viewModelScope.launch(Dispatchers.Default) {
            convertersList.value = repository.availableConverters(
                settings.currencies.distinctUntilChanged().first()
            )

            val currencyConverter = convertersList.value.find { it is CurrencyConverter }
            if (currencyConverter != null) {
                currenciesList.value = (currencyConverter as CurrencyConverter).getAbbreviations()
            }
        }
        loading.value = false
    }
}