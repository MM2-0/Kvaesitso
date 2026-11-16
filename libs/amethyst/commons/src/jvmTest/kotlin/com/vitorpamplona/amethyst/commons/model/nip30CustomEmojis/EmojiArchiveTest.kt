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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmojiArchiveTest {
    private fun zipOf(vararg entries: Pair<String, ByteArray?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                bytes?.let { zip.write(it) }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun extractsOnlyImagesInOrder() {
        val dir = Files.createTempDirectory("emoji-archive-test").toFile()
        try {
            val zip =
                zipOf(
                    "pack/" to null,
                    "pack/b.png" to byteArrayOf(1, 2),
                    "pack/readme.txt" to byteArrayOf(3),
                    "__MACOSX/pack/._b.png" to byteArrayOf(4),
                    "pack/.hidden.png" to byteArrayOf(5),
                    "a.gif" to byteArrayOf(6, 7, 8),
                )
            val result = extractEmojiArchive(ByteArrayInputStream(zip), dir)
            assertEquals(listOf("b.png", "a.gif"), result.files.map { it.fileName })
            assertEquals(
                listOf(6, 7, 8),
                result.files[1]
                    .path
                    .toFile()
                    .readBytes()
                    .map { it.toInt() },
            )
            assertTrue(result.tooLarge.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun skipsOversizedEntries() {
        val dir = Files.createTempDirectory("emoji-archive-test").toFile()
        try {
            val big = ByteArray((EmojiArchiveLimits.MAX_ENTRY_BYTES + 1).toInt())
            val result = extractEmojiArchive(ByteArrayInputStream(zipOf("big.png" to big, "ok.png" to byteArrayOf(1))), dir)
            assertEquals(listOf("ok.png"), result.files.map { it.fileName })
            assertEquals(listOf("big.png"), result.tooLarge)
            assertEquals(1, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptArchiveFailsAndCleansUp() {
        val dir = Files.createTempDirectory("emoji-archive-test").toFile()
        try {
            val zip = zipOf("a.png" to byteArrayOf(1, 2, 3), "b.png" to Random(1).nextBytes(4096))
            // Cut inside the second entry's (incompressible) data.
            val truncated = zip.copyOf(zip.size / 2)
            assertFailsWith<IllegalArgumentException> { extractEmojiArchive(ByteArrayInputStream(truncated), dir) }
            assertEquals(0, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Streams [size] zero bytes into one entry without holding them in memory. */
    private fun ZipOutputStream.zeros(
        name: String,
        size: Long,
    ) {
        putNextEntry(ZipEntry(name))
        val chunk = ByteArray(1 shl 20)
        var left = size
        while (left > 0) {
            val n = minOf(left, chunk.size.toLong()).toInt()
            write(chunk, 0, n)
            left -= n
        }
        closeEntry()
    }

    private fun bomb(build: ZipOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.build() }
        return out.toByteArray()
    }

    @Test
    fun aHugeNonImageEntryIsRefusedNotSilentlyInflated() {
        val dir = Files.createTempDirectory("emoji-archive-test").toFile()
        try {
            val zip = bomb { zeros("padding.bin", EmojiArchiveLimits.MAX_INFLATED_BYTES + 1) }
            assertFailsWith<IllegalArgumentException> { extractEmojiArchive(ByteArrayInputStream(zip), dir) }
            assertEquals(0, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun oversizedImagesCountTowardTheInflateBudget() {
        val dir = Files.createTempDirectory("emoji-archive-test").toFile()
        try {
            val each = EmojiArchiveLimits.MAX_ENTRY_BYTES + 1
            val count = (EmojiArchiveLimits.MAX_INFLATED_BYTES / each + 2).toInt()
            val zip = bomb { repeat(count) { zeros("big$it.png", each) } }
            assertFailsWith<IllegalArgumentException> { extractEmojiArchive(ByteArrayInputStream(zip), dir) }
            assertEquals(0, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun stopsAtTheImageLimitAndSaysSo() {
        val dir = Files.createTempDirectory("emoji-archive-test").toFile()
        try {
            val zip =
                bomb {
                    repeat(EmojiArchiveLimits.MAX_IMAGES + 3) {
                        putNextEntry(ZipEntry("e$it.png"))
                        write(1)
                        closeEntry()
                    }
                }
            val result = extractEmojiArchive(ByteArrayInputStream(zip), dir)
            assertEquals(EmojiArchiveLimits.MAX_IMAGES, result.files.size)
            assertTrue(result.truncated)
        } finally {
            dir.deleteRecursively()
        }
    }
}
