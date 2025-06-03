package de.mm20.launcher2.ui.settings.unitconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UnitConverterSettingsScreenVM: ViewModel(), KoinComponent {

    private val settings: UnitConverterSettings by inject()
    private val repository: UnitConverterRepository by inject()

    val unitConverter = settings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setUnitConverter(unitConverter: Boolean) {
        settings.setEnabled(unitConverter)
    }

    val currencyConverter = settings.currencies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCurrencyConverter(currencyConverter: Boolean) {
        settings.setCurrencies(currencyConverter)
    }

    val availableConverters = settings.currencies.map {
        repository.getAvailableConverters(includeCurrencies = it)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100), 1)

    val availableUnits = availableConverters.map {
        it.map { converter -> converter.getSupportedUnits() }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100), 1)

    val customVerb = settings.customVerb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "para")
    fun setCustomVerb(customVerb: String) {
        settings.setCustomVerb(customVerb)
    }
}