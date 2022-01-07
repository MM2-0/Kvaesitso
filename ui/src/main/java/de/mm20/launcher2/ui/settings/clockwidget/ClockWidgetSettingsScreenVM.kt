package de.mm20.launcher2.ui.settings.clockwidget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()
    val layout = dataStore.data.map { it.clockWidget.layout }.asLiveData()
    fun setLayout(layout: ClockWidgetSettings.ClockWidgetLayout) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setLayout(layout)
                    ).build()
            }
        }
    }

    val clockStyle = dataStore.data.map { it.clockWidget.clockStyle }.asLiveData()
    fun setClockStyle(clockStyle: ClockWidgetSettings.ClockStyle) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setClockStyle(clockStyle)
                    ).build()
            }
        }
    }
}