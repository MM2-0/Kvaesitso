package de.mm20.launcher2.ui.system

import de.mm20.launcher2.i18n.R as I18nR
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.appmanagement.battery.BatteryPolicy
import de.mm20.launcher2.ktx.PrivilegedShell
import de.mm20.launcher2.preferences.battery.BatterySettings
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun SystemBatteryTab() {
    val settings: BatterySettings = koinInject()
    val group by settings.data.collectAsStateWithLifecycle(
        de.mm20.launcher2.preferences.BatteryGroup(),
    )
    var privileged by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { privileged = PrivilegedShell.available() }
    val context = LocalContext.current
    val gpsInstalled = remember {
        BatteryPolicy.installedRecommended(context, BatteryPolicy.RECOMMENDED_GPS)
    }
    val callInstalled = remember {
        BatteryPolicy.installedRecommended(context, BatteryPolicy.RECOMMENDED_CALL)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(I18nR.string.battery_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (!privileged) {
            item {
                Text(
                    stringResource(I18nR.string.battery_needs_shizuku),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_master),
                summary = stringResource(I18nR.string.battery_master_summary),
                value = group.batteryGuardEnabled,
                onValueChanged = { settings.update { g -> g.copy(batteryGuardEnabled = it) } },
                enabled = privileged,
            )
        }
        item { PreferenceCategory(title = stringResource(I18nR.string.battery_screen_off)) }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_gps),
                summary = stringResource(I18nR.string.battery_gps_summary),
                value = group.batteryScreenOffGps,
                onValueChanged = { settings.update { g -> g.copy(batteryScreenOffGps = it) } },
                enabled = group.batteryGuardEnabled,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_data),
                summary = stringResource(I18nR.string.battery_data_summary),
                value = group.batteryScreenOffData,
                onValueChanged = { settings.update { g -> g.copy(batteryScreenOffData = it) } },
                enabled = group.batteryGuardEnabled,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_mic),
                summary = stringResource(I18nR.string.battery_mic_summary),
                value = group.batteryScreenOffMic,
                onValueChanged = { settings.update { g -> g.copy(batteryScreenOffMic = it) } },
                enabled = group.batteryGuardEnabled,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_camera),
                summary = stringResource(I18nR.string.battery_camera_summary),
                value = group.batteryScreenOffCamera,
                onValueChanged = { settings.update { g -> g.copy(batteryScreenOffCamera = it) } },
                enabled = group.batteryGuardEnabled,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_sync),
                summary = stringResource(I18nR.string.battery_sync_summary),
                value = group.batteryScreenOffSync,
                onValueChanged = { settings.update { g -> g.copy(batteryScreenOffSync = it) } },
                enabled = group.batteryGuardEnabled,
            )
        }
        item {
            Text(stringResource(I18nR.string.battery_radio), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.padding(horizontal = 12.dp)) {
                listOf("" to I18nR.string.battery_radio_leave, "lte" to I18nR.string.battery_radio_lte, "wcdma" to I18nR.string.battery_radio_3g, "gsm" to I18nR.string.battery_radio_2g).forEach { (id, label) ->
                    FilterChip(
                        selected = group.batteryScreenOffRadio == id,
                        onClick = { settings.update { g -> g.copy(batteryScreenOffRadio = id) } },
                        enabled = group.batteryGuardEnabled,
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
        item { PreferenceCategory(title = stringResource(I18nR.string.battery_schedulers)) }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_weather_wake),
                summary = stringResource(I18nR.string.battery_weather_wake_summary),
                value = group.batteryWeatherWakeMinutes > 0,
                onValueChanged = { on -> settings.update { g -> g.copy(batteryWeatherWakeMinutes = if (on) 60 else 0) } },
                enabled = group.batteryGuardEnabled,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(I18nR.string.battery_sync_wake),
                summary = stringResource(I18nR.string.battery_sync_wake_summary),
                value = group.batterySyncWakeMinutes > 0,
                onValueChanged = { on -> settings.update { g -> g.copy(batterySyncWakeMinutes = if (on) 60 else 0) } },
                enabled = group.batteryGuardEnabled,
            )
        }
        item { PreferenceCategory(title = stringResource(I18nR.string.battery_holds)) }
        item {
            Text(
                stringResource(I18nR.string.battery_holds_gps_list, gpsInstalled.joinToString().ifBlank { "—" }),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                stringResource(I18nR.string.battery_holds_call_list, callInstalled.joinToString().ifBlank { "—" }),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
