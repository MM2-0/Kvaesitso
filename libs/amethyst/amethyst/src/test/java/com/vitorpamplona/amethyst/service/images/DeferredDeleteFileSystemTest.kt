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

import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DeferredDeleteFileSystemTest {
    private lateinit var tmpRoot: File
    private lateinit var tmpDir: Path

    @Before
    fun setUp() {
        tmpRoot = Files.createTempDirectory("deferred-delete-test").toFile()
        tmpDir = tmpRoot.toOkioPath()
    }

    @After
    fun tearDown() {
        tmpRoot.deleteRecursively()
    }

    private fun write(
        name: String,
        content: String,
    ): Path {
        val path = tmpDir.resolve(name)
        FileSystem.SYSTEM.write(path) { writeUtf8(content) }
        return path
    }

    private fun read(path: Path): String = FileSystem.SYSTEM.read(path) { readUtf8() }

    /**
     * Builds a wrapper whose background drainer is immediately cancelled, so
     * the test drives eviction deterministically through [DeferredDeleteFileSystem.drainNow].
     * Nothing is enqueued before the cancel, so the drainer never processes a delete.
     */
    private fun newInertWrapper(): DeferredDeleteFileSystem {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
        scope.cancel("test drives drainNow() explicitly")
        return fs
    }

    @Test
    fun delete_doesNotUnlinkImmediately() {
        val fs = newInertWrapper()
        val path = write("a.txt", "hello")

        fs.delete(path)

        assertTrue("file must still exist right after delete()", FileSystem.SYSTEM.exists(path))
        assertEquals(1, fs.pendingCount())
    }

    @Test
    fun drainNow_unlinksPendingFiles() {
        val fs = newInertWrapper()
        val a = write("a.txt", "1")
        val b = write("b.txt", "2")

        fs.delete(a)
        fs.delete(b)
        assertEquals(2, fs.pendingCount())

        fs.drainNow()

        assertFalse(FileSystem.SYSTEM.exists(a))
        assertFalse(FileSystem.SYSTEM.exists(b))
        assertEquals(0, fs.pendingCount())
    }

    @Test
    fun delete_dedupesSamePath() {
        val fs = newInertWrapper()
        val path = write("a.txt", "x")

        fs.delete(path)
        fs.delete(path)
        fs.delete(path)

        assertEquals(1, fs.pendingCount())
    }

    @Test
    fun atomicMove_cancelsPendingDeleteOfTarget() {
        val fs = newInertWrapper()
        val target = write("entry.0", "old-evicted-content")
        val source = write("entry.0.tmp", "fresh-refetched-content")

        // Coil evicts the entry...
        fs.delete(target)
        assertEquals(1, fs.pendingCount())

        // ...then re-fetches the same URL and commits a new file to the same path.
        fs.atomicMove(source, target)

        // The pending delete of the target must have been cancelled.
        assertEquals(0, fs.pendingCount())
        fs.drainNow()
        assertTrue("freshly committed file must survive", FileSystem.SYSTEM.exists(target))
        assertEquals("fresh-refetched-content", read(target))
    }

    @Test
    fun atomicMove_cancelsPendingDeleteOfSource() {
        val fs = newInertWrapper()
        val source = write("entry.0.tmp", "data")
        val target = tmpDir.resolve("entry.0")

        fs.delete(source)
        assertEquals(1, fs.pendingCount())

        fs.atomicMove(source, target)

        assertEquals(0, fs.pendingCount())
        fs.drainNow()
        assertTrue(FileSystem.SYSTEM.exists(target))
        assertFalse(FileSystem.SYSTEM.exists(source))
    }

    @Test
    fun sink_cancelsPendingDeleteOfPath() {
        val fs = newInertWrapper()
        val path = write("entry.0", "old")

        fs.delete(path)
        assertEquals(1, fs.pendingCount())

        // Re-create the path by opening a sink and writing fresh content.
        fs.sink(path, mustCreate = false).use { sink ->
            sink.buffer().use { it.writeUtf8("new") }
        }

        assertEquals(0, fs.pendingCount())
        fs.drainNow()
        assertTrue(FileSystem.SYSTEM.exists(path))
        assertEquals("new", read(path))
    }

    @Test
    fun appendingSink_cancelsPendingDeleteOfPath() {
        val fs = newInertWrapper()
        val path = write("entry.0", "old")

        fs.delete(path)
        assertEquals(1, fs.pendingCount())

        fs.appendingSink(path, mustExist = false).use { sink ->
            sink.buffer().use { it.writeUtf8("-more") }
        }

        assertEquals(0, fs.pendingCount())
        fs.drainNow()
        assertTrue(FileSystem.SYSTEM.exists(path))
        assertEquals("old-more", read(path))
    }

    @Test
    fun openReadWrite_cancelsPendingDeleteOfPath() {
        val fs = newInertWrapper()
        val path = write("entry.0", "old")

        fs.delete(path)
        assertEquals(1, fs.pendingCount())

        fs.openReadWrite(path, mustCreate = false, mustExist = false).use { handle ->
            handle.write(0, "X".toByteArray(), 0, 1)
        }

        assertEquals(0, fs.pendingCount())
        fs.drainNow()
        assertTrue(FileSystem.SYSTEM.exists(path))
    }

    @Test
    fun createDirectory_cancelsPendingDeleteOfDir() {
        val fs = newInertWrapper()
        val dir = tmpDir.resolve("subdir")
        FileSystem.SYSTEM.createDirectory(dir)

        fs.delete(dir)
        assertEquals(1, fs.pendingCount())

        fs.createDirectory(dir, mustCreate = false)

        assertEquals(0, fs.pendingCount())
        fs.drainNow()
        assertTrue(FileSystem.SYSTEM.exists(dir))
    }

    @Test
    fun nonDeleteOperations_passThroughToDelegate() {
        val fs = newInertWrapper()
        val path = write("a.txt", "payload")

        // Reads, metadata, and listing must work unchanged through the wrapper.
        assertEquals("payload", fs.read(path) { readUtf8() })
        assertTrue(fs.exists(path))
        assertTrue(fs.metadataOrNull(path)?.isRegularFile == true)
        assertTrue(fs.list(tmpDir).contains(path))
    }

    @Test
    fun backgroundDrainer_eventuallyUnlinks() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
            val path = write("bg.txt", "data")

            fs.delete(path)

            val deadline = System.currentTimeMillis() + 5_000
            while (FileSystem.SYSTEM.exists(path) && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }

            assertFalse("background drainer should have unlinked the file", FileSystem.SYSTEM.exists(path))
            assertEquals(0, fs.pendingCount())
        } finally {
            scope.cancel("tearDown")
        }
    }

    @Test
    fun backgroundDrainer_handlesBurstThenReCreate() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val fs = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
            // Evict a burst of entries.
            val evicted = (1..50).map { write("e$it.0", "v$it") }
            evicted.forEach { fs.delete(it) }

            // Immediately re-create one of them (re-fetch race).
            val reFetched = evicted[25]
            fs.sink(reFetched, mustCreate = false).use { sink ->
                sink.buffer().use { it.writeUtf8("refetched") }
            }

            val deadline = System.currentTimeMillis() + 5_000
            while (fs.pendingCount() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }

            assertEquals(0, fs.pendingCount())
            // Every evicted file is gone...
            evicted.forEachIndexed { i, p ->
                if (i == 25) {
                    assertTrue("re-fetched file must survive the burst drain", FileSystem.SYSTEM.exists(p))
                    assertEquals("refetched", read(p))
                } else {
                    assertFalse("evicted file e${i + 1}.0 must be unlinked", FileSystem.SYSTEM.exists(p))
                }
            }
        } finally {
            scope.cancel("tearDown")
        }
    }
}
