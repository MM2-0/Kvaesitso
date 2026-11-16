package de.mm20.launcher2.preferences.battery

import de.mm20.launcher2.preferences.BatteryGroup
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class BatterySettings internal constructor(
    private val dataStore: LauncherDataStore,
) {
    val data: Flow<BatteryGroup> get() = dataStore.data.map { it.battery }.distinctUntilChanged()

    fun update(transform: (BatteryGroup) -> BatteryGroup) {
        dataStore.update { it.copy(battery = transform(it.battery)) }
    }
}
