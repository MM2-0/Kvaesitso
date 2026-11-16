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

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileDeletionTest {
    private val createdFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        createdFiles.asReversed().forEach { it.delete() }
        createdFiles.clear()
    }

    private fun tempFile(): File =
        File.createTempFile("file-deletion-test", ".tmp").also {
            createdFiles.add(it)
        }

    @Test
    fun deletesExistingFileAndReturnsTrue() {
        val file = tempFile()
        assertTrue(file.exists())

        assertTrue(file.deleteOrWarn("FileDeletionTest", "temp file"))
        assertFalse(file.exists())
    }

    @Test
    fun alreadyGoneFileIsSuccess() {
        val file = tempFile()
        assertTrue(file.delete())

        // Simulates a concurrent eviction: the file vanished before our delete.
        assertTrue(file.deleteOrWarn("FileDeletionTest", "temp file"))
    }

    @Test
    fun undeletableFileReturnsFalseAndKeepsFile() {
        // A non-empty directory cannot be deleted, and still exists afterwards —
        // the genuine-failure branch.
        val dir = Files.createTempDirectory("file-deletion-test").toFile()
        val child = File(dir, "child.txt").apply { writeText("keeps dir non-empty") }
        createdFiles.add(dir)
        createdFiles.add(child)

        assertFalse(dir.deleteOrWarn("FileDeletionTest", "non-empty dir"))
        assertTrue(dir.exists())
    }
}
