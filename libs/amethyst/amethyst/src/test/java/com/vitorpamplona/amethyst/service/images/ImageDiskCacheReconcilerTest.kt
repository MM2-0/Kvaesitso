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
package com.vitorpamplona.amethyst.service.images

import coil3.disk.DiskCache
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import com.vitorpamplona.amethyst.commons.service.images.ImageDiskCacheReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The leak this guards against is silent from inside Coil: a deferred unlink lost to a process
 * death leaves a file that `DiskLruCache.processJournal()` never counts and never evicts, because
 * it derives `size` from the journal alone. So these tests measure the directory, not the cache.
 */
class ImageDiskCacheReconcilerTest {
    private lateinit var tmpRoot: File
    private lateinit var cacheDir: Path
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        tmpRoot = Files.createTempDirectory("image-cache-reconciler-test").toFile()
        cacheDir = tmpRoot.resolve("image_cache").toOkioPath()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
        tmpRoot.deleteRecursively()
    }

    private fun bytesOnDisk(): Long =
        FileSystem.SYSTEM
            .listOrNull(cacheDir)
            .orEmpty()
            .sumOf { FileSystem.SYSTEM.metadataOrNull(it)?.size ?: 0L }

    private fun newCache(
        fileSystem: FileSystem,
        maxSize: Long,
    ) = DiskCache
        .Builder()
        .directory(cacheDir)
        .fileSystem(fileSystem)
        .maxSizeBytes(maxSize)
        .build()

    private fun writeEntry(
        diskCache: DiskCache,
        key: String,
        bytes: Int,
    ) {
        val editor = diskCache.openEditor(key) ?: error("openEditor returned null for $key")
        try {
            diskCache.fileSystem.write(editor.data) { write(ByteArray(bytes) { 1 }) }
            editor.commit()
        } catch (e: Throwable) {
            editor.abort()
            throw e
        }
    }

    @Test
    fun orphansLostToAProcessDeath_areReclaimed() {
        // "Process one": an inert drainer stands in for a process that died with eviction's
        // unlinks still queued in memory.
        val deadProcessFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        scope.cancel("the drainer never runs — the process died first")

        val budget = 16L * 1024
        val first = newCache(deadProcessFs, budget)
        repeat(40) { i -> writeEntry(first, "key$i", 1024) }
        val deadline = System.currentTimeMillis() + 5_000
        while (first.size > budget && System.currentTimeMillis() < deadline) Thread.sleep(20)
        first.shutdown()

        val leaked = bytesOnDisk()
        assertTrue(
            "the orphans must actually exceed the ceiling, or the test proves nothing (onDisk=$leaked)",
            leaked > ImageDiskCacheReconciler.ceilingBytes(budget, minSlackBytes = 0),
        )

        // "Process two": a fresh start over the same directory.
        val liveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val liveFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, liveScope)
        val second = newCache(liveFs, budget)
        try {
            val result = ImageDiskCacheReconciler.reconcile(second, minSlackBytes = 0)

            assertTrue("should have judged the directory over budget", result.wasOverBudget)
            assertEquals(leaked, result.bytesOnDisk)

            // The reconciler unlinks through the same deferred file system as everything else.
            liveFs.drainNow()

            assertTrue(
                "directory must come back under budget (was $leaked, now ${bytesOnDisk()})",
                bytesOnDisk() <= budget,
            )
        } finally {
            second.shutdown()
            liveScope.cancel()
        }
    }

    @Test
    fun clearAlone_wouldNotHaveReclaimedThem() {
        // Pins why the reconciler does not just call DiskCache.clear(): evictAll() walks
        // lruEntries, which is exactly the set of files the journal knows about, so orphans
        // survive it. If this ever starts failing, Coil learned to sweep and this class can go.
        val deadProcessFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        scope.cancel("inert drainer")

        val budget = 16L * 1024
        val first = newCache(deadProcessFs, budget)
        repeat(40) { i -> writeEntry(first, "key$i", 1024) }
        val deadline = System.currentTimeMillis() + 5_000
        while (first.size > budget && System.currentTimeMillis() < deadline) Thread.sleep(20)
        first.shutdown()

        val leaked = bytesOnDisk()

        val liveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val liveFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, liveScope)
        val second = newCache(liveFs, budget)
        try {
            second.clear()
            liveFs.drainNow()

            assertTrue(
                "clear() should leave the orphans behind (was $leaked, now ${bytesOnDisk()})",
                bytesOnDisk() > budget,
            )
        } finally {
            second.shutdown()
            liveScope.cancel()
        }
    }

    @Test
    fun aHealthyCacheIsLeftAlone() {
        val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        val budget = 1024L * 1024
        val diskCache = newCache(fs, budget)
        try {
            repeat(10) { i -> writeEntry(diskCache, "key$i", 1024) }
            val before = bytesOnDisk()

            val result = ImageDiskCacheReconciler.reconcile(diskCache)

            assertFalse(result.wasOverBudget)
            assertEquals(0, result.reclaimedFiles)
            assertEquals(before, bytesOnDisk())
            assertNotNull("entries must still be readable", diskCache.openSnapshot("key9")?.also { it.close() })
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun driftWithinTheSlackIsLeftAlone() {
        // Being a little over the budget is normal: eviction is async, and a write in flight has a
        // dirty file on disk that the journal has not accounted for yet. Only real drift is wiped.
        val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        val budget = 16L * 1024
        val diskCache = newCache(fs, budget)
        try {
            repeat(18) { i -> writeEntry(diskCache, "key$i", 1024) }

            val result = ImageDiskCacheReconciler.reconcile(diskCache, minSlackBytes = 8 * 1024)

            assertFalse("18 KB against a 16 KB budget + 8 KB slack is not drift", result.wasOverBudget)
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun aMissingDirectoryIsANoOp() {
        val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        val diskCache = newCache(fs, 1024L * 1024)
        try {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)

            val result = ImageDiskCacheReconciler.reconcile(diskCache)

            assertEquals(0L, result.bytesOnDisk)
            assertFalse(result.wasOverBudget)
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun theShippedCeilingIsBudgetPlusAQuarter() {
        // Pins the production numbers the injectable overrides above bypass.
        val oneGb = 1024L * 1024 * 1024
        assertEquals(oneGb + oneGb / 4, ImageDiskCacheReconciler.ceilingBytes(oneGb))

        // ...but never less than the 4 MiB floor, so a small budget on a full disk is not wiped
        // over the journal plus a couple of in-flight writes.
        assertEquals(1024L * 1024 + 4L * 1024 * 1024, ImageDiskCacheReconciler.ceilingBytes(1024L * 1024))
    }

    // ---- cadence ----------------------------------------------------------------------------
    // The healthy pass is a readdir plus a stat per file, and AppModules.initiate() runs on every
    // process start — including the WorkManager wake-ups that cold-start the whole graph. Drift
    // accrues over process deaths, not startups, so the check is rate-limited.

    private val markerPath get() = cacheDir / ".reconciled"

    @Test
    fun aCacheWithNoRecordedPassIsDue() {
        val diskCache = newCache(DeferredDeleteFileSystem(FileSystem.SYSTEM, scope), 1024L * 1024)
        try {
            assertNotNull(ImageDiskCacheReconciler.reconcileIfDue(diskCache))
            assertNotNull("the pass must record itself", FileSystem.SYSTEM.metadataOrNull(markerPath))
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun aSecondStartWithinTheIntervalSkipsTheWalk() {
        val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        val budget = 16L * 1024
        val diskCache = newCache(fs, budget)
        try {
            val now = System.currentTimeMillis()
            assertNotNull(ImageDiskCacheReconciler.reconcileIfDue(diskCache, now = now))

            // Even a directory well over budget is left alone until the next pass is due — the
            // point of the gate is that the common start does no work at all.
            repeat(40) { i -> writeEntry(diskCache, "key$i", 1024) }

            val skipped = ImageDiskCacheReconciler.reconcileIfDue(diskCache, now = now + 1000, minSlackBytes = 0)

            assertNull("within the interval the pass must not run", skipped)
            // Coil's size, not the directory's bytes: eviction runs asynchronously on its own scope
            // and the drainer unlinks behind it, so comparing byte totals across the call races
            // both. A wipe is what this needs to rule out, and clear() takes the size to zero.
            assertTrue("a skipped pass must not have cleared the cache", diskCache.size > 0)
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun theIntervalIsMeasuredFromTheRecordedPass() {
        val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        val diskCache = newCache(fs, 1024L * 1024)
        try {
            val interval = 24L * 60 * 60 * 1000
            ImageDiskCacheReconciler.reconcileIfDue(diskCache, intervalMs = interval)

            // Pin the recorded pass to a whole second before measuring from it. isDue() compares
            // against the marker's mtime, and a file system that keeps mtime at whole-second
            // resolution reads it back up to a second before the write that made it — so a boundary
            // measured from our own clock instead lands a second early there. That is what made the
            // first version of this test pass on a dev box and fail on CI.
            val lastPass = (System.currentTimeMillis() / 1000) * 1000
            assertTrue("could not set the marker's mtime", File(markerPath.toString()).setLastModified(lastPass))
            assertEquals("the file system must keep the timestamp we set", lastPass, recordedPassAt())

            assertNull(
                "a millisecond before the interval is up, the pass must not run",
                ImageDiskCacheReconciler.reconcileIfDue(diskCache, now = lastPass + interval - 1, intervalMs = interval),
            )
            assertNotNull(
                "one interval after the recorded pass, it is due again",
                ImageDiskCacheReconciler.reconcileIfDue(diskCache, now = lastPass + interval, intervalMs = interval),
            )
        } finally {
            diskCache.shutdown()
        }
    }

    private fun recordedPassAt(): Long =
        requireNotNull(FileSystem.SYSTEM.metadataOrNull(markerPath)?.lastModifiedAtMillis) {
            "a pass must record a timestamp"
        }

    @Test
    fun aMarkerDatedInTheFutureDoesNotParkTheCheck() {
        // A clock that jumped back, or a restored backup, would otherwise strand the check until
        // real time caught up with the marker.
        val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        val diskCache = newCache(fs, 1024L * 1024)
        try {
            val now = System.currentTimeMillis()
            ImageDiskCacheReconciler.reconcileIfDue(diskCache, now = now)

            assertNotNull(ImageDiskCacheReconciler.reconcileIfDue(diskCache, now = now - 365L * 24 * 60 * 60 * 1000))
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun theMarkerSurvivesAWipe() {
        // The marker is a plain file in the cache directory, so the sweep would take it unless it is
        // preserved explicitly — and a wipe that erases its own record makes every later start look
        // due and walk again. Drives reconcile() rather than reconcileIfDue(), because the latter
        // rewrites the marker afterwards and would mask the deletion.
        val deadProcessFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        scope.cancel("inert drainer")

        val budget = 16L * 1024
        val first = newCache(deadProcessFs, budget)
        repeat(40) { i -> writeEntry(first, "key$i", 1024) }
        val deadline = System.currentTimeMillis() + 5_000
        while (first.size > budget && System.currentTimeMillis() < deadline) Thread.sleep(20)
        first.shutdown()

        val liveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val liveFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, liveScope)
        val second = newCache(liveFs, budget)
        try {
            // Lay down a marker the way a previous pass would have.
            FileSystem.SYSTEM.write(markerPath) {}

            val result = ImageDiskCacheReconciler.reconcile(second, minSlackBytes = 0)
            assertTrue("expected a wipe", result.wasOverBudget)

            liveFs.drainNow()

            assertNotNull("the marker must outlive the wipe", FileSystem.SYSTEM.metadataOrNull(markerPath))
        } finally {
            second.shutdown()
            liveScope.cancel()
        }
    }
}
