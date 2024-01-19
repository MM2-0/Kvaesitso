package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class CalculatorSearchSettings internal constructor(
    private val dataStore: LauncherDataStore,
){
    val enabled
        get() = dataStore.data.map { it.calculatorEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update {
            it.copy(calculatorEnabled = enabled)
        }
    }
}