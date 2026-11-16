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
package com.vitorpamplona.amethyst.commons.service.resourceusage

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Durable daily buckets for the resource-usage ledger, one JSON file in the
 * app's private filesDir. Same persistence idiom as ScheduledPostStore:
 * Jackson + Mutex + write-to-tmp-then-rename + version envelope.
 *
 * Day keys are UTC epoch-days (stringified for JSON). Buckets older than
 * [keepDays] are pruned on every merge, so the file stays small (a few KB, on a
 * key space the churn counters tripled but left compile-time bounded). The whole
 * file is re-serialized on every flush (debounced to ~30s while traffic flows),
 * so that size is paid on each write, not just at rest.
 * Also carries the high-consumption alert state (last prompt time, opt-out)
 * so the whole feature has exactly one file.
 */
class ResourceUsageStore(
    private val storageFile: File,
    /**
     * Retention, in days.
     *
     * Seven because that is the widest window anything reads: the summary tables and
     * the trend chart both span `today - 6 .. today`, the alert evaluator looks at
     * two days, and the report's raw dump is byte-bounded well below a week. At 30 —
     * the previous value — twenty-three days were rewritten on every flush and read
     * by nothing.
     *
     * That is not free: [persist] rewrites the whole file on every merge, and the
     * relay-churn counters took a day's bucket from ~57 keys to ~241. Retention is
     * therefore a write-amplification setting as much as a history setting — cutting
     * it to a week is what keeps those counters at ~18% over the previous file size
     * rather than ~5x.
     */
    private val keepDays: Long = 7,
) {
    @Serializable
    data class UsageFile(
        val version: Int = 1,
        val days: Map<String, Map<String, Long>> = emptyMap(),
        val lastAlertAtSec: Long = 0L,
        val alertsOptOut: Boolean = false,
    )

    /**
     * `encodeDefaults = true` so this writes the same bytes Jackson did — kotlinx
     * omits a value equal to its default, which would silently drop `"version":1`
     * from every file this build rewrites. `ignoreUnknownKeys` matches the
     * FAIL_ON_UNKNOWN_PROPERTIES=false it replaces, so a file written by a newer
     * build still loads here.
     */
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val mutex = Mutex()
    private var loaded = false
    private var data = UsageFile()

    suspend fun mergeInto(
        day: Long,
        deltas: Map<String, Long>,
    ) {
        if (deltas.isEmpty()) return
        mutex.withLock {
            ensureLoaded()
            val dayKey = day.toString()
            val bucket = data.days[dayKey].orEmpty().toMutableMap()
            deltas.forEach { (key, amount) -> bucket[key] = (bucket[key] ?: 0L) + amount }
            val pruned =
                data.days
                    .filterKeys { (it.toLongOrNull() ?: Long.MAX_VALUE) >= day - keepDays }
                    .toMutableMap()
            pruned[dayKey] = bucket
            data = data.copy(days = pruned)
            persist()
        }
    }

    /** All persisted daily buckets, keyed by epoch-day. */
    suspend fun allDays(): Map<Long, Map<String, Long>> =
        mutex.withLock {
            ensureLoaded()
            data.days.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()
        }

    suspend fun lastAlertAtSec(): Long =
        mutex.withLock {
            ensureLoaded()
            data.lastAlertAtSec
        }

    suspend fun alertsOptOut(): Boolean =
        mutex.withLock {
            ensureLoaded()
            data.alertsOptOut
        }

    suspend fun markAlertPrompted(atSec: Long) =
        mutex.withLock {
            ensureLoaded()
            data = data.copy(lastAlertAtSec = atSec)
            persist()
        }

    suspend fun setAlertsOptOut(optOut: Boolean) =
        mutex.withLock {
            ensureLoaded()
            data = data.copy(alertsOptOut = optOut)
            persist()
        }

    private fun ensureLoaded() {
        if (loaded) return
        data =
            try {
                if (storageFile.exists() && storageFile.length() > 0) {
                    json.decodeFromString<UsageFile>(storageFile.readText())
                } else {
                    UsageFile()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load resource usage from $storageFile", e)
                UsageFile()
            }
        loaded = true
    }

    private fun persist() {
        storageFile.parentFile?.mkdirs()
        val tmp = File(storageFile.parentFile, storageFile.name + ".tmp")
        try {
            tmp.writeText(json.encodeToString(data))
            if (!tmp.renameTo(storageFile)) {
                if (!storageFile.delete() || !tmp.renameTo(storageFile)) {
                    Log.e(TAG) { "Failed to rename $tmp to $storageFile" }
                    if (!tmp.delete()) {
                        Log.w(TAG) { "Failed to clean up temp file $tmp" }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist resource usage to $storageFile", e)
            if (!tmp.delete()) {
                Log.w(TAG) { "Failed to clean up temp file $tmp" }
            }
        }
    }

    companion object {
        private const val TAG = "ResourceUsageStore"
        const val FILE_NAME = "resource_usage.json"
    }
}
