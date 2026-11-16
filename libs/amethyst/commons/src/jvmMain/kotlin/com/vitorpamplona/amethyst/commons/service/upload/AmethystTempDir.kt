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
package com.vitorpamplona.amethyst.commons.service.upload

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempFile
import kotlin.io.path.exists

/**
 * Per-user temporary directory at `~/.amethyst/tmp/`, mode 0700 on
 * POSIX systems. Used by the image compression pipeline so:
 *
 * - tmpfs-mounted `/tmp` on Linux doesn't OOM under a 50 MP HEIC
 *   decode (a 600 MB intermediate easily exceeds a small VM's
 *   tmpfs allotment),
 * - shared multi-user systems can't race on a predictably-named temp
 *   file in world-readable `/tmp`,
 * - the boot-time sweep has a strict ownership claim — anything in
 *   this directory belongs to us.
 *
 * The directory may be overridden via `-Damethyst.tmp.dir=PATH`.
 */
object AmethystTempDir {
    private const val PROP_OVERRIDE = "amethyst.tmp.dir"
    private const val ORPHAN_MAX_AGE_HOURS = 24L

    private val root: File by lazy {
        resolveRoot().also {
            ensure(it)
            // First access in this JVM session — sweep amethyst_* files
            // older than ORPHAN_MAX_AGE_HOURS. Recovers temp space after
            // a JVM crash that skipped shutdown hooks. Idempotent.
            sweepOrphansAt(it)
        }
    }

    /**
     * Create a temp file under the per-user temp dir. Names follow
     * `amethyst_<prefix>_<random>.<suffix>` so the boot-time sweep can
     * recognize ownership.
     */
    fun createTempFile(
        prefix: String,
        suffix: String,
    ): File {
        require(prefix.startsWith("amethyst_")) {
            "Temp file prefix must start with 'amethyst_' for sweep ownership; got '$prefix'"
        }
        return createTempFile(root.toPath(), prefix, suffix).toFile()
    }

    /**
     * Path to the per-user temp dir. Every JVM on the account shares it, test forks included, so a
     * test must not list it: another fork's files would show up there.
     */
    fun rootDir(): File = root

    /**
     * Delete `amethyst_*` files older than [ORPHAN_MAX_AGE_HOURS] hours.
     * Called once at app startup to clean up after JVM crashes that
     * skipped shutdown hooks. Returns the number of files removed.
     */
    fun sweepOrphans(): Int = sweepOrphansAt(root)

    private fun sweepOrphansAt(dir: File): Int {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(ORPHAN_MAX_AGE_HOURS)
        var removed = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith("amethyst_") && f.lastModified() < cutoff) {
                if (f.delete()) removed++
            }
        }
        return removed
    }

    private fun resolveRoot(): File {
        System.getProperty(PROP_OVERRIDE)?.let { return File(it) }
        return File(System.getProperty("user.home"), ".amethyst/tmp")
    }

    private fun ensure(dir: File) {
        val path = dir.toPath()
        if (!path.exists()) Files.createDirectories(path)
        // POSIX: lock to owner-only. Best-effort on Windows (no equivalent).
        try {
            Files.setPosixFilePermissions(
                path,
                PosixFilePermissions.fromString("rwx------"),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows / non-POSIX FS — leave default ACLs.
        } catch (_: SecurityException) {
            // Sandbox or unusual perms — best effort only.
        }
    }
}
