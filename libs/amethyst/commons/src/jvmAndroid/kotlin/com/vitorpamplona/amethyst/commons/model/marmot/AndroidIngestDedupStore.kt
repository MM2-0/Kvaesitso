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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.marmot.MarmotIngestDedupStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [MarmotIngestDedupStore] — one hex event id per
 * line under `<rootDir>/marmot_ingested.ids`.
 *
 * Deliberately NOT encrypted: the file holds public relay event ids and no key
 * material, and a marker lost to a decryption failure would silently cost a
 * re-decision rather than fail loudly.
 *
 * Capped and trimmed oldest-first. The worst case for a forgotten marker is
 * one wasted NIP-59 unwrap on the next sync, so bounding growth is worth more
 * than remembering every id forever.
 */
class AndroidIngestDedupStore(
    private val rootDir: File,
    private val maxEntries: Int = 20_000,
) : MarmotIngestDedupStore {
    private val mutex = Mutex()

    private fun file(): File = File(rootDir, "marmot_ingested.ids")

    override suspend fun mark(eventId: HexKey) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val target = file()
                try {
                    target.parentFile?.mkdirs()
                    target.appendText(eventId + "\n")
                    if (target.length() > maxEntries.toLong() * 65L) {
                        val kept = target.readLines().filter { it.isNotBlank() }.takeLast(maxEntries / 2)
                        target.writeText(kept.joinToString("\n") + "\n")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "could not record ingest marker: ${e.message}", e)
                }
                Unit
            }
        }

    override suspend fun loadAll(): Set<HexKey> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    file()
                        .takeIf { it.exists() }
                        ?.readLines()
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        .orEmpty()
                } catch (e: Exception) {
                    Log.w(TAG, "could not read ingest markers: ${e.message}", e)
                    emptySet()
                }
            }
        }

    companion object {
        private const val TAG = "AndroidIngestDedupStore"
    }
}
