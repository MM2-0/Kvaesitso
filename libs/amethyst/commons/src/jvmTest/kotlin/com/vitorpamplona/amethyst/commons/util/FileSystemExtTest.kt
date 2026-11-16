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
package com.vitorpamplona.amethyst.commons.util

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSystemExtTest {
    private val root = Files.createTempDirectory("fs-ext-test").toFile()

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    /** Refuses to list one directory, as an unreadable or concurrently removed one would. */
    private class UnlistableDirFileSystem(
        private val unlistable: Path,
    ) : ForwardingFileSystem(FileSystem.SYSTEM) {
        override fun list(dir: Path): List<Path> {
            if (dir == unlistable) throw IOException("cannot list $dir")
            return super.list(dir)
        }
    }

    private fun write(
        relative: String,
        text: String = "x",
    ) {
        val file = root.resolve(relative)
        file.parentFile.mkdirs()
        file.writeText(text)
    }

    @Test
    fun deletesAWholeTree() {
        write("a/b/c.txt")
        write("a/d.txt")
        val dir = root.resolve("a").toOkioPath()

        assertTrue(FileSystem.SYSTEM.deleteRecursivelyQuietly(dir))
        assertFalse(FileSystem.SYSTEM.exists(dir))
    }

    @Test
    fun missingPathCountsAsDeleted() {
        assertTrue(FileSystem.SYSTEM.deleteRecursivelyQuietly(root.resolve("never").toOkioPath()))
    }

    @Test
    fun carriesOnPastADirectoryItCannotList() {
        // File.deleteRecursively removed everything it could reach and reported false for the
        // rest. One unlistable subdirectory must not stop its siblings from being deleted.
        write("g/stuck/inner.txt")
        write("g/sibling/messages", "plaintext")
        write("g/state")
        val dir = root.resolve("g").toOkioPath()
        val fs = UnlistableDirFileSystem(dir / "stuck")

        assertFalse(fs.deleteRecursivelyQuietly(dir))
        assertFalse(root.resolve("g/sibling/messages").exists())
        assertFalse(root.resolve("g/state").exists())
        assertTrue(root.resolve("g/stuck/inner.txt").exists())
    }

    @Test
    fun deletesASymlinkWithoutEmptyingItsTarget() {
        write("outside/keep.txt")
        write("h/own.txt")
        Files.createSymbolicLink(root.resolve("h/link").toPath(), root.resolve("outside").toPath())
        val dir = root.resolve("h").toOkioPath()

        assertTrue(FileSystem.SYSTEM.deleteRecursivelyQuietly(dir))
        assertFalse(root.resolve("h").exists())
        assertTrue(root.resolve("outside/keep.txt").exists())
    }
}
