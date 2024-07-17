package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*

class AlarmPartProvider : PartProvider {

    private val nextAlarmTime = mutableStateOf<Long?>(null)

    private val time = MutableStateFlow(System.currentTimeMillis())

    override fun setTime(time: Long) {
        this.time.value = time
    }

    override fun getRanking(context: Context): Flow<Int> = channelFlow {
        val nextAlarm = getNextAlarmTime(context)
        nextAlarm.collectLatest { alarm ->
            nextAlarmTime.value = alarm
            if (alarm == null) {
                send(0)
            } else {
                time.collectLatest {
                    if (alarm > it + 8 * 60 * 60 * 1000) {
                        send(0)
                    } else {
                        send(60)
                    }
                }
            }
        }
    }

    private fun getNextAlarmTime(context: Context): Flow<Long?> = callbackFlow {
        val alarmManager: AlarmManager = context.getSystemService() ?: return@callbackFlow
        trySendBlocking(alarmManager.nextAlarmClock?.triggerTime)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(alarmManager.nextAlarmClock?.triggerTime)
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        })
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current

        val alarmTime by nextAlarmTime
        val time by this.time.collectAsState(System.currentTimeMillis())

        alarmTime?.let {

            if (!compactLayout) {

                TextButton(
                    onClick = {
                        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Alarm,
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = DateUtils.getRelativeTimeSpanString(
                            it,
                            time,
                            DateUtils.MINUTE_IN_MILLIS
                        )
                            .toString(),
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current
                    )
                ) {
                    Icon(
                        modifier = Modifier.padding(end = 8.dp).size(32.dp),
                        imageVector = Icons.Rounded.Alarm,
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = DateUtils.getRelativeTimeSpanString(
                            it,
                            time,
                            DateUtils.MINUTE_IN_MILLIS
                        )
                            .toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}