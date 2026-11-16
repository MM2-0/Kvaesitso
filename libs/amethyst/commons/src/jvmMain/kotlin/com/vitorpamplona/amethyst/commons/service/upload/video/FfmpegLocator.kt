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

import com.vitorpamplona.amethyst.commons.moderation.notifications.HostOs
import com.vitorpamplona.amethyst.commons.moderation.notifications.detectHostOs
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Finds the `ffmpeg` and `ffprobe` executables once per process.
 *
 * Resolution order for each name: the bare name on `$PATH` (probed with `-version`), the
 * package-manager directories a Finder- or Start-menu-launched app does not have on its PATH,
 * then `${compose.application.resources.dir}/ffmpeg/<name>` for a bundled binary. Never a
 * working-directory-relative path, which whoever launches the app controls.
 *
 * The environment variable `AMETHYST_FFMPEG_DISABLED` (any value) makes the locator report
 * nothing, which is how the app and the tests run as a machine without ffmpeg: a bare `PATH`
 * is not enough, because the package-manager directories are searched by design.
 */
class FfmpegLocator(
    private val runsOnPath: (binary: String) -> Boolean = ::probeOnPath,
    private val packageDirs: () -> List<File> = ::defaultPackageDirs,
    private val resourcesDir: () -> File? = { System.getProperty("compose.application.resources.dir")?.let(::File) },
    private val disabled: Boolean = System.getenv("AMETHYST_FFMPEG_DISABLED") != null,
    private val windows: Boolean = isWindows,
) {
    val ffmpeg: String? by lazy { resolve("ffmpeg") }
    val ffprobe: String? by lazy { resolve("ffprobe") }

    val available: Boolean get() = ffmpeg != null

    /** Resolves both names now, so reading them later never blocks on the `-version` probe. */
    fun warmUp() {
        ffmpeg
        ffprobe
    }

    private fun resolve(name: String): String? {
        if (disabled) {
            Log.i(TAG) { "$name disabled by AMETHYST_FFMPEG_DISABLED" }
            return null
        }
        val binary = if (windows) "$name.exe" else name
        if (runsOnPath(binary)) return binary
        val candidates = packageDirs().map { File(it, binary) } + listOfNotNull(resourcesDir()?.let { File(it, "ffmpeg/$binary") })
        val found = candidates.firstOrNull { it.isFile && it.canExecute() }
        Log.i(TAG) { "$name: ${found?.absolutePath ?: "not found"}" }
        return found?.absolutePath
    }

    companion object {
        private const val TAG = "FfmpegLocator"

        val default = FfmpegLocator()

        private val isWindows: Boolean get() = detectHostOs() == HostOs.WINDOWS

        /** True when `binary -version` runs and exits 0 within 2 s; the process is killed on overrun. */
        fun probeOnPath(binary: String): Boolean =
            runCatching {
                val probe =
                    ProcessBuilder(binary, "-version")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                val exited = probe.waitFor(2, TimeUnit.SECONDS)
                if (!exited) {
                    probe.destroyForcibly()
                    return@runCatching false
                }
                probe.exitValue() == 0
            }.getOrDefault(false)

        /** Where Homebrew, MacPorts, winget, scoop and Chocolatey put binaries. */
        fun defaultPackageDirs(): List<File> =
            if (isWindows) {
                listOfNotNull(
                    System.getenv("ProgramFiles")?.let { File(it, "ffmpeg\\bin") },
                    System.getenv("LOCALAPPDATA")?.let { File(it, "Microsoft\\WinGet\\Links") },
                    System.getenv("USERPROFILE")?.let { File(it, "scoop\\shims") },
                    System.getenv("ChocolateyInstall")?.let { File(it, "bin") },
                )
            } else {
                listOf(File("/opt/homebrew/bin"), File("/usr/local/bin"), File("/opt/local/bin"))
            }
    }
}
