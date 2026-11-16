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
package com.vitorpamplona.amethyst.commons.napplet.webxdc

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Getting a `.xdc` onto disk and open: the archive is streamed to a file and hashed on the way in,
 * never held in memory (a Half-Life port is 178 MB), then unpacked into a directory under hard
 * limits, since the zip is whatever the post's author made it.
 */
object WebxdcArchive {
    /** The largest archive downloaded; the largest one seen published is a 178 MB Half-Life. */
    const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024

    /** Every byte inflated out of one archive, kept or not: a small zip of zeros can't fill the disk. */
    const val MAX_UNPACKED_BYTES = 1024L * 1024 * 1024

    const val MAX_ENTRIES = 20_000

    private const val TAG = "WebxdcArchive"

    /**
     * Downloads the first of [urls] that answers with an archive into [target], at most
     * [MAX_ARCHIVE_BYTES]. With an [expectedHash], anything else is refused; without one, the
     * archive is taken as served. Gives up as soon as [cancelled] says so. [onProgress] hears the bytes
     * read so far and the size the server gave, if any, from the start of each server tried. Returns
     * the archive's sha256, or null when nothing usable came.
     */
    fun download(
        client: OkHttpClient,
        urls: List<String>,
        expectedHash: HexKey?,
        target: File,
        cancelled: () -> Boolean = { false },
        onProgress: (bytes: Long, total: Long?) -> Unit = { _, _ -> },
    ): HexKey? {
        for (url in urls.distinct()) {
            if (cancelled()) break
            val hash = downloadOne(client, url, target, cancelled, onProgress) ?: continue
            if (expectedHash == null || hash == expectedHash.lowercase()) return hash
            Log.w(TAG) { "$url is not the archive the post names ($hash)" }
        }
        if (target.exists() && !target.delete()) Log.w(TAG) { "Could not delete $target" }
        return null
    }

    private fun downloadOne(
        client: OkHttpClient,
        url: String,
        target: File,
        cancelled: () -> Boolean,
        onProgress: (bytes: Long, total: Long?) -> Unit,
    ): HexKey? {
        val call: Call =
            client.newCall(
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build(),
            )
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful || response.body.contentLength() > MAX_ARCHIVE_BYTES) return null
                val size = response.body.contentLength().takeIf { it > 0 }
                onProgress(0, size)
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            // A server that sends no length is held to the cap as it streams.
                            if (total > MAX_ARCHIVE_BYTES || cancelled()) return null
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress(total, size)
                        }
                    }
                }
                digest.digest().toHexKey()
            }
        } catch (e: Exception) {
            if (!call.isCanceled()) Log.w(TAG, "Downloading $url failed", e)
            null
        }
    }

    /**
     * Unpacks [zip] into [dir], which must be empty. Entries outside the archive's own tree (`..`,
     * absolute paths) and macOS metadata are skipped; past [MAX_ENTRIES] entries or
     * [MAX_UNPACKED_BYTES] inflated, it gives up. Returns whether the archive unpacked whole.
     */
    fun unpack(
        zip: File,
        dir: File,
    ): Boolean =
        try {
            zip.inputStream().use { unpack(it, dir) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not unpack $zip", e)
            false
        }

    private fun unpack(
        input: InputStream,
        dir: File,
    ) {
        val root = dir.canonicalFile
        var inflated = 0L
        var entries = 0
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(input).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw ZipException("More than $MAX_ENTRIES entries")
                val path = if (entry.isDirectory) null else entryPath(entry.name)
                val file = path?.let { File(root, it).canonicalFile }?.takeIf { it.path.startsWith(root.path + File.separator) }
                if (file == null) {
                    // Read anyway: a skipped entry is inflated just the same, and counts.
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        inflated += read
                        if (inflated > MAX_UNPACKED_BYTES) throw ZipException("Unpacks to more than $MAX_UNPACKED_BYTES bytes")
                    }
                    continue
                }
                val parent = file.parentFile
                if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw ZipException("Could not create $parent")
                file.outputStream().use { output ->
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        inflated += read
                        if (inflated > MAX_UNPACKED_BYTES) throw ZipException("Unpacks to more than $MAX_UNPACKED_BYTES bytes")
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        if (entries == 0) throw ZipException("Not a zip archive")
    }

    /**
     * The relative path a zip entry may be written to, or null for one that must not be: empty,
     * absolute, climbing out with `..`, or macOS metadata.
     */
    fun entryPath(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || name.startsWith("/") || parts.any { it == ".." }) return null
        if (parts.first() == "__MACOSX" || parts.last() == ".DS_Store") return null
        return parts.joinToString("/")
    }
}
