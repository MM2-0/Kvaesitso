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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.utils.formatPercent
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

    @Composable
    override fun Component(compactLayout: Boolean) {

        val batteryInfo by this.batteryInfo.collectAsState(null)

        batteryInfo?.let {

            if (!compactLayout) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(getBatteryIcon(it)),
                        modifier = Modifier.alignByBaseline(),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
                        text = formatPercent(it.level.toFloat()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (it.charging) {
                        Text(
                            modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
                            text = it.fullIn?.let {
                                val m = (it / 60000).toInt()
                                pluralStringResource(R.plurals.battery_part_remaining_charge_time, m, m)
                            } ?: stringResource(R.string.battery_part_charging),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (compactLayout) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.padding(end = 12.dp).size(36.dp),
                        painter = painterResource(getBatteryIcon(it)),
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

    private fun getBatteryIcon(batteryInfo: BatteryInfo): Int {
        return if (batteryInfo.charging) {
            when (batteryInfo.level) {
                in 0..25 -> R.drawable.battery_charging_20_24px
                in 26..55 -> R.drawable.battery_charging_30_24px
                in 56..85 -> R.drawable.battery_charging_80_24px
                in 86..95 -> R.drawable.battery_charging_90_24px
                else -> R.drawable.battery_charging_full_24px
            }
        } else {
            when (batteryInfo.level) {
                in 0..12 -> R.drawable.battery_0_bar_24px
                in 13..25 -> R.drawable.battery_1_bar_24px
                in 26..37 -> R.drawable.battery_2_bar_24px
                in 38..50 -> R.drawable.battery_3_bar_24px
                in 51..63 -> R.drawable.battery_4_bar_24px
                in 64..75 -> R.drawable.battery_5_bar_24px
                in 76..88 -> R.drawable.battery_6_bar_24px
                else -> R.drawable.battery_full_24px
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
                    batteryManager.computeChargeTimeRemaining().takeIf { it > 0 }
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
                            batteryManager.computeChargeTimeRemaining().takeIf { it > 0 }
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