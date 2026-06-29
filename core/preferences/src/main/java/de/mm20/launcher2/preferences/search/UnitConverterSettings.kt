package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class UnitConverterSettingsData(
    val enabled: Boolean,
    val currencies: Boolean,
)

class UnitConverterSettings internal constructor(
    private val dataStore: LauncherDataStore,
) : Flow<UnitConverterSettingsData> by (dataStore.data.map {
    UnitConverterSettingsData(
        enabled = it.unitConverterEnabled,
        currencies = it.unitConverterCurrencies,
    )
}.distinctUntilChanged()) {
    val enabled: Flow<Boolean>
        get() = dataStore.data.map { it.unitConverterEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(unitConverterEnabled = enabled) }
    }

    val currenciesEnabled: Flow<Boolean>
        get() = dataStore.data.map { it.unitConverterCurrencies }.distinctUntilChanged()

    fun setCurrenciesEnabled(currencies: Boolean) {
        dataStore.update { it.copy(unitConverterCurrencies = currencies) }
    }

    val preferredCurrencies: Flow<List<String>>
        get() = dataStore.data.map { it.localeCurrencies }.distinctUntilChanged()
}