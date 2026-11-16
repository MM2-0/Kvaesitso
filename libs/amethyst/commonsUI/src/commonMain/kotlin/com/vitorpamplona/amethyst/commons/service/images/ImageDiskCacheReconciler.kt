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
package com.vitorpamplona.amethyst.commons.service.images

import coil3.disk.DiskCache
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import okio.IOException
import okio.Path

/** What one [ImageDiskCacheReconciler] pass found, and how many files it unlinked. */
data class ImageCacheReconciliation(
    val bytesOnDisk: Long,
    val budgetBytes: Long,
    val ceilingBytes: Long,
    val reclaimedFiles: Int,
) {
    val wasOverBudget get() = reclaimedFiles > 0
}

/**
 * Brings the image cache directory back under its own size budget at startup.
 *
 * [DeferredDeleteFileSystem] moves Coil's eviction `unlink()` off the `DiskLruCache` lock by
 * queueing it in memory. Anything still queued when the process dies is never unlinked — and Coil
 * cannot recover it, because `DiskLruCache.processJournal()` computes `size` purely from the
 * journal's recorded lengths and never scans the directory for files it does not know about. The
 * orphan counts toward neither `size` nor eviction, so the directory keeps whatever residue every
 * killed process left behind, forever.
 *
 * Measured against the real Coil `DiskCache`: 48 unlinks lost to one process death left Coil
 * reporting 16 KB against a 16 KB budget while the directory actually held 48 KB — and reopening
 * as a fresh process reclaimed none of it.
 *
 * `DiskCache.clear()` is not enough on its own: it evicts `lruEntries`, which is exactly the set of
 * files the journal knows about, so it walks straight past the orphans. Hence the two steps below —
 * empty the journal, then unlink whatever is still sitting in the directory.
 *
 * This is deliberately blunt. It costs the whole cache, so it only fires once the directory has
 * drifted past [ceilingBytes] — well beyond the slack normal operation needs. It does not read the
 * journal, so it stays independent of Coil's on-disk format.
 *
 * Drift accrues only when a process dies with unlinks still queued, which is slow, so
 * [reconcileIfDue] rate-limits the check to [DEFAULT_INTERVAL_MS]. Even the healthy pass is a
 * `readdir` plus a `stat` per file — on a full 1 GB cache, tens of thousands of syscalls — and
 * `AppModules.initiate()` runs on every process start, including the WorkManager wake-ups that
 * cold-start the whole graph. Gating it on a marker file's mtime costs one `stat` on the starts
 * that skip.
 */
object ImageDiskCacheReconciler {
    /**
     * Fraction of the cache's own budget allowed on top of it before the directory counts as
     * drifted rather than merely busy.
     */
    private const val DEFAULT_SLACK_FRACTION = 0.25

    /**
     * Floor for that slack, so a cache with a small budget (20% of a nearly-full disk) is not wiped
     * over a few megabytes. Covers the journal plus the dirty files of any writes in flight.
     */
    private const val DEFAULT_MIN_SLACK_BYTES = 4L * 1024 * 1024

    /** How long one pass is good for. Drift accrues over days, so checking daily is ample. */
    private const val DEFAULT_INTERVAL_MS = 24L * 60 * 60 * 1000

    /**
     * Empty file whose mtime is the last pass. It lives inside the cache directory so it travels
     * with the thing it describes: clearing the app's cache from Settings takes the marker with it,
     * and the next start reconciles a directory whose history we no longer know.
     */
    private const val MARKER_FILE = ".reconciled"

    /** Coil's own bookkeeping, which is not entry data and must survive a wipe. */
    private val JOURNAL_FILES = setOf("journal", "journal.tmp", "journal.bkp")

    /** Files in the cache directory that are not entry data and must survive a wipe. */
    private val PRESERVED_FILES = JOURNAL_FILES + MARKER_FILE

    /** Bytes the directory may hold before [reconcile] wipes it. */
    fun ceilingBytes(
        budgetBytes: Long,
        slackFraction: Double = DEFAULT_SLACK_FRACTION,
        minSlackBytes: Long = DEFAULT_MIN_SLACK_BYTES,
    ): Long = budgetBytes + maxOf((budgetBytes * slackFraction).toLong(), minSlackBytes)

    /**
     * Runs [reconcile] if the last pass is older than [intervalMs], else returns null having done
     * one `stat`.
     *
     * Blocking IO — call it from a background dispatcher.
     */
    fun reconcileIfDue(
        diskCache: DiskCache,
        now: Long = TimeUtils.nowMillis(),
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        slackFraction: Double = DEFAULT_SLACK_FRACTION,
        minSlackBytes: Long = DEFAULT_MIN_SLACK_BYTES,
    ): ImageCacheReconciliation? {
        if (!isDue(diskCache, now, intervalMs)) return null

        return reconcile(diskCache, slackFraction, minSlackBytes).also { markPass(diskCache) }
    }

    /**
     * True when no pass is recorded, or the recorded one is [intervalMs] old.
     *
     * A marker dated in the future — a clock that jumped back, or a restored backup — would
     * otherwise park the check until real time caught up, so that also counts as due.
     */
    private fun isDue(
        diskCache: DiskCache,
        now: Long,
        intervalMs: Long,
    ): Boolean {
        val lastPass =
            try {
                diskCache.fileSystem.metadataOrNull(diskCache.directory / MARKER_FILE)?.lastModifiedAtMillis
            } catch (e: IOException) {
                Log.d("ImageDiskCache") { "could not stat the marker: ${e.message}" }
                null
            } ?: return true

        return now - lastPass >= intervalMs || lastPass > now
    }

    /** Records that a pass just happened, by writing the marker's mtime to now. */
    private fun markPass(diskCache: DiskCache) {
        try {
            diskCache.fileSystem.createDirectories(diskCache.directory)
            diskCache.fileSystem.write(diskCache.directory / MARKER_FILE) {}
        } catch (e: IOException) {
            // Losing the marker only costs a redundant walk on the next start.
            Log.d("ImageDiskCache") { "could not write the marker: ${e.message}" }
        }
    }

    /**
     * Walks the cache directory and, if it holds more than [ceilingBytes], empties it.
     *
     * Blocking IO — call it from a background dispatcher. Prefer [reconcileIfDue]; this is the
     * unconditional pass.
     *
     * Deliberately does not read `DiskCache.size`: that would force the journal parse on the happy
     * path, and the decision does not need it. A request racing the wipe can lose the entry it was
     * writing; Coil treats that as a cache miss and re-fetches, which is why this runs at startup
     * rather than while the feed is scrolling.
     */
    fun reconcile(
        diskCache: DiskCache,
        slackFraction: Double = DEFAULT_SLACK_FRACTION,
        minSlackBytes: Long = DEFAULT_MIN_SLACK_BYTES,
    ): ImageCacheReconciliation {
        val budget = diskCache.maxSize
        val ceiling = ceilingBytes(budget, slackFraction, minSlackBytes)

        val bytesOnDisk = regularFiles(diskCache).sumOf { sizeOf(diskCache, it) }
        if (bytesOnDisk <= ceiling) {
            return ImageCacheReconciliation(bytesOnDisk, budget, ceiling, 0)
        }

        Log.w(
            "ImageDiskCache",
            "Image cache holds $bytesOnDisk bytes against a $budget budget (ceiling $ceiling) — " +
                "wiping it. Deferred unlinks lost to a process death leave files Coil can no longer see.",
        )

        // Empties the journal, so every entry file left below is unreferenced by definition.
        // Its own unlinks go through the deferred file system like any other eviction; the paths
        // below are re-queued rather than double-unlinked, which the pending set dedupes.
        diskCache.clear()

        var reclaimed = 0
        regularFiles(diskCache).forEach { path ->
            if (path.name in PRESERVED_FILES) return@forEach
            try {
                diskCache.fileSystem.delete(path, mustExist = false)
                reclaimed++
            } catch (e: IOException) {
                Log.w("ImageDiskCache", "could not unlink $path", e)
            }
        }

        return ImageCacheReconciliation(bytesOnDisk, budget, ceiling, reclaimed)
    }

    private fun regularFiles(diskCache: DiskCache): List<Path> =
        try {
            diskCache.fileSystem
                .listOrNull(diskCache.directory)
                .orEmpty()
                .filter { diskCache.fileSystem.metadataOrNull(it)?.isRegularFile == true }
        } catch (e: IOException) {
            // A missing directory is the normal first-launch state, not a failure.
            Log.d("ImageDiskCache") { "could not list ${diskCache.directory}: ${e.message}" }
            emptyList()
        }

    private fun sizeOf(
        diskCache: DiskCache,
        path: Path,
    ): Long = diskCache.fileSystem.metadataOrNull(path)?.size ?: 0L
}
