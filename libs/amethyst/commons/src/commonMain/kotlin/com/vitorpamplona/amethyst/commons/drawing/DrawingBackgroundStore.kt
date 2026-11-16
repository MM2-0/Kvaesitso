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

import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * The pictures a user has drawn on, kept on this device so they can draw on
 * them again: one PNG per picture, already cropped and scaled, in [directory].
 *
 * Callers give each account its own directory, so one account's pictures never
 * show up in another's editor. A file is named `<saved at, ms>_<content hash>.png`:
 * the time orders the list without trusting file timestamps, and the hash means
 * saving the same picture twice keeps one copy and only moves it to the front.
 * At most [maxKept] are kept; saving past that drops the oldest.
 *
 * Nothing here leaves the device: a saved picture is only uploaded if it ends up
 * in a posted drawing.
 */
class DrawingBackgroundStore(
    private val directory: Path,
    private val fileSystem: FileSystem = platformFileSystem,
    private val maxKept: Int = MAX_KEPT,
    private val nowMillis: () -> Long = { TimeUtils.nowMillis() },
) {
    /** Saved pictures, newest first. */
    suspend fun list(): List<Path> =
        withContext(Dispatchers.IO) {
            try {
                if (!fileSystem.exists(directory)) return@withContext emptyList()
                fileSystem
                    .list(directory)
                    .filter { it.name.endsWith(EXTENSION) && savedAt(it) != null }
                    .sortedByDescending { savedAt(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not list saved drawing backgrounds", e)
                emptyList()
            }
        }

    /** Keeps [png] and returns where; the newest picture again if it was already saved. */
    suspend fun save(png: ByteArray): Path =
        withContext(Dispatchers.IO) {
            fileSystem.createDirectories(directory)
            val hash = sha256(png).toHexKey().take(NAME_LENGTH)
            val path = directory / "${nowMillis()}_$hash$EXTENSION"
            // The same picture saved before is dropped, so the new name moves it to the front.
            list().filter { it.name.endsWith("_$hash$EXTENSION") }.forEach { fileSystem.delete(it, mustExist = false) }
            fileSystem.write(path) { write(png) }
            list().drop(maxKept).forEach { fileSystem.delete(it, mustExist = false) }
            path
        }

    suspend fun read(path: Path): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                fileSystem.read(path) { readByteArray() }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read saved drawing background $path", e)
                null
            }
        }

    suspend fun delete(path: Path) {
        withContext(Dispatchers.IO) {
            try {
                fileSystem.delete(path, mustExist = false)
            } catch (e: Exception) {
                // The picture stays in the list; failing to remove it must not take the editor down.
                Log.w(TAG, "Could not delete saved drawing background", e)
            }
        }
    }

    private fun savedAt(path: Path): Long? = path.name.substringBefore('_', "").toLongOrNull()

    companion object {
        const val MAX_KEPT = 12
        private const val EXTENSION = ".png"
        private const val NAME_LENGTH = 32
        private const val TAG = "DrawingBackgroundStore"
    }
}
