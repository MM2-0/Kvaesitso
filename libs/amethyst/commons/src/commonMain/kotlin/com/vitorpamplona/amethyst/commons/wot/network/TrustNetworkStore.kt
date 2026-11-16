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
package com.vitorpamplona.amethyst.commons.wot.network

import com.vitorpamplona.amethyst.commons.util.moveOrCopy
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkCodec
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIds
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIndex
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkPartial
import com.vitorpamplona.quartz.utils.Log
import okio.FileSystem
import okio.Path
import kotlin.random.Random

/**
 * The two files of one account's trust network, in [directory] (the account's own directory, so
 * deleting the account deletes them):
 *
 *  - [INDEX_FILE]: the index, read at startup.
 *  - [IDS_FILE]: event ids and tombstones, read only by syncs.
 *  - [PARTIAL_INDEX_FILE] and [PARTIAL_IDS_FILE]: what a cold download that has not finished
 *    collected so far, so it resumes. Never read as the network.
 *
 * Files are written to a temp file and moved into place, so a crash leaves the old file or the
 * new one, never half. A file that cannot be read (wrong version, truncated) reads as missing.
 * Blocking IO: call from `Dispatchers.IO`.
 */
class TrustNetworkStore(
    private val directory: Path,
    private val fileSystem: FileSystem = platformFileSystem,
) {
    private var legacyChecked = false

    fun readIndex(): TrustNetwork? {
        deleteLegacyFiles()
        return read(INDEX_FILE) { bytes -> TrustNetworkCodec.decodeIndex(bytes)?.let { (header, index) -> TrustNetwork(header, index) } }
    }

    /** The ids saved with the index whose header is [header], or null when they are not (any more) on disk. */
    fun readIds(header: TrustNetworkHeader): TrustNetworkIds? = read(IDS_FILE, TrustNetworkCodec::decodeIds)?.takeIf { it.generation == header.generation }

    /**
     * Saves [index] and its [ids], paired by a new random generation, and returns [header] as
     * written (use it from now on: [readIds] matches on its generation).
     */
    fun write(
        header: TrustNetworkHeader,
        index: TrustNetworkIndex,
        ids: TrustNetworkIds,
    ): TrustNetworkHeader = writePair(INDEX_FILE, IDS_FILE, header, index, ids)

    /** What [provider]'s unfinished cold download collected, or null when there is none for it. */
    fun readPartial(provider: HexKey): TrustNetworkPartial? {
        val (header, index) = read(PARTIAL_INDEX_FILE, TrustNetworkCodec::decodeIndex) ?: return null
        if (header.provider != provider) return null
        val ids = read(PARTIAL_IDS_FILE, TrustNetworkCodec::decodeIds)?.takeIf { it.generation == header.generation && it.size == index.size } ?: return null
        return TrustNetworkPartial(header, index, ids)
    }

    /** Saves a checkpoint of a cold download (replacing the previous one). */
    fun writePartial(partial: TrustNetworkPartial) {
        writePair(PARTIAL_INDEX_FILE, PARTIAL_IDS_FILE, partial.header, partial.index, partial.ids)
    }

    fun deletePartial() = deleteFiles(PARTIAL_INDEX_FILE, PARTIAL_IDS_FILE)

    fun delete() = deleteFiles(INDEX_FILE, IDS_FILE, PARTIAL_INDEX_FILE, PARTIAL_IDS_FILE)

    private fun writePair(
        indexFile: String,
        idsFile: String,
        header: TrustNetworkHeader,
        index: TrustNetworkIndex,
        ids: TrustNetworkIds,
    ): TrustNetworkHeader {
        val written = header.copy(generation = Random.nextLong())
        fileSystem.createDirectories(directory)
        // A crash between the two leaves files of different generations: the next sync then
        // downloads again instead of pairing an index with someone else's ids.
        atomicWrite(directory / idsFile, TrustNetworkCodec.encodeIds(ids.withGeneration(written.generation)))
        atomicWrite(directory / indexFile, TrustNetworkCodec.encodeIndex(written, index))
        return written
    }

    private fun deleteFiles(vararg names: String) {
        try {
            names.forEach { fileSystem.delete(directory / it, mustExist = false) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the trust network files", e)
        }
    }

    private fun <T> read(
        name: String,
        decode: (ByteArray) -> T?,
    ): T? {
        val file = directory / name
        return try {
            if (!fileSystem.exists(file)) return null
            decode(fileSystem.read(file) { readByteArray() })
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $name", e)
            null
        }
    }

    private fun atomicWrite(
        file: Path,
        bytes: ByteArray,
    ) {
        val temp = file.parent!! / "${file.name}.tmp"
        fileSystem.write(temp) { write(bytes) }
        fileSystem.moveOrCopy(temp, file)
    }

    /** Files of format version 1, which no build reads any more. */
    private fun deleteLegacyFiles() {
        if (legacyChecked) return
        legacyChecked = true
        LEGACY_FILES.forEach {
            try {
                fileSystem.delete(directory / it, mustExist = false)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete $it", e)
            }
        }
    }

    companion object {
        private const val TAG = "TrustNetworkStore"
        const val INDEX_FILE = "network-v2.bin"
        const val IDS_FILE = "network-ids-v2.bin"
        const val PARTIAL_INDEX_FILE = "partial-network-v2.bin"
        const val PARTIAL_IDS_FILE = "partial-network-ids-v2.bin"
        private val LEGACY_FILES = listOf("network-v1.bin", "network-ids-v1.bin")
    }
}
