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
package com.vitorpamplona.amethyst.commons.service.image

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import okio.FileHandle
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Sink

/**
 * okio [FileSystem] wrapper that moves Coil's eviction `unlink()` out from
 * under the `DiskLruCache` lock.
 *
 * Verified against Coil 3.4.0's `coil3.disk.DiskLruCache`: eviction is not
 * inline on the commit thread — `completeEdit()` calls `launchCleanup()`,
 * which `launch`es on a limited-parallelism IO scope. But the cleanup
 * coroutine runs `trimToSize()` *while holding the global `DiskLruCache`
 * lock*, and `trimToSize()` → `removeEntry()` → `fileSystem.delete()` does
 * the unlink syscalls under that lock. Every `openSnapshot()` (read) and
 * `openEditor()` (write) contends on the same lock. So on a saturated
 * cache, each cleanup pass blocks all feed-scroll reads and writes for the
 * duration of a burst of `delete()` syscalls — that is the scroll stall
 * users see go away after a storage wipe.
 *
 * This wrapper intercepts `delete()` — exactly the call `removeEntry()`
 * makes — and, instead of unlinking inline, enqueues the path and unlinks
 * it later on a background coroutine, one path at a time with a `yield()`
 * between each so the IO dispatcher stays responsive. `delete()` returns in
 * microseconds, so `trimToSize()` releases the lock almost immediately;
 * Coil keeps full ownership of LRU order, keys, and `size` accounting, and
 * only the syscall is rescheduled off-lock.
 *
 * Re-create race: if Coil re-fetches a URL whose entry was just evicted, it
 * writes a new file at the same path. [atomicMove], [sink], [appendingSink],
 * [openReadWrite] and [createDirectory] therefore cancel any pending delete
 * of the path they are about to (re)create, so the drainer can never unlink
 * a freshly written file.
 *
 * The drainer unlinks each path while holding [lock], so a concurrent
 * write-path call that needs to cancel a delete waits at most one in-flight
 * `unlink()` — O(1), never the O(N) burst this class exists to remove.
 *
 * This instance is dedicated to Coil's image disk cache, so every path it
 * sees is a cache file and deferring all `delete()` calls is safe.
 */
class DeferredDeleteFileSystem(
    delegate: FileSystem,
    scope: CoroutineScope,
) : ForwardingFileSystem(delegate) {
    companion object {
        private const val TAG = "DeferredDeleteFileSystem"
    }

    private val lock = KmpLock()
    private val pendingDeletes = LinkedHashSet<Path>()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in wakeUp) {
                drainPending()
            }
        }
    }

    override fun delete(
        path: Path,
        mustExist: Boolean,
    ) {
        lock.withLock { pendingDeletes.add(path) }
        wakeUp.trySend(Unit)
    }

    override fun atomicMove(
        source: Path,
        target: Path,
    ) {
        lock.withLock {
            pendingDeletes.remove(source)
            pendingDeletes.remove(target)
        }
        super.atomicMove(source, target)
    }

    override fun sink(
        file: Path,
        mustCreate: Boolean,
    ): Sink {
        lock.withLock { pendingDeletes.remove(file) }
        return super.sink(file, mustCreate)
    }

    override fun appendingSink(
        file: Path,
        mustExist: Boolean,
    ): Sink {
        lock.withLock { pendingDeletes.remove(file) }
        return super.appendingSink(file, mustExist)
    }

    override fun openReadWrite(
        file: Path,
        mustCreate: Boolean,
        mustExist: Boolean,
    ): FileHandle {
        lock.withLock { pendingDeletes.remove(file) }
        return super.openReadWrite(file, mustCreate, mustExist)
    }

    override fun createDirectory(
        dir: Path,
        mustCreate: Boolean,
    ) {
        lock.withLock { pendingDeletes.remove(dir) }
        super.createDirectory(dir, mustCreate)
    }

    /** Visible for tests: number of paths waiting to be unlinked. */
    fun pendingCount(): Int = lock.withLock { pendingDeletes.size }

    /** Visible for tests: unlink everything currently queued, synchronously. */
    fun drainNow() {
        while (true) {
            lock.withLock {
                val iterator = pendingDeletes.iterator()
                if (!iterator.hasNext()) return
                val path = iterator.next()
                iterator.remove()
                deleteUnderLock(path)
            }
        }
    }

    private suspend fun drainPending() {
        while (true) {
            lock.withLock {
                val iterator = pendingDeletes.iterator()
                if (!iterator.hasNext()) return
                val path = iterator.next()
                iterator.remove()
                deleteUnderLock(path)
            }
            yield()
        }
    }

    private fun deleteUnderLock(path: Path) {
        try {
            super.delete(path, mustExist = false)
        } catch (e: IOException) {
            Log.w(TAG, "deferred delete failed: $path", e)
        }
    }
}
