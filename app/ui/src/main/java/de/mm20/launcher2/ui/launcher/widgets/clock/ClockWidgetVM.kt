package de.mm20.launcher2.ui.launcher.widgets.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private val partProviders = MutableStateFlow<List<PartProvider>>(emptyList())

    init {
        viewModelScope.launch {
            dataStore.data.map { it.clockWidget }.distinctUntilChanged().collectLatest {
                val providers = mutableListOf<PartProvider>(
                    DatePartProvider()
                )
                if (it.musicPart) providers += MusicPartProvider()
                if (it.batteryPart) providers += BatteryPartProvider()
                if (it.alarmPart) providers += AlarmPartProvider()
                partProviders.value = providers
            }
        }
    }

    val withFavorites = dataStore.data.map { it.clockWidget.favoritesPart }.asLiveData()
    val favoritesPartProvider = FavoritesPartProvider()

    val time = MutableStateFlow(System.currentTimeMillis())

    fun getActivePart(context: Context): Flow<PartProvider?> = channelFlow {
        partProviders.collectLatest { providers ->
            if (providers.isEmpty()) {
                send(null)
                return@collectLatest
            }
            val rankings = providers.map { it.getRanking(context) }
            combine(rankings) { r ->
                var prov = providers[0]
                var ranking = r[0]
                for (i in 1 until providers.size) {
                    if (ranking < r[i]) {
                        prov = providers[i]
                        ranking = r[i]
                    }
                }
                return@combine prov
            }.collectLatest {
                send(it)
            }
        }
    }


    val layout = dataStore.data.map { it.clockWidget.layout }.asLiveData()
    val clockStyle = dataStore.data.map { it.clockWidget.clockStyle }.asLiveData()

    val color = dataStore.data.map { it.clockWidget.color }.asLiveData()

    fun updateTime(time: Long) {
        partProviders.value.forEach { it.setTime(time) }
    }

    fun launchClockApp(context: Context) {
        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}