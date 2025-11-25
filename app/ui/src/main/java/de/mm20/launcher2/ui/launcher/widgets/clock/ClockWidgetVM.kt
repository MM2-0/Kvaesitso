package de.mm20.launcher2.ui.launcher.widgets.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.AlarmPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.BatteryPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.DatePartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.FavoritesPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.MusicPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetVM : ViewModel(), KoinComponent {
    private val settings: ClockWidgetSettings by inject()

    private val partProviders = settings.parts.map {
        val providers = mutableListOf<PartProvider>()
        if (it.date) providers += DatePartProvider()
        if (it.music) providers += MusicPartProvider()
        if (it.battery) providers += BatteryPartProvider()
        if (it.alarm) providers += AlarmPartProvider()
        providers
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun getActiveParts(context: Context): Flow<List<PartProvider>> = channelFlow {
        combine(partProviders, partProviders.map { it.map { it.getRanking(context) }.merge() }) { providers, _ ->
            providers
        }.collectLatest { providers ->
            val rankings = providers.map { it.getRanking(context).map { r -> r to it } }
            combine(rankings) { r ->
                r.toList()
                    .filter { it.first > 0 }
                    .sortedByDescending { it.first }
                    .map { it.second }
            }.collectLatest { send(it) }
        }
    }

    val compactLayout = settings.compact
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val clockStyle = settings.clockStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val color = settings.color
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val alignment = settings.alignment
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val dockProvider = settings.dock
        .map { if (it) FavoritesPartProvider() else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun updateTime(time: Long) {
        partProviders.value.forEach { it.setTime(time) }
    }

    fun launchClockApp(context: Context) {
        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}