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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.utils.Log
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Where a NIP-95 blob's bytes go so they can leave memory.
 *
 * The cache does not need a directory, it needs somewhere to put bytes and a way to ask whether
 * they are already there — which is all this is. A front end that has nowhere to spill to simply
 * supplies none, and the event keeps its content in memory.
 */
interface Nip95BlobStore {
    /** Whether a blob is already stored under [id]. */
    fun exists(id: String): Boolean

    /** Where the blob for [id] lives (or would), for a media player or image loader to read. */
    fun path(id: String): Path

    /** Stores [bytes] under [id]. Returns false when the write failed. */
    fun store(
        id: String,
        bytes: ByteArray,
    ): Boolean
}

/**
 * The ordinary [Nip95BlobStore]: one file per event id under [directory].
 *
 * okio rather than `java.io.File` because this sits behind `LocalCacheHost`, which the event
 * cache reads on every NIP-95 event, and the cache is shared code.
 */
class FileSystemNip95BlobStore(
    private val directory: Path,
    private val fileSystem: FileSystem = platformFileSystem,
) : Nip95BlobStore {
    /** Takes the platform's own path string, so callers need no okio of their own. */
    constructor(directoryPath: String) : this(directoryPath.toPath())

    override fun exists(id: String): Boolean = fileSystem.exists(directory / id)

    override fun path(id: String): Path = directory / id

    override fun store(
        id: String,
        bytes: ByteArray,
    ): Boolean =
        try {
            fileSystem.createDirectories(directory)
            fileSystem.write(directory / id) { write(bytes) }
            true
        } catch (e: IOException) {
            Log.e("Nip95BlobStore", "Could not store the NIP-95 blob $id", e)
            false
        }
}
