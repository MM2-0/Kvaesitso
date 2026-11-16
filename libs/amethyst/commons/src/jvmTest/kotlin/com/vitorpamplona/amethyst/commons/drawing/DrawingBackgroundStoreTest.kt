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
package com.vitorpamplona.amethyst.commons.drawing

import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawingBackgroundStoreTest {
    private fun store(
        maxKept: Int = DrawingBackgroundStore.MAX_KEPT,
        clock: LongArray = longArrayOf(1_000),
    ): DrawingBackgroundStore {
        val dir = Files.createTempDirectory("drawing-backgrounds").toOkioPath() / "account"
        return DrawingBackgroundStore(dir, maxKept = maxKept, nowMillis = { clock[0]++ })
    }

    private fun picture(n: Int) = ByteArray(64) { (it * n).toByte() }

    @Test
    fun nothingSavedIsAnEmptyListNotAnError() =
        runBlocking {
            assertEquals(emptyList(), store().list())
        }

    @Test
    fun savedPicturesComeBackNewestFirst() =
        runBlocking {
            val store = store()
            val first = store.save(picture(1))
            val second = store.save(picture(2))
            assertEquals(listOf(second, first), store.list())
            assertContentEquals(picture(1), store.read(first))
        }

    @Test
    fun savingTheSamePictureAgainKeepsOneCopyAndMovesItToTheFront() =
        runBlocking {
            val store = store()
            store.save(picture(1))
            store.save(picture(2))
            val again = store.save(picture(1))
            val list = store.list()
            assertEquals(2, list.size)
            assertEquals(again, list.first())
            assertContentEquals(picture(1), store.read(list.first()))
        }

    @Test
    fun onlyTheNewestAreKept() =
        runBlocking {
            val store = store(maxKept = 3)
            (1..5).forEach { store.save(picture(it)) }
            val kept = store.list().map { store.read(it)!! }
            assertEquals(3, kept.size)
            assertContentEquals(picture(5), kept[0])
            assertContentEquals(picture(3), kept[2])
        }

    @Test
    fun aDeletedPictureIsGone() =
        runBlocking {
            val store = store()
            val path = store.save(picture(1))
            store.delete(path)
            assertTrue(store.list().isEmpty())
        }

    @Test
    fun aDeleteThatFailsDoesNotThrow() =
        runBlocking {
            val store = store()
            val path = store.save(picture(1))
            // A directory with something in it cannot be deleted: the store must log it, not crash the editor.
            val stuck = path.parent!! / "stuck_dir"
            Files.createDirectories(stuck.toNioPath())
            Files.write((stuck / "child").toNioPath(), byteArrayOf(1))
            store.delete(stuck)
            assertEquals(listOf(path), store.list())
        }
}
