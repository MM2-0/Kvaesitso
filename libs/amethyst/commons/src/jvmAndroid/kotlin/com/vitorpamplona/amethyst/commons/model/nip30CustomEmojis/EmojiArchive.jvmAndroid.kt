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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

actual suspend fun extractEmojiArchive(zip: ByteArray): ExtractedEmojiArchive? = withContext(Dispatchers.IO) { extractEmojiArchive(ByteArrayInputStream(zip)) }

/**
 * [extractEmojiArchive] from a stream, into [tempDir] (the JVM's temp dir by default, which is
 * the app's cache dir on Android).
 *
 * Every entry is read here, images or not, so every inflated byte counts against
 * [EmojiArchiveLimits.MAX_INFLATED_BYTES]: left to itself, ZipInputStream inflates an entry it
 * skips, and a small archive of zeros would cost unbounded time.
 */
fun extractEmojiArchive(
    zip: InputStream,
    tempDir: File? = null,
): ExtractedEmojiArchive {
    val written = mutableListOf<ExtractedEmojiFile>()
    val tooLarge = mutableListOf<String>()
    var truncated = false
    // The file being written, which a failure mid-entry would otherwise leave behind.
    var current: File? = null
    try {
        val budget = InflateBudget()
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(zip).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                val name = EmojiImport.baseName(entry.name)
                val isImage = !entry.isDirectory && !entry.name.startsWith("__MACOSX/") && !name.startsWith(".") && EmojiImport.isImageFileName(name)
                if (!isImage) {
                    drain(stream, buffer, budget)
                    continue
                }
                if (written.size >= EmojiArchiveLimits.MAX_IMAGES || total >= EmojiArchiveLimits.MAX_TOTAL_BYTES) {
                    truncated = true
                    break
                }

                val file = File.createTempFile("amethyst_emoji_", "." + EmojiImport.extensionOf(name), tempDir)
                current = file
                // Counted as read rather than trusting the entry's declared size, which a crafted archive can fake.
                val size = copyAtMost(stream, file, EmojiArchiveLimits.MAX_ENTRY_BYTES, buffer, budget)
                if (size < 0) {
                    file.delete()
                    current = null
                    tooLarge.add(name)
                    drain(stream, buffer, budget)
                    continue
                }
                total += size
                written.add(ExtractedEmojiFile(name, file.toOkioPath()))
                current = null
            }
        }
        return ExtractedEmojiArchive(written, tooLarge, truncated)
    } catch (e: Throwable) {
        current?.delete()
        written.forEach { it.path.toFile().delete() }
        if (e is CancellationException) throw e
        if (e is ZipException || e is EOFException) throw IllegalArgumentException("Not a valid zip archive: ${e.message}", e)
        throw e
    }
}

/** Every byte inflated out of one archive, written or thrown away. */
private class InflateBudget {
    private var used = 0L

    fun spend(bytes: Int) {
        used += bytes
        require(used <= EmojiArchiveLimits.MAX_INFLATED_BYTES) {
            "The archive unpacks to more than ${EmojiArchiveLimits.MAX_INFLATED_BYTES / (1024 * 1024)} MB"
        }
    }
}

/** Reads the rest of the current entry, counting it. */
private fun drain(
    stream: InputStream,
    buffer: ByteArray,
    budget: InflateBudget,
) {
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) return
        budget.spend(read)
    }
}

/** Copies the current entry into [file]; -1 (and a partial file) as soon as it passes [limit] bytes. */
private fun copyAtMost(
    stream: InputStream,
    file: File,
    limit: Long,
    buffer: ByteArray,
    budget: InflateBudget,
): Long {
    var copied = 0L
    file.outputStream().use { out ->
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            budget.spend(read)
            copied += read
            if (copied > limit) return -1
            out.write(buffer, 0, read)
        }
    }
    return copied
}
