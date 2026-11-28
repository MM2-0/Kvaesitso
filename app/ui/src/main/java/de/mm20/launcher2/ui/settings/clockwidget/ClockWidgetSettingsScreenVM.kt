package de.mm20.launcher2.ui.settings.clockwidget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.BatteryStatusVisibility
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetSettingsScreenVM : ViewModel(), KoinComponent {
    private val settings: ClockWidgetSettings by inject()
    private val uiSettings: UiSettings by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

    val compact = settings.compact
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCompact(compact: Boolean) {
        settings.setCompact(compact)
    }

    val availableClockStyles = combine(settings.digital1, settings.analog, settings.custom) { digital1, analog, custom ->
        listOf(
            digital1,
            ClockWidgetStyle.Digital2,
            analog,
            ClockWidgetStyle.Orbit,
            ClockWidgetStyle.Segment,
            ClockWidgetStyle.Binary,
            custom,
            ClockWidgetStyle.Empty,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setShowSeconds(showSeconds: Boolean) {
        settings.setShowSeconds(showSeconds)
    }

    val monospaced = settings.monospaced
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setMonospaced(monospaced: Boolean) {
        settings.setMonospaced(monospaced)
    }

    val useThemeColor = settings.useThemeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setUseThemeColor(boolean: Boolean) {
        settings.setUseThemeColor(boolean)
    }

    val widgetsOnHome = uiSettings.homeScreenWidgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val fillHeight = settings.fillHeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFillHeight(fillHeight: Boolean) {
        settings.setFillHeight(fillHeight)
    }

    val parts = settings.parts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDatePart(datePart: Boolean) {
        settings.setDatePart(datePart)
    }

    val dateApp: StateFlow<SavableSearchable?> = settings.dateApp.flatMapLatest { key ->
        if (key == null) flowOf(null)
        else searchableRepository.getByKeys(listOf(key)).map { it.firstOrNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDateApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) }
        settings.setDateApp(searchable?.key)
    }

    fun setBatteryPart(batteryPart: BatteryStatusVisibility) {
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

    val useSmartspacer = settings.useSmartspacer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun disableSmartspacer() {
        settings.setUseSmartspacer(false)
    }

}
