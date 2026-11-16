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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FilePermissionsTest {
    private val createdFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        createdFiles.asReversed().forEach { it.delete() }
        createdFiles.clear()
    }

    private fun isPosix() = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    @Test
    fun restrictsFileToOwnerReadWrite() {
        if (!isPosix()) return // Windows: restrictToOwner is documented as a silent no-op

        val file = File.createTempFile("file-permissions-test", ".tmp")
        createdFiles.add(file)

        file.restrictToOwner("FilePermissionsTest")

        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(file.toPath()),
        )
    }

    @Test
    fun restrictsDirectoryToOwnerReadWriteExecute() {
        if (!isPosix()) return

        val dir = Files.createTempDirectory("file-permissions-test").toFile()
        createdFiles.add(dir)

        dir.restrictToOwner("FilePermissionsTest")

        assertEquals(
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
            Files.getPosixFilePermissions(dir.toPath()),
        )
    }

    @Test
    fun missingFileDoesNotThrow() {
        val ghost = File(System.getProperty("java.io.tmpdir"), "file-permissions-test-missing-${System.nanoTime()}")

        // Best-effort contract: failures are logged, never thrown.
        ghost.restrictToOwner("FilePermissionsTest")
    }
}
