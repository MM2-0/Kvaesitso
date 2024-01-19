package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class ClockWidgetParts(
    val date: Boolean,
    val music: Boolean = false,
    val battery: Boolean = false,
    val alarm: Boolean = false,
)

class ClockWidgetSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val compact
        get() = launcherDataStore.data.map { it.clockWidgetCompact }

    fun setCompact(compact: Boolean) {
        launcherDataStore.update {
            it.copy(clockWidgetCompact = compact)
        }
    }

    val parts
        get() = launcherDataStore.data.map {
            ClockWidgetParts(
                date = it.clockWidgetDatePart,
                music = it.clockWidgetMusicPart,
                battery = it.clockWidgetBatteryPart,
                alarm = it.clockWidgetAlarmPart,
            )
        }.distinctUntilChanged()

    fun setDatePart(datePart: Boolean) {
        launcherDataStore.update {
            it.copy(clockWidgetDatePart = datePart)
        }
    }

    fun setMusicPart(musicPart: Boolean) {
        launcherDataStore.update {
            it.copy(clockWidgetMusicPart = musicPart)
        }
    }

    fun setBatteryPart(batteryPart: Boolean) {
        launcherDataStore.update {
            it.copy(clockWidgetBatteryPart = batteryPart)
        }
    }

    fun setAlarmPart(alarmPart: Boolean) {
        launcherDataStore.update {
            it.copy(clockWidgetAlarmPart = alarmPart)
        }
    }

    val fillHeight
        get() = launcherDataStore.data.map { it.clockWidgetFillHeight }

    fun setFillHeight(fillHeight: Boolean) {
        launcherDataStore.update {
            it.copy(clockWidgetFillHeight = fillHeight)
        }
    }

    val dock
        get() = launcherDataStore.data.map { it.homeScreenDock }

    fun setDock(dock: Boolean) {
        launcherDataStore.update {
            it.copy(homeScreenDock = dock)
        }
    }

    val alignment
        get() = launcherDataStore.data.map { it.clockWidgetAlignment }

    fun setAlignment(alignment: ClockWidgetAlignment) {
        launcherDataStore.update {
            it.copy(clockWidgetAlignment = alignment)
        }
    }

    val clockStyle
        get() = launcherDataStore.data.map { it.clockWidgetStyle }

    fun setClockStyle(clockStyle: ClockWidgetStyle) {
        launcherDataStore.update {
            it.copy(clockWidgetStyle = clockStyle)
        }
    }

    fun setColor(color: ClockWidgetColors) {
        launcherDataStore.update {
            it.copy(clockWidgetColors = color)
        }
    }

    val color
        get() = launcherDataStore.data.map { it.clockWidgetColors }
}