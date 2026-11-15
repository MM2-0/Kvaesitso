package de.mm20.launcher2.ui.downloads

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.downloads.AfterFinish
import de.mm20.launcher2.downloads.DownloadSettings
import de.mm20.launcher2.downloads.ProxyType
import de.mm20.launcher2.downloads.media.MediaRuntime
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DownloadsSettingsScreen() {
    val context = LocalContext.current
    val store: DownloadSettings = koinInject()
    val s by store.values.collectAsState()
    val wgController: de.mm20.launcher2.network.api.WireguardController = koinInject()
    val wgConfigs by wgController.configs.collectAsState()
    val torrentGate: de.mm20.launcher2.downloads.TorrentProxyGate = koinInject()
    val torrentRoute by torrentGate.route.collectAsState()
    var editUserAgent by remember { mutableStateOf(false) }
    var editProxy by remember { mutableStateOf(false) }
    val runtime: MediaRuntime = koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var cookiesVersion by remember { mutableStateOf(0) }
    var updatedAt by remember { mutableStateOf(runtime.lastUpdate) }
    var version by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(updatedAt) {
        version = if (runtime.isAvailable) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runtime.version } else null
    }
    fun pickTime(minutes: Int, onPicked: (Int) -> Unit) {
        android.app.TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minutes / 60, minutes % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
    }
    fun clock(minutes: Int) = android.text.format.DateFormat.getTimeFormat(context).format(
        java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, minutes / 60); set(java.util.Calendar.MINUTE, minutes % 60) }.time
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            store.update { it.copy(defaultFolder = uri.toString()) }
        }
    }

    PreferenceScreen(title = stringResource(I18nR.string.dl_settings_title)) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dl_settings_queue)) {
                SliderPreference(
                    title = stringResource(I18nR.string.dl_max_parallel), value = s.maxParallel, min = 1, max = 10,
                    onValueChanged = { v -> store.update { it.copy(maxParallel = v) } },
                    label = { Text(it.toString()) },
                )
                SliderPreference(
                    title = stringResource(I18nR.string.dl_default_connections), value = s.connections, min = 1, max = 16,
                    onValueChanged = { v -> store.update { it.copy(connections = v) } },
                    label = { Text(it.toString()) },
                )
                ListPreference(
                    title = stringResource(I18nR.string.dl_global_limit),
                    items = listOf(0, 256, 512, 1024, 2048, 5120, 10240).map {
                        ListPreferenceItem(if (it == 0) stringResource(I18nR.string.dl_limit_none) else if (it >= 1024) "${it / 1024} MB/s" else "$it KB/s", it)
                    },
                    value = s.speedLimitKBps,
                    summary = if (s.speedLimitKBps == 0) stringResource(I18nR.string.dl_limit_none) else if (s.speedLimitKBps >= 1024) "${s.speedLimitKBps / 1024} MB/s" else "${s.speedLimitKBps} KB/s",
                    onValueChanged = { v -> store.update { it.copy(speedLimitKBps = v) } },
                )
                SliderPreference(
                    title = stringResource(I18nR.string.dl_retries), value = s.maxRetries, min = 0, max = 20,
                    onValueChanged = { v -> store.update { it.copy(maxRetries = v) } },
                    label = { Text(it.toString()) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dl_settings_rules)) {
                TransferNetworkPreferences("downloads")
                SwitchPreference(
                    title = stringResource(I18nR.string.dl_low_battery), summary = stringResource(I18nR.string.dl_low_battery_summary, s.lowBatteryPercent),
                    value = s.pauseOnLowBattery, onValueChanged = { v -> store.update { it.copy(pauseOnLowBattery = v) } },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dl_p3_schedule_title)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.dl_p3_schedule_enable), summary = stringResource(I18nR.string.dl_p3_schedule_sum),
                    value = s.scheduleEnabled, onValueChanged = { v -> store.update { it.copy(scheduleEnabled = v) } },
                )
                if (s.scheduleEnabled) {
                    Preference(
                        title = stringResource(I18nR.string.dl_p3_schedule_from, clock(s.scheduleStartMinute)),
                        onClick = { pickTime(s.scheduleStartMinute) { m -> store.update { it.copy(scheduleStartMinute = m) } } },
                    )
                    Preference(
                        title = stringResource(I18nR.string.dl_p3_schedule_to, clock(s.scheduleEndMinute)),
                        onClick = { pickTime(s.scheduleEndMinute) { m -> store.update { it.copy(scheduleEndMinute = m) } } },
                    )
                    Text(stringResource(I18nR.string.dl_p3_schedule_days), style = androidx.compose.material3.MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp)) {
                        val names = listOf(I18nR.string.dl_p3_day_1, I18nR.string.dl_p3_day_2, I18nR.string.dl_p3_day_3, I18nR.string.dl_p3_day_4, I18nR.string.dl_p3_day_5, I18nR.string.dl_p3_day_6, I18nR.string.dl_p3_day_7)
                        for (d in 0..6) {
                            androidx.compose.material3.FilterChip(
                                selected = (s.scheduleDays shr d) and 1 == 1,
                                onClick = { store.update { it.copy(scheduleDays = it.scheduleDays xor (1 shl d)) } },
                                label = { Text(stringResource(names[d])) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dl_m_settings_title)) {
                if (!runtime.isAvailable) {
                    Preference(title = stringResource(I18nR.string.dl_m_settings_unavailable), summary = stringResource(I18nR.string.dl_m_missing_text))
                } else {
                    Preference(
                        title = stringResource(I18nR.string.dl_m_update),
                        summary = if (updating) stringResource(I18nR.string.dl_m_updating) else stringResource(I18nR.string.dl_m_update_sum),
                        enabled = !updating,
                        onClick = {
                            updating = true
                            scope.launch {
                                val msg = try {
                                    context.getString(I18nR.string.dl_m_updated, runtime.update())
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    context.getString(I18nR.string.dl_m_update_failed, e.message.orEmpty())
                                }
                                updatedAt = runtime.lastUpdate
                                updating = false
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    var autoUpdate by remember { mutableStateOf(runtime.autoUpdate) }
                    SwitchPreference(
                        title = stringResource(I18nR.string.dl_m_auto_update),
                        summary = stringResource(I18nR.string.dl_m_auto_update_sum),
                        value = autoUpdate,
                        onValueChanged = { autoUpdate = it; runtime.autoUpdate = it },
                    )
                    Preference(
                        title = stringResource(I18nR.string.dl_m_version, version ?: "?"),
                        summary = stringResource(
                            I18nR.string.dl_m_last_update,
                            if (updatedAt > 0) android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date(updatedAt)) else stringResource(I18nR.string.dl_m_never),
                        ),
                    )
                    Preference(
                        title = stringResource(I18nR.string.dl_m_sites),
                        summary = "github.com/yt-dlp/yt-dlp",
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                    val has = remember(cookiesVersion) { runtime.hasCookies }
                    Preference(title = stringResource(if (has) I18nR.string.dl_m_cookies_present else I18nR.string.dl_m_cookies_absent))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        MediaCookieControls(runtime, null, onChanged = { cookiesVersion++ })
                    }
                }
                Text(stringResource(I18nR.string.dl_m_notice), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dl_settings_storage)) {
                Preference(
                    title = stringResource(I18nR.string.dl_default_folder),
                    summary = s.defaultFolder.takeIf { it.isNotEmpty() }?.let { Uri.decode(it).substringAfterLast(':') } ?: stringResource(I18nR.string.dl_folder_default),
                    onClick = { picker.launch(null) },
                )
                if (s.defaultFolder.isNotEmpty()) {
                    Preference(
                        title = stringResource(I18nR.string.dl_default_folder_reset),
                        onClick = { store.update { it.copy(defaultFolder = "") } },
                    )
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.dl_p3_auto_extract), summary = stringResource(I18nR.string.dl_p3_auto_extract_sum),
                    value = s.autoExtract, onValueChanged = { v -> store.update { it.copy(autoExtract = v) } },
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.dl_p3_detect_clipboard), summary = stringResource(I18nR.string.dl_p3_detect_clipboard_sum),
                    value = s.detectClipboard, onValueChanged = { v -> store.update { it.copy(detectClipboard = v) } },
                )
                ListPreference(
                    title = stringResource(I18nR.string.dl_after_finish),
                    items = listOf(
                        ListPreferenceItem(stringResource(I18nR.string.dl_after_nothing), AfterFinish.Nothing),
                        ListPreferenceItem(stringResource(I18nR.string.dl_after_open), AfterFinish.Open),
                        ListPreferenceItem(stringResource(I18nR.string.dl_after_share), AfterFinish.Share),
                    ),
                    value = s.afterFinish,
                    onValueChanged = { v -> store.update { it.copy(afterFinish = v) } },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dl_settings_network)) {
                Preference(
                    title = stringResource(I18nR.string.dl_user_agent),
                    summary = s.userAgent.ifBlank { stringResource(I18nR.string.dl_user_agent_default) },
                    onClick = { editUserAgent = true },
                )
                Preference(
                    title = stringResource(I18nR.string.dl_proxy),
                    summary = when (s.proxyType) {
                        ProxyType.None -> stringResource(I18nR.string.dl_proxy_none)
                        ProxyType.Http -> "HTTP ${s.proxyHost}:${s.proxyPort}"
                        ProxyType.Socks -> "SOCKS ${s.proxyHost}:${s.proxyPort}"
                    } + if (s.torrentWgConfigId > 0) {
                        "\n" + stringResource(
                            I18nR.string.au5_wgtorrent_summary,
                            wgConfigs.firstOrNull { it.id == s.torrentWgConfigId }?.name ?: stringResource(I18nR.string.au5_wgtorrent_removed),
                        )
                    } else "",
                    onClick = { editProxy = true },
                )
            }
        }
        torrentSettingsItems()
        item {
            PreferenceCategory(title = stringResource(I18nR.string.dl_settings_notifications)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.dl_notifications),
                    value = s.notifications, onValueChanged = { v -> store.update { it.copy(notifications = v) } },
                )
            }
        }
    }

    if (editUserAgent) {
        var text by remember { mutableStateOf(s.userAgent) }
        AlertDialog(
            onDismissRequest = { editUserAgent = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.dl_user_agent))  } },
            text = {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(I18nR.string.dl_user_agent_default)) })
            },
            confirmButton = { TextButton(onClick = { store.update { it.copy(userAgent = text.trim()) }; editUserAgent = false }) { Text(stringResource(I18nR.string.dl_save)) } },
            dismissButton = { TextButton(onClick = { editUserAgent = false }) { Text(stringResource(I18nR.string.dl_cancel)) } },
        )
    }
    if (editProxy) {
        var type by remember { mutableStateOf(s.proxyType) }
        var host by remember { mutableStateOf(s.proxyHost) }
        var port by remember { mutableStateOf(if (s.proxyPort > 0) s.proxyPort.toString() else "") }
        var wgId by remember { mutableStateOf(s.torrentWgConfigId) }
        AlertDialog(
            onDismissRequest = { editProxy = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.dl_proxy))  } },
            text = {
                Column {
                    androidx.compose.foundation.layout.FlowRow {
                        for (t in ProxyType.entries) {
                            androidx.compose.material3.FilterChip(
                                selected = type == t, onClick = { type = t },
                                label = { Text(when (t) { ProxyType.None -> stringResource(I18nR.string.dl_proxy_none); ProxyType.Http -> "HTTP"; ProxyType.Socks -> "SOCKS" }) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    if (type != ProxyType.None) {
                        Text(stringResource(if (type == ProxyType.Http) I18nR.string.au4_torrentproxy_note_http else I18nR.string.au4_torrentproxy_note_socks), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(I18nR.string.dl_proxy_host)) })
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(I18nR.string.dl_proxy_port)) })
                    }
                    Text(stringResource(I18nR.string.au5_wgtorrent_title), style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                    Text(stringResource(I18nR.string.au5_wgtorrent_desc), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    androidx.compose.foundation.layout.FlowRow {
                        androidx.compose.material3.FilterChip(
                            selected = wgId <= 0, onClick = { wgId = 0 },
                            label = { Text(stringResource(I18nR.string.au5_wgtorrent_none)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        for (c in wgConfigs) {
                            androidx.compose.material3.FilterChip(
                                selected = wgId == c.id, onClick = { wgId = c.id },
                                label = { Text(c.name) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    if (wgConfigs.isEmpty()) {
                        Text(stringResource(I18nR.string.au5_wgtorrent_no_configs), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    torrentWgStatusText(torrentRoute)?.let {
                        Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                // a proxy without host or with a wrong port would be ignored; do not store it
                TextButton(enabled = type == ProxyType.None || (host.isNotBlank() && port.toIntOrNull() in 1..65535), onClick = {
                    store.update { it.copy(proxyType = type, proxyHost = host.trim(), proxyPort = port.toIntOrNull() ?: 0, torrentWgConfigId = wgId) }
                    editProxy = false
                }) { Text(stringResource(I18nR.string.dl_save)) }
            },
            dismissButton = { TextButton(onClick = { editProxy = false }) { Text(stringResource(I18nR.string.dl_cancel)) } },
        )
    }
}
