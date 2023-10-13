package de.mm20.launcher2.ui.launcher.widgets.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherDataStore
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private val partProviders = dataStore.data.map { it.clockWidget }.distinctUntilChanged().map {
        val providers = mutableListOf<PartProvider>()
        if (it.datePart) providers += DatePartProvider()
        if (it.favoritesPart) providers += FavoritesPartProvider()
        if (it.musicPart) providers += MusicPartProvider()
        if (it.batteryPart) providers += BatteryPartProvider()
        if (it.alarmPart) providers += AlarmPartProvider()
        providers
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun getActiveParts(context: Context): Flow<List<PartProvider>> = channelFlow {
        partProviders.collectLatest { providers ->
            if (providers.isEmpty()) {
                send(emptyList())
                return@collectLatest
            }
            val rankings = providers.map { it.getRanking(context).map { r -> r to it } }
            combine(rankings) { r ->
                val sorted = r.sortedBy { it.first }
                sorted.takeLast(if (sorted.last().second is FavoritesPartProvider) 2 else 1)
                    .map { it.second }
            }.collectLatest {
                send(it)
            }
        }
    }

    val layout = dataStore.data.map { it.clockWidget.layout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val clockStyle = dataStore.data.map { it.clockWidget.clockStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val color = dataStore.data.map { it.clockWidget.color }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val alignment = dataStore.data.map { it.clockWidget.alignment }
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