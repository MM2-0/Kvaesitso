package de.mm20.launcher2.ui.settings.clockwidget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()
    val layout = dataStore.data.map { it.clockWidget.layout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
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

    val clockStyle = dataStore.data.map { it.clockWidget.clockStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

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

    val color = dataStore.data.map { it.clockWidget.color }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setColor(color: ClockWidgetColors) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setColor(color)
                    ).build()
            }
        }
    }

    val fillHeight = dataStore.data.map { it.clockWidget.fillHeight }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setFillHeight(fillHeight: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setFillHeight(fillHeight)
                    ).build()
            }
        }
    }

    val datePart = dataStore.data.map { it.clockWidget.datePart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setDatePart(datePart: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setDatePart(datePart)
                    ).build()
            }
        }
    }

    val favoritesPart = dataStore.data.map { it.clockWidget.favoritesPart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setFavoritesPart(favoritesPart: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setFavoritesPart(favoritesPart)
                    ).build()
            }
        }
    }

    val batteryPart = dataStore.data.map { it.clockWidget.batteryPart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setBatteryPart(batteryPart: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setBatteryPart(batteryPart)
                    ).build()
            }
        }
    }

    val musicPart = dataStore.data.map { it.clockWidget.musicPart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setMusicPart(musicPart: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setMusicPart(musicPart)
                    ).build()
            }
        }
    }

    val alarmPart = dataStore.data.map { it.clockWidget.alarmPart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setAlarmPart(alarmPart: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setClockWidget(
                        it.clockWidget.toBuilder()
                            .setAlarmPart(alarmPart)
                    ).build()
            }
        }
    }
}