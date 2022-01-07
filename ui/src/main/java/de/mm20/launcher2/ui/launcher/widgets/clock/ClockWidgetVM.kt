package de.mm20.launcher2.ui.launcher.widgets.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.lifecycle.BroadcastReceiverLiveData
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClockWidgetVM: ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

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