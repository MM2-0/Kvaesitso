package de.mm20.launcher2.ui.settings.clockwidget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetSettingsScreenVM : ViewModel(), KoinComponent {
    private val settings: ClockWidgetSettings by inject()
    val compact = settings.compact
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCompact(compact: Boolean) {
        settings.setCompact(compact)
    }

    val clockStyle = settings.clockStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setClockStyle(clockStyle: ClockWidgetStyle) {
        settings.setClockStyle(clockStyle)
    }

    val color = settings.color
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setColor(color: ClockWidgetColors) {
        settings.setColor(color)
    }

    val showSeconds = settings.showSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setShowSeconds(showSeconds: Boolean) {
        settings.setShowSeconds(showSeconds)
    }

    val useThemeColor = settings.useThemeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

    fun setUseThemeColor(boolean: Boolean) {
        settings.setUseThemeColor(boolean)
    }

    val fillHeight = settings.fillHeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setFillHeight(fillHeight: Boolean) {
        settings.setFillHeight(fillHeight)
    }

    val dock = settings.dock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val parts = settings.parts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDatePart(datePart: Boolean) {
        settings.setDatePart(datePart)
    }

    fun setFavoritesPart(favoritesPart: Boolean) {
        settings.setDock(favoritesPart)
    }

    fun setBatteryPart(batteryPart: Boolean) {
        settings.setBatteryPart(batteryPart)
    }

    fun setMusicPart(musicPart: Boolean) {
        settings.setMusicPart(musicPart)
    }

    fun setAlarmPart(alarmPart: Boolean) {
        settings.setAlarmPart(alarmPart)
    }

    val alignment = settings.alignment
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setAlignment(alignment: ClockWidgetAlignment) {
        settings.setAlignment(alignment)
    }
}