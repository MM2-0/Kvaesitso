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

import android.content.Context
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import com.vitorpamplona.amethyst.service.safeCacheDir
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class ImageCacheFactory {
    companion object {
        fun newDisk(
            app: Context,
            scope: CoroutineScope,
        ): DiskCache =
            DiskCache
                .Builder()
                .directory(app.safeCacheDir().resolve("image_cache").toOkioPath())
                // Defers Coil's inline eviction unlink() onto [scope] so a
                // saturated cache doesn't stall feed-scroll IO on the write path.
                .fileSystem(DeferredDeleteFileSystem(FileSystem.SYSTEM, scope))
                .maxSizePercent(0.2)
                .maximumMaxSizeBytes(1024 * 1024 * 1024) // 1GB
                .build()

        fun newMemory(app: Context): MemoryCache =
            MemoryCache
                .Builder()
                .maxSizePercent(app)
                .build()
    }
}
