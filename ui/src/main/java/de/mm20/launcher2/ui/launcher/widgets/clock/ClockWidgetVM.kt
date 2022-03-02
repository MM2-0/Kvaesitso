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
                val providers = mutableListOf<PartProvider>()
                if (it.datePart) providers += DatePartProvider()
                if (it.musicPart) providers += MusicPartProvider()
                if (it.batteryPart) providers += BatteryPartProvider()
                if (it.alarmPart) providers += AlarmPartProvider()
                partProviders.value = providers
            }
        }
    }

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
                for (i in 1 until providers.size) {
                    if (r[i - 1] < r[i]) {
                        prov = providers[i]
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

    private fun getTime(context: Context): Flow<Long> = callbackFlow {
        trySendBlocking(System.currentTimeMillis())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(System.currentTimeMillis())
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        })
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    private fun updatePartsTime(time: Long) {
        partProviders.value.forEach { it.setTime(time) }
    }

    fun launchClockApp(context: Context) {
        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    suspend fun onActive(context: Context) {
        getTime(context).collectLatest {
            time.value = it
            updatePartsTime(it)
        }
    }
}