package de.mm20.launcher2.ui.settings.clockwidget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetSettingsScreenVM : ViewModel(), KoinComponent {
    private val settings: ClockWidgetSettings by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val iconService: IconService by inject()

    val compact = settings.compact
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCompact(compact: Boolean) {
        settings.setCompact(compact)
    }

    val availableClockStyles = combine(settings.digital1, settings.custom) { digital1, custom ->
        listOf(
            digital1,
            ClockWidgetStyle.Digital2,
            ClockWidgetStyle.Analog,
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

    val timeFormat = settings.timeFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), TimeFormat.System)

    fun setTimeFormat(timeFormat: TimeFormat) {
        settings.setTimeFormat(timeFormat)
    }

    val useThemeColor = settings.useThemeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setUseThemeColor(boolean: Boolean) {
        settings.setUseThemeColor(boolean)
    }

    val fillHeight = settings.fillHeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setFillHeight(fillHeight: Boolean) {
        settings.setFillHeight(fillHeight)
    }

    val tapAction = settings.tapAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setTapAction(action: GestureAction) {
        settings.setTapAction(action)
    }

    val tapApp: Flow<SavableSearchable?> = tapAction
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 10000), null)

    fun setTapApp(searchable: SavableSearchable?) {
        searchable?.let { searchableRepository.insert(it) } ?: return
        setTapAction(GestureAction.Launch(searchable.key))
    }

    val parts = settings.parts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDatePart(datePart: Boolean) {
        settings.setDatePart(datePart)
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

    fun getIcon(searchable: SavableSearchable?, size: Int): Flow<LauncherIcon?> {
        if (searchable == null) return emptyFlow()
        return iconService.getIcon(searchable, size)
    }
}