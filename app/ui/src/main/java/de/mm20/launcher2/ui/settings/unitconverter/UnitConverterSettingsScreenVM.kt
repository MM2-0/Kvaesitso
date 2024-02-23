package de.mm20.launcher2.ui.settings.unitconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UnitConverterSettingsScreenVM: ViewModel(), KoinComponent {

    private val settings: UnitConverterSettings by inject()

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
}