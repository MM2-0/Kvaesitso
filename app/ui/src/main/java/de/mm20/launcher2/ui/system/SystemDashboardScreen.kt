package de.mm20.launcher2.ui.system

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.graphics.HwuiRenderer
import de.mm20.launcher2.network.NetState
import de.mm20.launcher2.network.NetworkEngine
import de.mm20.launcher2.network.api.DnsController
import de.mm20.launcher2.network.api.LogController
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlinx.serialization.Serializable
import java.io.File
import java.net.NetworkInterface
import kotlin.math.sin

@Serializable
data class SystemDashboardRoute(val tab: String = "") : NavKey

internal data class ProcRow(val pid: Int, val name: String, val ramKb: Long?)

@Composable
fun SystemDashboardScreen(initialTab: String = "") {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var cpu by remember { mutableStateOf<Float?>(null) }
    var ramUsed by remember { mutableStateOf(0L) }
    var ramTotal by remember { mutableStateOf(0L) }
    var ips by remember { mutableStateOf(listOf<String>()) }
    var wifiSsid by remember { mutableStateOf<String?>(null) }
    var processes by remember { mutableStateOf<List<ProcRow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    val uiSettings: UiSettings = koinInject()
    val comms: CommsSettings = koinInject()
    val engine: NetworkEngine = koinInject()
    val logs: LogController = koinInject()
    val dns: DnsController = koinInject()
    val systemWidgetOn by uiSettings.systemWidgetEnabled.collectAsState(false)
    val preferredRenderer by comms.hwuiRenderer.collectAsState(HwuiRenderer.MODE_DEFAULT)
    val restartNotice by comms.hwuiShowRestartNotice.collectAsState(true)
    val netState by engine.state.collectAsState()
    val netStats by logs.stats.collectAsState()
    val dnsServer by dns.selected.collectAsState()
    var liveRenderer by remember { mutableStateOf(HwuiRenderer.MODE_DEFAULT) }
    var explainRestart by remember { mutableStateOf(false) }
    val storeVm: de.mm20.launcher2.ui.store.StoreViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var currentTab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(
            when (initialTab.lowercase()) {
                "apps" -> 1
                "tweaks" -> 2
                "shield", "privacy", "security" -> 3
                "shizuku" -> 4
                else -> 0
            }
        )
    }
    val pulse = rememberInfiniteTransition(label = "sysPulse")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")

    LaunchedEffect(Unit) {
        var previousCpu: Pair<Long, Long>? = null
        while (true) {
            val currentCpu = withContext(Dispatchers.IO) { readCpu() }
            cpu = cpuUsage(previousCpu, currentCpu)
            previousCpu = currentCpu
            val am = context.getSystemService<ActivityManager>()
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            ramUsed = mi.totalMem - mi.availMem
            ramTotal = mi.totalMem
            ips = localIps()
            wifiSsid = currentSsidSystemDash(context)

            processes = withContext(Dispatchers.IO) { runningProcs(context) }
            liveRenderer = HwuiRenderer.current()
            delay(1500)
        }
    }

    if (explainRestart) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { explainRestart = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_graphics_api))  } },
            text = { Text(stringResource(I18nR.string.app_polish_renderer_restart)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { explainRestart = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
        )
    }
    de.mm20.launcher2.ui.component.VirtualAppWindow {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.22f * MaterialTheme.transparency.surface), Color.Transparent)))) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * (0.18f + 0.04f * sin(t * Math.PI * 2).toFloat())
            val c = Offset(size.width * 0.82f, size.height * 0.12f)
            drawCircle(scheme.primary.copy(alpha = 0.12f), r, c, style = Stroke(width = 6f))
            drawCircle(scheme.tertiary.copy(alpha = 0.10f), r * 1.4f, c, style = Stroke(width = 3f))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (backStack.size > 1) {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back))
                    }
                }
                TelosCyberAppBarTitle(
                    title = stringResource(I18nR.string.telos_system_label),
                    status = stringResource(I18nR.string.net_hub_status),
                    statusColor = TelosCyber.NeonCyan,
                    modifier = Modifier.weight(1f).padding(start = if (backStack.size > 1) 0.dp else 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TelosTechPill(
                    text = stringResource(I18nR.string.telos_system_label),
                    isSelected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    accentColor = TelosCyber.NeonCyan,
                )
                TelosTechPill(
                    text = stringResource(I18nR.string.hc_apps),
                    isSelected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    accentColor = TelosCyber.NeonGreen,
                )
                TelosTechPill(
                    text = stringResource(I18nR.string.preference_screen_tweaks),
                    isSelected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    accentColor = TelosCyber.NeonOrange,
                )
                TelosTechPill(
                    text = stringResource(I18nR.string.security_hub_tab_title),
                    isSelected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    accentColor = TelosCyber.NeonMagenta,
                )
                TelosTechPill(
                    text = stringResource(I18nR.string.telos_shizuku_title),
                    isSelected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    accentColor = TelosCyber.NeonCyan,
                )
            }
            if (currentTab == 1) {
                SystemAppsTab(onOpenDetails = { pkg -> backStack.add(SystemAppDetailsRoute(pkg)) })
            } else if (currentTab == 2) {
                de.mm20.launcher2.ui.settings.tweaks.TweaksSettingsScreen()
            } else if (currentTab == 3) {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                    item { de.mm20.launcher2.ui.settings.security.TelosSecurityHubBody() }
                }
            } else if (currentTab == 4) {
                SystemShizukuTab()
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { de.mm20.launcher2.ui.component.PerAppFontScaleSetting(appKey = "system") }
                item { BatteryCard() }
                item {
                    GlassCard {
                        Text("${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.titleMedium)
                        Text("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT} · ${Build.HARDWARE} · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
                        Text(stringResource(I18nR.string.telos_system_uptime, formatUptime(SystemClock.elapsedRealtime())))
                    }
                }
                item {
                    GlassCard {
                        Text(stringResource(I18nR.string.hc_graphics_api), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(I18nR.string.hc_graphics_current) + ": " + when (liveRenderer) {
                                HwuiRenderer.MODE_VULKAN -> stringResource(I18nR.string.hc_graphics_vulkan)
                                HwuiRenderer.MODE_OPENGL -> stringResource(I18nR.string.hc_graphics_opengl)
                                else -> stringResource(I18nR.string.hc_graphics_default)
                            },
                            color = scheme.primary,
                        )
                        Text(stringResource(I18nR.string.hc_graphics_api_about_summary), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(I18nR.string.app_polish_renderer_restart), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = preferredRenderer == HwuiRenderer.MODE_VULKAN,
                                onClick = {
                                    scope.launch {
                                        val mode = HwuiRenderer.MODE_VULKAN
                                        comms.setHwuiRenderer(mode)
                                        val ok = HwuiRenderer.apply(mode)
                                        liveRenderer = HwuiRenderer.current()
                                        status = if (ok) {
                                            if (restartNotice) explainRestart = true
                                            context.getString(I18nR.string.hc_graphics_applied)
                                        } else {
                                            context.getString(I18nR.string.hc_graphics_need_privilege, HwuiRenderer.adbCommand(mode))
                                        }
                                    }
                                },
                                label = { Text(stringResource(I18nR.string.hc_graphics_vulkan)) },
                            )
                            FilterChip(
                                selected = preferredRenderer == HwuiRenderer.MODE_OPENGL,
                                onClick = {
                                    scope.launch {
                                        val mode = HwuiRenderer.MODE_OPENGL
                                        comms.setHwuiRenderer(mode)
                                        val ok = HwuiRenderer.apply(mode)
                                        liveRenderer = HwuiRenderer.current()
                                        status = if (ok) {
                                            if (restartNotice) explainRestart = true
                                            context.getString(I18nR.string.hc_graphics_applied)
                                        } else {
                                            context.getString(I18nR.string.hc_graphics_need_privilege, HwuiRenderer.adbCommand(mode))
                                        }
                                    }
                                },
                                label = { Text(stringResource(I18nR.string.hc_graphics_opengl)) },
                            )
                            FilterChip(
                                selected = preferredRenderer == HwuiRenderer.MODE_DEFAULT,
                                onClick = {
                                    scope.launch {
                                        val mode = HwuiRenderer.MODE_DEFAULT
                                        comms.setHwuiRenderer(mode)
                                        val ok = HwuiRenderer.apply(mode)
                                        liveRenderer = HwuiRenderer.current()
                                        status = if (ok) {
                                            if (restartNotice) explainRestart = true
                                            context.getString(I18nR.string.hc_graphics_applied)
                                        } else {
                                            context.getString(I18nR.string.hc_graphics_need_privilege, HwuiRenderer.adbCommand(mode))
                                        }
                                    }
                                },
                                label = { Text(stringResource(I18nR.string.hc_graphics_default)) },
                            )
                        }
                        status?.let { msg ->
                            val ok = msg == context.getString(I18nR.string.hc_graphics_applied)
                            Text(msg, color = if (ok) scheme.primary else scheme.error)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(I18nR.string.hc_system_widget_enable), modifier = Modifier.weight(1f))
                            Switch(checked = systemWidgetOn, onCheckedChange = { uiSettings.setSystemWidgetEnabled(it) })
                        }
                        Text(stringResource(I18nR.string.hc_system_widget_enable_summary), style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val cpuUsage = cpu ?: 0f
                        de.mm20.launcher2.ui.component.CyberRadialGauge(
                            title = stringResource(I18nR.string.telos_system_cpu),
                            value = cpuUsage,
                            valueText = "${(cpuUsage * 100).toInt()}%",
                            subtitle = if (cpu != null) stringResource(I18nR.string.telos_system_cpu) else stringResource(I18nR.string.virtual_telemetry_unavailable),
                            modifier = Modifier.weight(1f),
                            primaryColor = TelosCyber.NeonCyan,
                            accentColor = TelosCyber.NeonMagenta,
                        )

                        val ramF = if (ramTotal == 0L) 0f else ramUsed.toFloat() / ramTotal
                        de.mm20.launcher2.ui.component.CyberRadialGauge(
                            title = stringResource(I18nR.string.telos_system_ram),
                            value = ramF,
                            valueText = "${(ramF * 100).toInt()}%",
                            subtitle = "${Formatter.formatFileSize(context, ramUsed)} / ${Formatter.formatFileSize(context, ramTotal)}",
                            modifier = Modifier.weight(1f),
                            primaryColor = TelosCyber.NeonGreen,
                            accentColor = TelosCyber.NeonCyan,
                        )
                    }
                }
                item {
                    GlassCard {
                        Text(stringResource(I18nR.string.telos_system_network), style = MaterialTheme.typography.titleMedium)
                        val vpnOn = netState is NetState.On
                        Text(
                            stringResource(I18nR.string.net_hub_title) + " · " + stringResource(
                                if (vpnOn) I18nR.string.net_hub_state_on else I18nR.string.net_hub_state_off,
                            ),
                            color = if (vpnOn) scheme.primary else scheme.onSurfaceVariant,
                        )
                        Text("${stringResource(I18nR.string.net_hub_stat_dns)} ${dnsServer.name}")
                        Text("${stringResource(I18nR.string.net_hub_stat_blocked_dns)} ${netStats.dnsBlocked}  ·  ${stringResource(I18nR.string.net_hub_stat_blocked_conn)} ${netStats.connectionsBlocked}")
                        Text("SSID: ${wifiSsid ?: "—"}")
                        ips.forEach { Text(it) }
                        Text(
                            stringResource(I18nR.string.telos_system_saved_wifi),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = {
                            backStack.add(de.mm20.launcher2.ui.network.NetworkSettingsRoute)
                        }) {
                            Text(stringResource(I18nR.string.telos_system_reveal_wifi))
                        }
                    }
                }
                item { SystemToolsSection() }
                item { TelosCyberSectionLabel(stringResource(I18nR.string.telos_system_processes), accent = TelosCyber.NeonOrange) }
                items(processes.take(40), key = { it.pid }) { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${p.pid}  ${p.name}", modifier = Modifier.weight(1f), maxLines = 1)
                        Text(p.ramKb?.let { Formatter.formatFileSize(context, it * 1024) } ?: "—")
                    }
                }
            }
            }
        }
    }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = MaterialTheme.shapes.medium)
            .padding(de.mm20.launcher2.ui.layout.UiSpacing.Content),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

private fun formatUptime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    return "%dh %dm".format(h, m)
}

private fun localIps(): List<String> = buildList {
    runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { ni ->
            if (!ni.isUp || ni.isLoopback) return@forEach
            ni.inetAddresses.toList().forEach { addr ->
                if (!addr.isLoopbackAddress) add("${ni.displayName}: ${addr.hostAddress}")
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun currentSsidSystemDash(context: Context): String? {
    val cm = context.getSystemService<ConnectivityManager>() ?: return null
    if (cm.allNetworks.none { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }) return null
    val wm = context.getSystemService<WifiManager>() ?: return null
    val ssid = wm.connectionInfo?.ssid?.trim('"')
    return ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
}

private fun readCpu(): Pair<Long, Long>? = runCatching {
    File("/proc/stat").bufferedReader().use { parseCpuStat(it.readLine()) }
}.getOrNull()

/** /proc/stat includes guest time in user/nice already; summing it twice inflates load. */
internal fun parseCpuStat(line: String?): Pair<Long, Long>? {
    val columns = line?.trim()?.split(Regex("\\s+")) ?: return null
    if (columns.firstOrNull() != "cpu") return null
    val counters = columns.drop(1).take(8).map { it.toLongOrNull() ?: return null }
    if (counters.size < 4 || counters.any { it < 0 }) return null
    return (counters[3] + (counters.getOrNull(4) ?: 0)) to counters.sum()
}

internal fun cpuUsage(previous: Pair<Long, Long>?, current: Pair<Long, Long>?): Float? {
    if (previous == null || current == null) return null
    val total = current.second - previous.second
    val idle = current.first - previous.first
    if (total <= 0 || idle < 0 || idle > total) return null
    return (1f - idle.toFloat() / total).coerceIn(0f, 1f)
}

internal fun runningProcs(context: Context): List<ProcRow> {
    val am = context.getSystemService<ActivityManager>() ?: return emptyList()
    val processes = runCatching { am.runningAppProcesses }.getOrNull().orEmpty()
    // Android restricts other UIDs; zero PSS from that restriction is not real zero RAM.
    val memory = runCatching { am.getProcessMemoryInfo(processes.map { it.pid }.toIntArray()) }.getOrNull()
    return processes.mapIndexed { index, process ->
        ProcRow(process.pid, process.processName, memory?.getOrNull(index)?.totalPss?.toLong()?.takeIf { it > 0 })
    }.sortedBy { it.name }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    var batteryInfo by remember { mutableStateOf<BatteryStats?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
                batteryInfo = BatteryStats(pct, status, health, plugged, voltage, temp, tech)
            }
        }
    }

    batteryInfo?.let { info ->
        val statusText = when (info.status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> stringResource(I18nR.string.battery_status_charging)
            BatteryManager.BATTERY_STATUS_DISCHARGING -> stringResource(I18nR.string.battery_status_discharging)
            BatteryManager.BATTERY_STATUS_FULL -> stringResource(I18nR.string.battery_status_full)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> stringResource(I18nR.string.battery_status_not_charging)
            else -> stringResource(I18nR.string.freeze_hub_unknown)
        }
        val healthText = when (info.health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> stringResource(I18nR.string.battery_health_good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> stringResource(I18nR.string.battery_health_overheat)
            BatteryManager.BATTERY_HEALTH_DEAD -> stringResource(I18nR.string.battery_health_dead)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> stringResource(I18nR.string.battery_health_over_voltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> stringResource(I18nR.string.battery_health_failure)
            else -> stringResource(I18nR.string.freeze_hub_unknown)
        }
        val pluggedText = when (info.plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> stringResource(I18nR.string.battery_plugged_ac)
            BatteryManager.BATTERY_PLUGGED_USB -> stringResource(I18nR.string.battery_plugged_usb)
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> stringResource(I18nR.string.battery_plugged_wireless)
            else -> stringResource(I18nR.string.battery_plugged_unplugged)
        }
        val tempC = info.temp / 10f
        GlassCard {
            Row(
                Modifier.fillMaxWidth().clickable { showDetails = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(I18nR.string.battery_power_hub), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${info.percent}% · $statusText ($pluggedText)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(I18nR.string.battery_health_line, healthText, tempC.toString(), info.voltage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CircularProgressIndicator(
                    progress = { info.percent / 100f },
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 5.dp,
                )
            }
        }

        if (showDetails) {
            ModalBottomSheet(onDismissRequest = { showDetails = false }, sheetState = rememberModalBottomSheetState()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(I18nR.string.battery_details_health), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    InfoLine(stringResource(I18nR.string.battery_charge_level), "${info.percent}%")
                    InfoLine(stringResource(I18nR.string.battery_status), statusText)
                    InfoLine(stringResource(I18nR.string.battery_health), healthText)
                    InfoLine(stringResource(I18nR.string.battery_power_source), pluggedText)
                    InfoLine(stringResource(I18nR.string.battery_temperature), "${tempC}°C (${(tempC * 9 / 5 + 32)}°F)")
                    InfoLine(stringResource(I18nR.string.battery_voltage), "${info.voltage} mV")
                    InfoLine(stringResource(I18nR.string.battery_technology), info.tech)
                    Button(onClick = { showDetails = false }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(stringResource(I18nR.string.close))
                    }
                }
            }
        }
    }
}

private data class BatteryStats(
    val percent: Int,
    val status: Int,
    val health: Int,
    val plugged: Int,
    val voltage: Int,
    val temp: Int,
    val tech: String,
)

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
