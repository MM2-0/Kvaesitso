package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.icons.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*

class BatteryPartProvider : PartProvider {

    private val batteryInfo = MutableStateFlow<BatteryInfo?>(null)

    override fun getRanking(context: Context): Flow<Int> = channelFlow {
        val chargingInfo = getChargingInfo(context)

        chargingInfo.collectLatest {
            batteryInfo.value = it
            if (it.charging) {
                send(10)
            } else if (it.level <= 15) {
                send(55)
            } else {
                send(0)
            }
        }
    }

    private val time = MutableStateFlow(System.currentTimeMillis())

    override fun setTime(time: Long) {
        super.setTime(time)
        this.time.value = time
    }

    @Composable
    override fun Component(layout: Settings.ClockWidgetSettings.ClockWidgetLayout) {

        val batteryInfo by this.batteryInfo.collectAsState(null)

        batteryInfo?.let {

            if (layout == Settings.ClockWidgetSettings.ClockWidgetLayout.Vertical) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getBatteryIcon(it),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = "${it.level} %",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (it.charging) {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = it.fullIn?.let {
                                val m = (it / 60000).toInt()
                                pluralStringResource(R.plurals.battery_part_remaining_charge_time, m, m)
                            } ?: stringResource(R.string.battery_part_charging),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (layout == Settings.ClockWidgetSettings.ClockWidgetLayout.Horizontal) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.padding(end = 12.dp).size(36.dp),
                        imageVector = getBatteryIcon(it),
                        contentDescription = null
                    )
                    Column {
                        Text(
                            text = "${it.level} %",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (it.charging) {
                            Text(
                                text = it.fullIn?.let {
                                    val m = (it / 60000).toInt()
                                    pluralStringResource(R.plurals.battery_part_remaining_charge_time, m, m)
                                } ?: stringResource(R.string.battery_part_charging),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getBatteryIcon(batteryInfo: BatteryInfo): ImageVector {
        return if (batteryInfo.charging) {
            when (batteryInfo.level) {
                in 0..12 -> Icons.Rounded.BatteryCharging0Bar
                in 13..25 -> Icons.Rounded.BatteryCharging1Bar
                in 26..37 -> Icons.Rounded.BatteryCharging2Bar
                in 38..50 -> Icons.Rounded.BatteryCharging3Bar
                in 51..63 -> Icons.Rounded.BatteryCharging4Bar
                in 64..75 -> Icons.Rounded.BatteryCharging5Bar
                in 76..88 -> Icons.Rounded.BatteryCharging6Bar
                else -> Icons.Rounded.BatteryChargingFull
            }
        } else {
            when (batteryInfo.level) {
                in 0..12 -> Icons.Rounded.Battery0Bar
                in 13..25 -> Icons.Rounded.Battery1Bar
                in 26..37 -> Icons.Rounded.Battery2Bar
                in 38..50 -> Icons.Rounded.Battery3Bar
                in 51..63 -> Icons.Rounded.Battery4Bar
                in 64..75 -> Icons.Rounded.Battery5Bar
                in 76..88 -> Icons.Rounded.Battery6Bar
                else -> Icons.Rounded.BatteryFull
            }
        }
    }

    private fun getChargingInfo(context: Context): Flow<BatteryInfo> = callbackFlow {
        val batteryManager: BatteryManager = context.getSystemService() ?: return@callbackFlow

        trySendBlocking(
            BatteryInfo(
                level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                charging = batteryManager.isCharging,
                fullIn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    batteryManager.computeChargeTimeRemaining().takeIf { it >= 0 }
                } else null,
            )
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(
                    BatteryInfo(
                        level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                        charging = intent?.getIntExtra(
                            BatteryManager.EXTRA_STATUS,
                            BatteryManager.BATTERY_STATUS_UNKNOWN
                        ) == BatteryManager.BATTERY_STATUS_CHARGING,
                        fullIn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            batteryManager.computeChargeTimeRemaining().takeIf { it >= 0 }
                        } else null,
                    )
                )
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}

private data class BatteryInfo(
    val level: Int,
    val charging: Boolean,
    val fullIn: Long?,
)