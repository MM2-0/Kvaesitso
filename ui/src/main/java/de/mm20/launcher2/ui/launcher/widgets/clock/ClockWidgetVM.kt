package de.mm20.launcher2.ui.launcher.widgets.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.DatePartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.MusicPartProvider
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
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
        partProviders.value = listOf(DatePartProvider(), MusicPartProvider())
    }

    fun getActivePart(): Flow<PartProvider?> = channelFlow {
        partProviders.collectLatest { providers ->
            if (providers.isEmpty()) {
                send(null)
                return@collectLatest
            }
            val rankings = providers.map { it.getRanking() }
            combine(rankings) { r ->
                var prov = providers[0]
                for (i in 1 until providers.size) {
                    if (r[i - 1] < r[i]) {
                        prov = providers[i]
                    }
                }
                Log.d("MM20", prov.toString())
                return@combine prov
            }.collectLatest {
                send(it)
            }
        }
    }


    val layout = dataStore.data.map { it.clockWidget.layout }.asLiveData()
    val clockStyle = dataStore.data.map { it.clockWidget.clockStyle }.asLiveData()

    fun getTime(context: Context): Flow<Long> = callbackFlow {
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

    fun launchClockApp(context: Context) {
        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}