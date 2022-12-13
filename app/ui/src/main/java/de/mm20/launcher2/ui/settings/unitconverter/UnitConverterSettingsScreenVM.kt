package de.mm20.launcher2.ui.settings.unitconverter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UnitConverterSettingsScreenVM: ViewModel(), KoinComponent {

    private val dataStore: LauncherDataStore by inject()

    val unitConverter = dataStore.data.map { it.unitConverterSearch.enabled }.asLiveData()
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

    val currencyConverter = dataStore.data.map { it.unitConverterSearch.currencies }.asLiveData()
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