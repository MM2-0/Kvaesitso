package de.mm20.launcher2.ui.launcher.widgets.clock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.AlarmPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.BatteryPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.DatePartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.FavoritesPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.MusicPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetVM : ViewModel(), KoinComponent {
    private val settings: ClockWidgetSettings by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

    private val partProviders = settings.parts.map {
        val providers = mutableListOf<PartProvider>()
        if (it.date) providers += DatePartProvider()
        if (it.music) providers += MusicPartProvider()
        if (it.battery) providers += BatteryPartProvider()
        if (it.alarm) providers += AlarmPartProvider()
        providers
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun getActivePart(context: Context): Flow<PartProvider?> = channelFlow {
        partProviders.collectLatest { providers ->
            if (providers.isEmpty()) {
                send(null)
                return@collectLatest
            }
            val rankings = providers.map { it.getRanking(context).map { r -> r to it } }
            combine(rankings) { r ->
                r.filter { it.first > 0 }.maxByOrNull { it.first }?.second
            }.collectLatest {
                send(it)
            }
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

    private val tapAction = settings.tapAction
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val tapApp: StateFlow<SavableSearchable?> = tapAction
        .flatMapLatest {
            if (it !is GestureAction.Launch || it.key == null) flowOf(null)
            else searchableRepository.getByKeys(listOf(it.key!!)).map {
                it.firstOrNull()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun launchClockApp(context: Context) {
        when(tapAction.value) {
            is GestureAction.Alarms -> {
                context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }

            is GestureAction.Launch -> {
                val view = (context as Activity).window.decorView
                val options = ActivityOptionsCompat.makeScaleUpAnimation(
                    view,
                    0,
                    0,
                    view.width,
                    view.height
                )
                tapApp.value?.launch(context, options.toBundle())
            }

            else -> {}
        }
    }

}