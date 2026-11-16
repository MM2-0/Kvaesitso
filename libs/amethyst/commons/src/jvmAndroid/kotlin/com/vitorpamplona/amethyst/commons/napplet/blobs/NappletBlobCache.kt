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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * A content-addressed, on-disk cache for verified Blossom blobs, shared across the app's processes.
 *
 * Blobs are keyed by their sha256 and written atomically (temp file + rename), so the store needs no
 * journal and is safe for the **main** process (the prefetcher) and the **`:napplet`** process (the
 * content server) to read/write concurrently — unlike OkHttp's `DiskLruCache`, which is single-process
 * only. Both processes derive the same directory from the app's shared `cacheDir`.
 *
 * Every [put] re-hashes the bytes and only stores them if they match the key, so the store can only
 * ever hold correct content; a caller may still re-verify on read (the resolver does).
 *
 * Files are kept per site and per account ([forSite]): one shared cache would tell a site which files
 * this device already holds (no request reaches its server, the page loads instantly), so a site
 * could learn what else was opened, or link two accounts on one device. [trimToSize] covers them all.
 */
class NappletBlobCache private constructor(
    private val dir: File,
    // The cache's top folder, which trimming walks whichever site's cache it is called on.
    private val root: File,
) {
    constructor(dir: File) : this(dir, dir)

    /** The files [partition] (see [partitionOf]) may see: its own, never another site's or account's. */
    fun forSite(partition: String): NappletBlobCache = NappletBlobCache(File(root, sha256(partition.encodeToByteArray()).toHexKey().take(PARTITION_NAME_LENGTH)), root)

    fun has(sha256: String): Boolean = fileFor(sha256)?.isFile == true

    fun get(sha256: String): ByteArray? = file(sha256)?.let { runCatching { it.readBytes() }.getOrNull() }

    /**
     * The cached blob's file, to stream instead of loading, or null when it isn't cached. A hit
     * marks the blob as used, so [trimToSize] evicts what was used longest ago, not what was
     * downloaded first: a napplet opened every day stays cached.
     */
    fun file(sha256: String): File? {
        val file = fileFor(sha256)?.takeIf { it.isFile } ?: return null
        val now = System.currentTimeMillis()
        // At most once an hour per blob: a page's requests shouldn't each cost a metadata write.
        if (now - file.lastModified() > TOUCH_INTERVAL_MS && !file.setLastModified(now)) {
            Log.d("NappletBlobCache") { "Could not mark $sha256 as used" }
        }
        return file
    }

    /** Stores [bytes] under [sha256] iff they actually hash to it. No-op if already present or mismatched. */
    fun put(
        sha256: String,
        bytes: ByteArray,
    ) {
        if (sha256(bytes).toHexKey() != sha256.lowercase()) return // verify-on-write: never cache wrong content
        putVerified(sha256, bytes)
    }

    /**
     * Stores [bytes] under [sha256], which the caller has just checked they hash to (quartz's
     * resolver does), so a multi-megabyte blob isn't hashed twice.
     */
    fun putVerified(
        sha256: String,
        bytes: ByteArray,
    ) {
        val target = fileFor(sha256) ?: return
        if (target.isFile) return
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w("NappletBlobCache") { "Cannot create $dir" }
            return
        }
        val tmp = File(dir, "$sha256$TEMP_MARKER${System.nanoTime()}")
        try {
            // Synced before the rename: reads never re-hash, so a file under a hash's name must be
            // whole even after a power loss.
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) tmp.deleteOrWarn("NappletBlobCache", "leftover temp file")
        } catch (e: IOException) {
            // A full disk, most likely: the partial file goes, the blob is simply not cached.
            Log.w("NappletBlobCache", "Could not cache $sha256", e)
            tmp.deleteOrWarn("NappletBlobCache", "partial temp file")
        }
    }

    /**
     * Best-effort eviction across every site's files: if they exceed [maxBytes], delete the least
     * recently used until under it.
     */
    fun trimToSize(maxBytes: Long) {
        runCatching {
            val files =
                root
                    .walkTopDown()
                    .maxDepth(2)
                    .filter { it.isFile && !isStaleTemp(it) && !it.name.contains(TEMP_MARKER) }
                    .map { Triple(it, it.length(), it.lastModified()) }
                    .toList()
            var total = files.sumOf { it.second }
            if (total <= maxBytes) return
            files.sortedBy { it.third }.forEach { (f, length, _) ->
                if (total <= maxBytes) return
                if (f.deleteOrWarn("NappletBlobCache", "blob")) {
                    total -= length
                }
            }
        }
    }

    /**
     * A temp file a crashed or killed write left behind: one being written now is younger than this,
     * so it stays. Deleted here, as trimming walks the directory anyway.
     */
    private fun isStaleTemp(file: File): Boolean {
        if (!file.name.contains(TEMP_MARKER) || System.currentTimeMillis() - file.lastModified() < STALE_TEMP_MS) return false
        file.deleteOrWarn("NappletBlobCache", "stale temp file")
        return true
    }

    /**
     * The blob's file, or null when [sha256] is not one. The name comes from a site's manifest, so it is
     * checked here too: a `../` in it would otherwise read and write outside the cache directory.
     */
    private fun fileFor(sha256: String): File? = sha256.lowercase().takeIf { it.length == 64 && Hex.isHex64(it) }?.let { File(dir, it) }

    companion object {
        const val DEFAULT_MAX_BYTES = 256L * 1024 * 1024
        private const val TOUCH_INTERVAL_MS = 60L * 60 * 1000
        private const val STALE_TEMP_MS = 60L * 60 * 1000
        private const val PARTITION_NAME_LENGTH = 32

        /**
         * The cache partition of a site ([author], [identifier], as a launch's identity names it) seen
         * by [account] (any stable per-account name). A card's prefetch and the launch compute the same.
         */
        fun partitionOf(
            account: String,
            author: String,
            identifier: String,
        ): String = "$account|$author:$identifier"

        private const val TEMP_MARKER = ".tmp."

        /** The shared cache directory, identical across processes since they share the app's cacheDir. */
        fun dirFor(cacheDir: File) = File(cacheDir, "napplet-blobs")
    }
}
