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
package com.vitorpamplona.amethyst.service.resourceusage

import android.os.Build
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.MemorySnapshot
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageSummary
import java.util.Locale

/**
 * Assembles the Markdown resource-usage report the user can DM to the
 * developers via NIP-17 — same shape as the crash ReportAssembler: a device
 * header table, a human-readable summary, then the full per-day counter dump
 * as the technical payload. Counters are sizes/durations/counts only; no
 * URLs, relay names, or content.
 */
class ResourceUsageReportAssembler {
    fun buildReport(
        days: Map<Long, Map<String, Long>>,
        today: Long,
        memory: MemorySnapshot? = null,
    ): String {
        val sb = StringBuilder()
        sb.append("Resource Usage Report: ")
        sb.append(BuildConfig.VERSION_NAME)
        sb.append("-")
        sb.append(BuildConfig.FLAVOR.uppercase())
        sb.append("\n\n")

        sb.append("| Prop | Value |\n")
        sb.append(TABLE_SEPARATOR)
        sb.append("| Manuf | ${Build.MANUFACTURER} |\n")
        sb.append("| Model | ${Build.MODEL} |\n")
        sb.append("| Android | ${Build.VERSION.RELEASE} |\n")
        sb.append("| SDK Int | ${Build.VERSION.SDK_INT} |\n")
        sb.append("\n")

        val todayCounters = days[today].orEmpty()
        val weekCounters = (today - 6..today).mapNotNull { days[it] }

        sb.append("**Today**\n\n")
        sb.append(summaryTable(UsageSummary.from(todayCounters)))
        sb.append("\n**Last 7 days**\n\n")
        sb.append(summaryTable(UsageSummary.fromDays(weekCounters)))

        if (memory != null) {
            sb.append("\n**Memory right now**\n\n")
            sb.append("| Metric | Value |\n")
            sb.append(TABLE_SEPARATOR)
            sb.append("| Device class | ${memory.memoryClassMb} MB |\n")
            sb.append("| App heap | ${memory.heapUsedMb} / ${memory.heapMaxMb} MB |\n")
            sb.append("| Native heap | ${memory.nativeHeapUsedMb} MB |\n")
            sb.append("| Image cache (RAM) | ${memory.imageCacheUsedMb} / ${memory.imageCacheMaxMb} MB |\n")
            sb.append("| Image cache (disk) | ${memory.imageDiskUsedMb} / ${memory.imageDiskMaxMb} MB |\n")
            sb.append("| Cached notes/users/addressables/chatrooms | ${memory.noteCount}/${memory.userCount}/${memory.addressableCount}/${memory.chatroomCount} |\n")
        }

        sb.append("\nTechnical details (per epoch-day):\n")
        sb.append("```\n")
        val sorted = days.toSortedMap()
        val included = newestDaysWithin(sorted, MAX_DUMP_CHARS)
        sorted.forEach { (day, counters) ->
            if (day !in included) return@forEach
            sb.append("day $day (today=$today)\n")
            counters.toSortedMap().forEach { (key, value) ->
                sb.append("    $key = $value\n")
            }
        }
        val omitted = sorted.size - included.size
        if (omitted > 0) {
            sb.append("($omitted earlier day(s) omitted to keep this report sendable; ")
            sb.append("the summary tables above still cover them)\n")
        }
        sb.append("```\n")
        return sb.toString()
    }

    /**
     * The most recent days whose dumps fit in [budget], newest first, always
     * including at least the newest even if it alone exceeds it.
     *
     * Bounded by size rather than by a day count because the per-day size is not a
     * constant: it tracks how many distinct counters the build emits, and that has
     * grown by more than an order of magnitude. A fixed day count would have to be
     * re-tuned every time a counter family is added, and would be wrong in the
     * meantime.
     */
    private fun newestDaysWithin(
        days: Map<Long, Map<String, Long>>,
        budget: Int,
    ): Set<Long> {
        val included = mutableSetOf<Long>()
        var left = budget
        for (day in days.keys.sortedDescending()) {
            val size = days.getValue(day).entries.sumOf { it.key.length + DUMP_LINE_OVERHEAD }
            if (included.isNotEmpty() && size > left) break
            included.add(day)
            left -= size
        }
        return included
    }

    private fun summaryTable(s: UsageSummary): String =
        buildString {
            append("| Metric | Value |\n")
            append(TABLE_SEPARATOR)
            append("| Cellular data (background) | ${formatBytes(s.mobileBytesBg)} |\n")
            append("| Cellular data (foreground) | ${formatBytes(s.mobileBytesFg)} |\n")
            append("| Wi-Fi data | ${formatBytes(s.wifiBytesBg + s.wifiBytesFg)} |\n")
            append("| Relay connection time | ${formatConnHours(s.relayConnMs)} |\n")
            append("| ... while backgrounded on cellular | ${formatConnHours(s.relayConnMsMobileBg)} |\n")
            append("| Notification wakelock | ${formatDurationMs(s.wakelockMs)} (${s.wakelockCount}x) |\n")
            append("| Relay reconnections | ${s.relayConnects} (${s.relayConnectFails} failed) |\n")
            append("| Web requests | ${s.httpRequests} (${s.radioBursts} radio bursts, ${formatDurationMs(s.httpActiveMs)} active) |\n")
            append("| Media playback | ${formatDurationMs(s.mediaPlayMs)} |\n")
            append("| PoW mining | ${formatDurationMs(s.powMs)} |\n")
            append("| Tor uptime (in-app) | ${formatDurationMs(s.torMs)} |\n")
            append("| Always-on service | ${formatDurationMs(s.alwaysOnMs)} (${s.alwaysOnStarts} starts) |\n")
            append("| Calls / audio rooms | ${formatDurationMs(s.callMs)} / ${formatDurationMs(s.nestsMs)} |\n")
            append("| Location listening | ${formatDurationMs(s.locationMs)} |\n")
            append("| Signatures verified | ${s.verifyCount} (${formatDurationMs(s.verifyUs / 1_000)} CPU) |\n")
            append("| Decryptions | ${s.decryptCount} (${formatDurationMs(s.decryptUs / 1_000)} CPU) |\n")
            append("| Remote signatures | ${s.signNip46} NIP-46, ${s.signNip55} NIP-55 |\n")
            append("| Battery drain (measured, whole device) | ${s.batteryDrainFg}% in app, ${s.batteryDrainBg}% background |\n")
            append("| App CPU time | ${formatDurationMs(s.cpuMs)} |\n")
            append("| Time in app | ${formatDurationMs(s.foregroundMs)} |\n")
            append("| Background worker runs | ${s.workerRuns} |\n")
            append("| App process starts | ${s.appStarts} |\n")
            val subsystems =
                s.bytesPerSubsystem.entries
                    .sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key} ${formatBytes(it.value)}" }
            if (subsystems.isNotEmpty()) {
                append("| By subsystem | $subsystems |\n")
            }
            val screens =
                s.screenTimeMs.entries
                    .sortedByDescending { it.value }
                    .take(8)
                    .joinToString(", ") { "${it.key} ${formatDurationMs(it.value)}" }
            if (screens.isNotEmpty()) {
                append("| Screen time | $screens |\n")
            }
        }

    companion object {
        /** Markdown table header/body separator row. */
        private const val TABLE_SEPARATOR = "| --- | --- |\n"

        /**
         * Character budget for the raw per-day dump.
         *
         * This report exists to be sent to the developers as a NIP-17 DM, and relays
         * commonly cap events between 64 and 256 KB — so an unbounded dump does not
         * merely inconvenience, it makes the report unsendable by exactly the users
         * whose ledgers are most worth seeing. It also travels through a ~1 MB Binder
         * transaction when shared.
         *
         * The ledger keeps 30 days and a busy day now emits ~1,200 counters, which is
         * ~52 KB of dump per day — so "every retained day" would be ~1.5 MB. This
         * keeps the newest days and says how many it dropped; the summary tables
         * above are unaffected and still cover the whole window.
         */
        private const val MAX_DUMP_CHARS = 64 * 1024

        /** `    ` + ` = ` + the value, per dumped line. */
        private const val DUMP_LINE_OVERHEAD = 24

        fun formatBytes(bytes: Long): String =
            when {
                bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }

        /** Relay-connection time: Σ connections x time, so shown as "relay-hours". */
        fun formatConnHours(ms: Long): String = String.format(Locale.US, "%.1f relay-hours", ms / (1000.0 * 60.0 * 60.0))

        fun formatDurationMs(ms: Long): String =
            when {
                ms >= 60L * 60L * 1000L -> String.format(Locale.US, "%.1f h", ms / (1000.0 * 60.0 * 60.0))
                ms >= 60L * 1000L -> String.format(Locale.US, "%.1f min", ms / (1000.0 * 60.0))
                else -> String.format(Locale.US, "%.1f s", ms / 1000.0)
            }
    }
}
