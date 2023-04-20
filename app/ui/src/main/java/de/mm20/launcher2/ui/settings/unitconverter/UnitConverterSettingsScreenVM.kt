package de.mm20.launcher2.ui.settings.unitconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UnitConverterSettingsScreenVM: ViewModel(), KoinComponent {

    private val dataStore: LauncherDataStore by inject()

    val unitConverter = dataStore.data.map { it.unitConverterSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setUnitConverter(unitConverter: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setUnitConverterSearch(
                        it.unitConverterSearch.toBuilder()
                            .setEnabled(unitConverter)
                    )
                    .build()
            }
        }
    }

    val currencyConverter = dataStore.data.map { it.unitConverterSearch.currencies }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCurrencyConverter(currencyConverter: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setUnitConverterSearch(
                        it.unitConverterSearch.toBuilder()
                            .setCurrencies(currencyConverter)
                    )
                    .build()
            }
        }
    }
}