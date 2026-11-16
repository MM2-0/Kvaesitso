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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Integration test against the real Coil 3 [DiskCache], pinned to whatever
 * Coil version the app actually ships.
 *
 * Verifies the assumption [DeferredDeleteFileSystem] is built on: that Coil's
 * eviction (`trimToSize` → `removeEntry`) routes its unlink through
 * `fileSystem.delete()`. The pure unit tests prove the wrapper's behaviour;
 * this proves Coil drives it. If a future Coil version stops deleting via the
 * injected `FileSystem`, [coilEviction_routesEvictionThroughDeferredDelete]
 * fails and the wrapper has silently become a no-op.
 */
class DeferredDeleteFileSystemCoilIntegrationTest {
    private lateinit var tmpRoot: File
    private lateinit var cacheDir: Path
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        tmpRoot = Files.createTempDirectory("coil-integration-test").toFile()
        cacheDir = tmpRoot.resolve("image_cache").toOkioPath()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel("tearDown")
        tmpRoot.deleteRecursively()
    }

    /** Count of regular files Coil has physically on disk in the cache dir. */
    private fun entryFilesOnDisk(fs: FileSystem): Int = fs.list(cacheDir).count { fs.metadataOrNull(it)?.isRegularFile == true }

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
    fun coilEviction_routesEvictionThroughDeferredDelete() {
        // Inert drainer: nothing is enqueued before the cancel, so the background
        // drainer never runs and deferred deletes accumulate for inspection.
        val deferredFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        scope.cancel("test inspects pendingDeletes directly")

        val maxSize = 16L * 1024 // 16 KB
        val entrySize = 1024 // 1 KB payload each
        val entryCount = 40 // 40 KB written → Coil must evict well past maxSize

        val diskCache =
            DiskCache
                .Builder()
                .directory(cacheDir)
                .fileSystem(deferredFs)
                .maxSizeBytes(maxSize)
                .build()

        try {
            repeat(entryCount) { i -> writeEntry(diskCache, "key$i", entrySize) }

            // Coil's cleanup is async on its own cleanupScope. Wait for it to
            // bring its size accounting at/under the cap.
            val deadline = System.currentTimeMillis() + 5_000
            while (diskCache.size > maxSize && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }

            // Coil actually evicted: its size accounting is at/under the cap.
            assertTrue(
                "Coil should have evicted down to <= maxSize, size=${diskCache.size}",
                diskCache.size <= maxSize,
            )

            // The headline assertion: eviction's unlink syscalls were intercepted
            // by DeferredDeleteFileSystem.delete() and are sitting in its queue.
            // If Coil ever stops deleting via the injected FileSystem this is 0.
            assertTrue(
                "Coil eviction must route unlink() through DeferredDeleteFileSystem.delete()",
                deferredFs.pendingCount() > 0,
            )

            // Because the drainer is inert, those files are still physically on
            // disk even though Coil's size counter already dropped — i.e. the
            // unlink was genuinely deferred, not executed inline.
            val filesBeforeDrain = entryFilesOnDisk(FileSystem.SYSTEM)
            assertTrue(
                "deferred entry files should still be on disk before drain " +
                    "(onDisk=$filesBeforeDrain, coilSize=${diskCache.size})",
                filesBeforeDrain * entrySize.toLong() > diskCache.size,
            )

            // Draining executes the real unlinks.
            deferredFs.drainNow()
            assertEquals(0, deferredFs.pendingCount())

            val filesAfterDrain = entryFilesOnDisk(FileSystem.SYSTEM)
            assertTrue(
                "drain must physically unlink the deferred files " +
                    "(before=$filesBeforeDrain, after=$filesAfterDrain)",
                filesAfterDrain < filesBeforeDrain,
            )
        } finally {
            diskCache.shutdown()
        }
    }

    @Test
    fun coilReFetchAfterEviction_survivesDeferredDelete() {
        // Live drainer this time: exercises the re-create race against real Coil.
        val deferredFs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)

        val maxSize = 8L * 1024
        val entrySize = 1024

        val diskCache =
            DiskCache
                .Builder()
                .directory(cacheDir)
                .fileSystem(deferredFs)
                .maxSizeBytes(maxSize)
                .build()

        try {
            // Saturate, forcing eviction of the earliest keys.
            repeat(30) { i -> writeEntry(diskCache, "key$i", entrySize) }

            // Re-fetch a key that was almost certainly evicted (an early one).
            // Coil opens a new editor and atomicMoves into the same path that
            // a deferred delete may still be queued for.
            writeEntry(diskCache, "key0", entrySize)

            // Let any background drain settle.
            val deadline = System.currentTimeMillis() + 5_000
            while (deferredFs.pendingCount() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }

            // The re-fetched entry must be readable — the deferred delete of its
            // path must not have clobbered the freshly committed file.
            val snapshot = diskCache.openSnapshot("key0")
            assertTrue("re-fetched entry must survive the deferred-delete race", snapshot != null)
            snapshot?.use {
                val size = diskCache.fileSystem.metadataOrNull(it.data)?.size ?: -1L
                assertEquals(entrySize.toLong(), size)
            }
        } finally {
            diskCache.shutdown()
        }
    }
}
