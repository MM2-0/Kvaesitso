/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.MemorySnapshot
import com.vitorpamplona.amethyst.collectMemorySnapshot
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.resource_usage_activity_section
import com.vitorpamplona.amethyst.commons.resources.resource_usage_alwayson_battery
import com.vitorpamplona.amethyst.commons.resources.resource_usage_alwayson_bg_relay
import com.vitorpamplona.amethyst.commons.resources.resource_usage_alwayson_explanation
import com.vitorpamplona.amethyst.commons.resources.resource_usage_alwayson_section
import com.vitorpamplona.amethyst.commons.resources.resource_usage_alwayson_settings_button
import com.vitorpamplona.amethyst.commons.resources.resource_usage_alwayson_starts
import com.vitorpamplona.amethyst.commons.resources.resource_usage_alwayson_uptime
import com.vitorpamplona.amethyst.commons.resources.resource_usage_app_starts
import com.vitorpamplona.amethyst.commons.resources.resource_usage_battery_drain
import com.vitorpamplona.amethyst.commons.resources.resource_usage_by_subsystem
import com.vitorpamplona.amethyst.commons.resources.resource_usage_by_subsystem_cellular
import com.vitorpamplona.amethyst.commons.resources.resource_usage_calls
import com.vitorpamplona.amethyst.commons.resources.resource_usage_cell_of_total
import com.vitorpamplona.amethyst.commons.resources.resource_usage_copy_button
import com.vitorpamplona.amethyst.commons.resources.resource_usage_cpu
import com.vitorpamplona.amethyst.commons.resources.resource_usage_decrypts
import com.vitorpamplona.amethyst.commons.resources.resource_usage_empty
import com.vitorpamplona.amethyst.commons.resources.resource_usage_http_active
import com.vitorpamplona.amethyst.commons.resources.resource_usage_http_requests
import com.vitorpamplona.amethyst.commons.resources.resource_usage_location
import com.vitorpamplona.amethyst.commons.resources.resource_usage_media_play
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_addressables
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_chatrooms
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_device_class
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_heap
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_image_cache
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_image_disk
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_native
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_notes
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_section
import com.vitorpamplona.amethyst.commons.resources.resource_usage_memory_users
import com.vitorpamplona.amethyst.commons.resources.resource_usage_nests
import com.vitorpamplona.amethyst.commons.resources.resource_usage_pow
import com.vitorpamplona.amethyst.commons.resources.resource_usage_radio_bursts
import com.vitorpamplona.amethyst.commons.resources.resource_usage_reconnects
import com.vitorpamplona.amethyst.commons.resources.resource_usage_relay_time
import com.vitorpamplona.amethyst.commons.resources.resource_usage_relay_time_bg_mobile
import com.vitorpamplona.amethyst.commons.resources.resource_usage_remote_signs
import com.vitorpamplona.amethyst.commons.resources.resource_usage_screen_time_section
import com.vitorpamplona.amethyst.commons.resources.resource_usage_send_button
import com.vitorpamplona.amethyst.commons.resources.resource_usage_send_explanation
import com.vitorpamplona.amethyst.commons.resources.resource_usage_send_section
import com.vitorpamplona.amethyst.commons.resources.resource_usage_share_button
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_image
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_money
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_nip05
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_other
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_preview
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_push
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_relay
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_uploads
import com.vitorpamplona.amethyst.commons.resources.resource_usage_subsystem_video
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tile_avg_relays
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tile_battery_rate
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tile_cellular
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tile_data_rate
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tile_in_app
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tile_relay
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tile_wifi
import com.vitorpamplona.amethyst.commons.resources.resource_usage_title
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tor
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tor_explanation
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tor_section
import com.vitorpamplona.amethyst.commons.resources.resource_usage_tor_settings_button
import com.vitorpamplona.amethyst.commons.resources.resource_usage_trend_section
import com.vitorpamplona.amethyst.commons.resources.resource_usage_verifies
import com.vitorpamplona.amethyst.commons.resources.resource_usage_wakelock
import com.vitorpamplona.amethyst.commons.resources.resource_usage_worker_runs
import com.vitorpamplona.amethyst.commons.service.crashreports.DEV_REPORT_PUBKEY
import com.vitorpamplona.amethyst.commons.service.resourceusage.ResourceUsageAccountant
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageSummary
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler.Companion.formatBytes
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler.Companion.formatConnHours
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageReportAssembler.Companion.formatDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import java.util.Locale

/**
 * The resource-usage ledger: how much network, relay connection time, and
 * background activity the app consumed, per subsystem — with an explicit,
 * user-initiated path to DM the numbers to the developers (same NIP-17 flow
 * as crash reports). Everything on this screen stays on-device until the
 * user sends that DM.
 *
 * Layout: headline stat tiles (today) → 7-day trend chart → per-feature
 * proportion bars → activity counters → live memory meters → send card.
 */
@Composable
fun ResourceUsageScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var days by remember { mutableStateOf<Map<Long, Map<String, Long>>?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            days = Amethyst.instance.resourceUsage.allDaysIncludingLive()
        }
    }

    // Live memory snapshot, refreshed only while this screen is composed.
    // Collected off-main: DiskLruCache.size() contends with journal I/O.
    val context = LocalContext.current
    val memory by produceState<MemorySnapshot?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { collectMemorySnapshot(context) }
            delay(2_000)
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(id = Res.string.resource_usage_title), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val loaded = days
            if (loaded == null) {
                Text(
                    text = stringRes(Res.string.resource_usage_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // remember(loaded): the memory produceState recomposes this
                // scope every 2s, and the summaries walk every counter of
                // every retained day — recompute only when the data reloads.
                val today = remember(loaded) { Amethyst.instance.resourceUsage.today() }
                val todaySummary = remember(loaded) { UsageSummary.from(loaded[today].orEmpty()) }
                val weekSummary = remember(loaded) { UsageSummary.fromDays((today - 6..today).mapNotNull { loaded[it] }) }

                TodayTiles(todaySummary)
                WeekRatesTiles(weekSummary)
                if (weekSummary.totalBytes > 0) {
                    SettingsSection(Res.string.resource_usage_trend_section) {
                        UsageTrendChart(loaded, today)
                    }
                }
                SubsystemSection(weekSummary)
                ScreenTimeSection(weekSummary)
                ActivitySection(weekSummary)
                AlwaysOnServiceSection(weekSummary, nav)
                TorServiceSection(weekSummary, nav)
                MemorySection(memory)
                SendReportSection(accountViewModel, nav, loaded, today, memory)
            }
        }
    }
}

/** Headline numbers for today as a 2x2 tile grid. */
@Composable
private fun TodayTiles(s: UsageSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Res.string.resource_usage_tile_cellular, formatBytes(s.mobileBytes), Modifier.weight(1f))
            StatTile(Res.string.resource_usage_tile_wifi, formatBytes(s.wifiBytesBg + s.wifiBytesFg), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Res.string.resource_usage_tile_relay, formatConnHours(s.relayConnMs), Modifier.weight(1f))
            StatTile(Res.string.resource_usage_tile_in_app, formatDurationMs(s.foregroundMs), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(
    label: StringResource,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringRes(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 7-day rates: totals aren't judgeable, rates are. Battery %/hour of use is
 * the most tangible battery number we can show; average simultaneous relay
 * connections is the number a user can act on by trimming their relay list.
 */
@Composable
private fun WeekRatesTiles(s: UsageSummary) {
    val fgHours = s.foregroundMs / 3_600_000.0
    val tiles =
        buildList {
            if (fgHours >= 0.5 && s.batteryDrainFg > 0) {
                add(Res.string.resource_usage_tile_battery_rate to String.format(Locale.US, "%.1f%%", s.batteryDrainFg / fgHours))
            }
            if (fgHours >= 0.5) {
                add(Res.string.resource_usage_tile_data_rate to formatBytes(((s.mobileBytesFg + s.wifiBytesFg) / fgHours).toLong()))
            }
            val avgRelays = s.relayConnMs.toDouble() / (s.dayCount * ResourceUsageAccountant.DAY_MS)
            if (avgRelays >= 0.5) {
                add(Res.string.resource_usage_tile_avg_relays to String.format(Locale.US, "%.1f", avgRelays))
            }
        }
    if (tiles.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
    }
}

/**
 * Ranked per-feature traffic with proportion bars (7 days). Cellular is the
 * scarce resource (battery and often money), so when any cellular traffic
 * exists the ranking, bars, and headline value are cellular — with the total
 * as secondary context. Wi-Fi-only devices fall back to totals.
 */
@Composable
private fun SubsystemSection(week: UsageSummary) {
    val cellular = week.mobileBytesPerSubsystem
    if (cellular.isNotEmpty()) {
        val rows = cellular.entries.sortedByDescending { it.value }
        val max = rows.first().value.coerceAtLeast(1L)
        SettingsSection(Res.string.resource_usage_by_subsystem_cellular) {
            rows.forEach { (subsystem, bytes) ->
                BarRow(
                    label = subsystemLabel(subsystem),
                    value = stringRes(Res.string.resource_usage_cell_of_total, formatBytes(bytes), formatBytes(week.bytesPerSubsystem[subsystem] ?: bytes)),
                    fraction = bytes.toFloat() / max.toFloat(),
                )
            }
        }
        return
    }
    if (week.bytesPerSubsystem.isEmpty()) return
    val rows = week.bytesPerSubsystem.entries.sortedByDescending { it.value }
    val max = rows.first().value.coerceAtLeast(1L)
    SettingsSection(Res.string.resource_usage_by_subsystem) {
        rows.forEach { (subsystem, bytes) ->
            BarRow(
                label = subsystemLabel(subsystem),
                value = formatBytes(bytes),
                fraction = bytes.toFloat() / max.toFloat(),
            )
        }
    }
}

/**
 * Where the screen-on time went (7 days). Route base names only — the
 * ledger never records which profile/hashtag/thread a screen showed.
 */
@Composable
private fun ScreenTimeSection(week: UsageSummary) {
    if (week.screenTimeMs.isEmpty()) return
    val rows =
        week.screenTimeMs.entries
            .sortedByDescending { it.value }
            .take(6)
    val max = rows.first().value.coerceAtLeast(1L)
    SettingsSection(Res.string.resource_usage_screen_time_section) {
        rows.forEach { (name, ms) ->
            BarRow(
                label = name,
                value = formatDurationMs(ms),
                fraction = ms.toFloat() / max.toFloat(),
            )
        }
    }
}

private fun subsystemLabel(subsystem: String): StringResource =
    when (subsystem) {
        "relay" -> Res.string.resource_usage_subsystem_relay
        "image" -> Res.string.resource_usage_subsystem_image
        "video" -> Res.string.resource_usage_subsystem_video
        "uploads" -> Res.string.resource_usage_subsystem_uploads
        "money" -> Res.string.resource_usage_subsystem_money
        "nip05" -> Res.string.resource_usage_subsystem_nip05
        "preview" -> Res.string.resource_usage_subsystem_preview
        "push" -> Res.string.resource_usage_subsystem_push
        else -> Res.string.resource_usage_subsystem_other
    }

@Composable
private fun BarRow(
    label: StringResource,
    value: String,
    fraction: Float,
    color: Color = MaterialTheme.colorScheme.primary,
) = BarRow(stringRes(label), value, fraction, color)

/** Label + value + a thin rounded proportion bar underneath. */
@Composable
private fun BarRow(
    label: String,
    value: String,
    fraction: Float,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
            )
        }
    }
}

/** Background/CPU activity counters for the last 7 days. */
@Composable
private fun ActivitySection(s: UsageSummary) {
    SettingsSection(Res.string.resource_usage_activity_section) {
        MetricRow(Res.string.resource_usage_relay_time, formatConnHours(s.relayConnMs))
        SettingsDivider()
        MetricRow(Res.string.resource_usage_relay_time_bg_mobile, formatConnHours(s.relayConnMsMobileBg))
        SettingsDivider()
        MetricRow(Res.string.resource_usage_reconnects, "${s.relayConnects} (${s.relayConnectFails})")
        SettingsDivider()
        MetricRow(Res.string.resource_usage_http_requests, s.httpRequests.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_radio_bursts, s.radioBursts.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_http_active, formatDurationMs(s.httpActiveMs))
        SettingsDivider()
        MetricRow(Res.string.resource_usage_media_play, formatDurationMs(s.mediaPlayMs))
        SettingsDivider()
        MetricRow(Res.string.resource_usage_verifies, s.verifyCount.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_cpu, formatDurationMs(s.cpuMs))
        SettingsDivider()
        MetricRow(Res.string.resource_usage_wakelock, formatDurationMs(s.wakelockMs))
        SettingsDivider()
        MetricRow(Res.string.resource_usage_worker_runs, s.workerRuns.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_app_starts, s.appStarts.toString())
        if (s.decryptCount > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_decrypts, s.decryptCount.toString())
        }
        if (s.signNip46 + s.signNip55 > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_remote_signs, (s.signNip46 + s.signNip55).toString())
        }
        if (s.powMs > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_pow, formatDurationMs(s.powMs))
        }
        if (s.torMs > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_tor, formatDurationMs(s.torMs))
        }
        if (s.callMs > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_calls, formatDurationMs(s.callMs))
        }
        if (s.nestsMs > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_nests, formatDurationMs(s.nestsMs))
        }
        if (s.locationMs > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_location, formatDurationMs(s.locationMs))
        }
        if (s.batteryDrainFg + s.batteryDrainBg > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_battery_drain, "${s.batteryDrainFg}% / ${s.batteryDrainBg}%")
        }
    }
}

@Composable
private fun MetricRow(
    label: StringResource,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Live memory meters. The heap bar keeps the retired debug chip's status
 * semantics: green below 60% of the max heap, amber to 80%, red above.
 */
@Composable
private fun MemorySection(memory: MemorySnapshot?) {
    if (memory == null) return
    val heapColor =
        when {
            memory.heapFraction > 0.80f -> MaterialTheme.colorScheme.error
            memory.heapFraction > 0.60f -> MaterialTheme.colorScheme.warningColor
            else -> MaterialTheme.colorScheme.allGoodColor
        }
    SettingsSection(Res.string.resource_usage_memory_section) {
        BarRow(
            label = Res.string.resource_usage_memory_heap,
            value = "${memory.heapUsedMb} / ${memory.heapMaxMb} MB",
            fraction = memory.heapFraction,
            color = heapColor,
        )
        BarRow(
            label = Res.string.resource_usage_memory_image_cache,
            value = "${memory.imageCacheUsedMb} / ${memory.imageCacheMaxMb} MB",
            fraction = memory.imageCacheUsedMb.toFloat() / memory.imageCacheMaxMb.coerceAtLeast(1L).toFloat(),
        )
        BarRow(
            label = Res.string.resource_usage_memory_image_disk,
            value = "${memory.imageDiskUsedMb} / ${memory.imageDiskMaxMb} MB",
            fraction = memory.imageDiskUsedMb.toFloat() / memory.imageDiskMaxMb.coerceAtLeast(1L).toFloat(),
        )
        SettingsDivider()
        MetricRow(Res.string.resource_usage_memory_native, "${memory.nativeHeapUsedMb} MB")
        SettingsDivider()
        MetricRow(Res.string.resource_usage_memory_notes, memory.noteCount.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_memory_users, memory.userCount.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_memory_addressables, memory.addressableCount.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_memory_chatrooms, memory.chatroomCount.toString())
        SettingsDivider()
        MetricRow(Res.string.resource_usage_memory_device_class, "${memory.memoryClassMb} MB")
    }
}

/**
 * Decision-support card for the always-on notification service — the one
 * background consumer users can directly turn off. Shows what the service
 * actually costs in the last 7 days (uptime, the relay connections it holds
 * while the app is closed, and the measured background battery drain) and
 * links straight to the setting that controls it.
 */
@Composable
private fun AlwaysOnServiceSection(
    s: UsageSummary,
    nav: INav,
) {
    if (s.alwaysOnMs <= 0) return
    val starts = s.alwaysOnStarts.toInt()
    SettingsSection(Res.string.resource_usage_alwayson_section) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(Res.string.resource_usage_alwayson_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MetricRow(
            Res.string.resource_usage_alwayson_uptime,
            "${formatDurationMs(s.alwaysOnMs)} · ${pluralStringRes(Res.plurals.resource_usage_alwayson_starts, starts, starts)}",
        )
        SettingsDivider()
        MetricRow(
            Res.string.resource_usage_alwayson_bg_relay,
            formatConnHours(s.relayConnMsMobileBg + s.relayConnMsWifiBg),
        )
        if (s.batteryDrainBg > 0) {
            SettingsDivider()
            MetricRow(Res.string.resource_usage_alwayson_battery, "${s.batteryDrainBg}%")
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(
                onClick = { nav.nav(Route.NotificationSettings) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringRes(Res.string.resource_usage_alwayson_settings_button))
            }
        }
    }
}

/**
 * Cost card for in-app Tor — like the always-on card: what it cost this
 * week and the settings surface that controls it.
 */
@Composable
private fun TorServiceSection(
    s: UsageSummary,
    nav: INav,
) {
    if (s.torMs <= 0) return
    val starts = s.torStarts.toInt()
    SettingsSection(Res.string.resource_usage_tor_section) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(Res.string.resource_usage_tor_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MetricRow(
            Res.string.resource_usage_alwayson_uptime,
            "${formatDurationMs(s.torMs)} · ${pluralStringRes(Res.plurals.resource_usage_alwayson_starts, starts, starts)}",
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(
                onClick = { nav.nav(Route.PrivacyOptions) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringRes(Res.string.resource_usage_tor_settings_button))
            }
        }
    }
}

@Composable
private fun SendReportSection(
    accountViewModel: AccountViewModel,
    nav: INav,
    days: Map<Long, Map<String, Long>>,
    today: Long,
    memory: MemorySnapshot?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Suspending, and off Main: assembling the report walks every counter of every
    // retained day (UsageSummary makes ~54 passes per summary) over a key space the
    // churn counters grew by an order of magnitude. `scope` is Main.immediate, so a
    // bare `scope.launch { }` would still build it on the UI thread — the
    // withContext is what actually moves it. Only the handover (clipboard, chooser,
    // navigation) stays on Main.
    suspend fun buildReport(): String = withContext(Dispatchers.Default) { ResourceUsageReportAssembler().buildReport(days, today, memory) }

    SettingsSection(Res.string.resource_usage_send_section) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(Res.string.resource_usage_send_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The DM route needs a composer to exist before the text does, which makes
            // it a poor fit for reading the report yourself — copying out of a draft
            // message is the only way to get at it. Copy and Share hand over the same
            // string directly, for pasting into an issue or saving to a file.
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        scope.launch { clipboard.setText(buildReport()) }
                    },
                ) {
                    Text(stringRes(Res.string.resource_usage_copy_button))
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            val send =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, buildReport())
                                    putExtra(Intent.EXTRA_TITLE, loadStringRes(Res.string.resource_usage_send_section))
                                }
                            context.startActivity(
                                Intent.createChooser(send, loadStringRes(Res.string.resource_usage_share_button)),
                            )
                        }
                    },
                ) {
                    Text(stringRes(Res.string.resource_usage_share_button))
                }
                Button(
                    onClick = {
                        scope.launch {
                            val report = buildReport()
                            nav.nav {
                                routeToMessage(
                                    user = LocalCache.getOrCreateUser(DEV_REPORT_PUBKEY),
                                    draftMessage = report,
                                    accountViewModel = accountViewModel,
                                    expiresDays = 30,
                                )
                            }
                        }
                    },
                ) {
                    Text(stringRes(Res.string.resource_usage_send_button))
                }
            }
        }
    }
}
