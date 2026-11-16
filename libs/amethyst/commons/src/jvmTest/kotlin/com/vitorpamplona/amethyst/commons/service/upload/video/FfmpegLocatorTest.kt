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
package com.vitorpamplona.amethyst.commons.service.upload.video

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FfmpegLocatorTest {
    private lateinit var dir: File
    private lateinit var pkgDir: File
    private lateinit var resDir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("ffmpeg-locator").toFile()
        pkgDir = File(dir, "pkg").apply { mkdirs() }
        resDir = File(dir, "res").apply { mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun executable(file: File): File =
        file.apply {
            parentFile.mkdirs()
            writeText("")
            setExecutable(true)
        }

    /**
     * Every locator under test ignores the environment, so the suite passes under
     * AMETHYST_FFMPEG_DISABLED=1 too, and resolves Unix names whatever the host, so the same
     * fake binaries serve on a Windows runner.
     */
    private fun locator(
        runsOnPath: (String) -> Boolean,
        packageDirs: () -> List<File>,
        resourcesDir: () -> File?,
        disabled: Boolean = false,
        windows: Boolean = false,
    ) = FfmpegLocator(runsOnPath, packageDirs, resourcesDir, disabled, windows)

    @Test
    fun prefersPath() {
        assertEquals("ffmpeg", locator({ true }, { emptyList() }, { null }).ffmpeg)
    }

    @Test
    fun findsHomebrewStyleDirWhenPathIsBare() { // Review Focus 5
        val bin = executable(File(pkgDir, "ffmpeg"))
        assertEquals(bin.absolutePath, locator({ false }, { listOf(File(dir, "missing"), pkgDir) }, { null }).ffmpeg)
    }

    @Test
    fun fallsBackToBundledExecutable() {
        val bin = executable(File(resDir, "ffmpeg/ffmpeg"))
        assertEquals(bin.absolutePath, locator({ false }, { emptyList() }, { resDir }).ffmpeg)
    }

    @Test
    fun windowsLooksForTheExe() {
        val bin = executable(File(pkgDir, "ffmpeg.exe"))
        executable(File(pkgDir, "ffmpeg"))
        assertEquals(bin.absolutePath, locator({ false }, { listOf(pkgDir) }, { null }, windows = true).ffmpeg)
        assertEquals("ffmpeg.exe", locator({ it == "ffmpeg.exe" }, { emptyList() }, { null }, windows = true).ffmpeg)
    }

    @Test
    fun nonExecutableFilesAreIgnored() {
        // Windows has no execute bit: every existing file is executable there.
        if (FfmpegTestSupport.isWindows) return
        File(resDir, "ffmpeg/ffprobe").apply {
            parentFile.mkdirs()
            writeText("")
            setExecutable(false)
        }
        assertNull(locator({ false }, { emptyList() }, { resDir }).ffprobe)
    }

    @Test
    fun unavailableWhenNothingExists() {
        assertFalse(locator({ false }, { emptyList() }, { null }).available)
    }

    @Test
    fun disabledIgnoresEverything() {
        var probes = 0
        val l =
            locator({
                probes++
                true
            }, { emptyList() }, { null }, disabled = true)
        assertNull(l.ffmpeg)
        assertNull(l.ffprobe)
        assertEquals(0, probes)
    }

    @Test
    fun warmUpResolvesBoth() {
        var probes = 0
        locator({
            probes++
            true
        }, { emptyList() }, { null }).warmUp()
        assertEquals(2, probes)
    }
}
