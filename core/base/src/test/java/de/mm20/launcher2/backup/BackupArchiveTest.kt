package de.mm20.launcher2.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Unsafe backup accepted") } catch (_: IOException) { }
    }

    @Test fun flatBackupRoundTrips() {
        val directory = temporary.newFolder()
        BackupArchive.extract(ByteArrayInputStream(archive("meta" to "{}", "searchactions.0000" to "[]")), directory)
        assertEquals("[]", BackupArchive.child(directory, "searchactions.0000").readText())
    }

    @Test fun importedNamesCannotEscapeOrSelectDirectories() {
        val directory = temporary.newFolder()
        for (name in listOf("../outside", "/absolute", "nested/file", "..\\outside", "C:\\outside", ".", "..", "", "bad\nname")) {
            rejects { BackupArchive.child(directory, name) }
        }
        rejects { BackupArchive.extract(ByteArrayInputStream(archive("../outside" to "bad")), directory) }
        assertFalse(java.io.File(directory.parentFile, "outside").exists())
    }

    @Test fun sizeAndEntryLimitsUseActualExpandedBytes() {
        val directory = temporary.newFolder()
        val bytes = archive("one" to "12345", "two" to "67890")
        rejects { BackupArchive.extract(ByteArrayInputStream(bytes), directory, maxEntryBytes = 4) }
        rejects { BackupArchive.extract(ByteArrayInputStream(bytes), directory, maxTotalBytes = 9) }
        rejects { BackupArchive.extract(ByteArrayInputStream(bytes), directory, maxEntries = 1) }
    }

    @Test fun duplicateEntriesCannotOverwriteAnEarlierFile() {
        val directory = temporary.newFolder()
        val bytes = archive("one" to "first", "two" to "second")
        for (i in 0..bytes.size - 3) {
            if (bytes[i] == 't'.code.toByte() && bytes[i + 1] == 'w'.code.toByte() && bytes[i + 2] == 'o'.code.toByte()) {
                bytes[i] = 'o'.code.toByte(); bytes[i + 1] = 'n'.code.toByte(); bytes[i + 2] = 'e'.code.toByte()
            }
        }
        rejects { BackupArchive.extract(ByteArrayInputStream(bytes), directory) }
        assertEquals("first", BackupArchive.child(directory, "one").readText())
    }

    @Test fun metadataReadIsBounded() {
        assertArrayEquals("1234".toByteArray(), BackupArchive.readLimited(ByteArrayInputStream("1234".toByteArray()), 4))
        rejects { BackupArchive.readLimited(ByteArrayInputStream("12345".toByteArray()), 4) }
    }

    @Test fun metadataMayFollowFlatAssetsButRejectsUnsafeNamesAndOversizeContent() {
        assertArrayEquals("{}".toByteArray(), BackupArchive.readMetadata(ByteArrayInputStream(archive("asset.0000" to "icon", "meta" to "{}"))))
        assertNull(BackupArchive.readMetadata(ByteArrayInputStream(archive("asset.0000" to "icon"))))
        rejects { BackupArchive.readMetadata(ByteArrayInputStream(archive("../outside" to "bad", "meta" to "{}"))) }
        rejects { BackupArchive.readMetadata(ByteArrayInputStream(archive("meta" to "a".repeat(BackupArchive.MaxMetadataBytes + 1)))) }
    }

    @Test fun recordFilesHaveASmallerLimitThanBinaryAssets() {
        val directory = temporary.newFolder()
        val content = "a".repeat(4 * 1024 * 1024 + 1)
        rejects { BackupArchive.extract(ByteArrayInputStream(archive("searchactions.0000" to content)), directory) }
        BackupArchive.extract(ByteArrayInputStream(archive("asset.searchaction.0000" to content)), directory)
        assertEquals(content.length.toLong(), BackupArchive.child(directory, "asset.searchaction.0000").length())
    }

    @Test fun cancellationClosesInputAndPreventsWrites() {
        val directory = temporary.newFolder()
        var closed = false
        val input = object : ByteArrayInputStream(archive("one" to "content")) {
            override fun close() { closed = true; super.close() }
        }
        try {
            BackupArchive.extract(input, directory, checkCancelled = { throw kotlinx.coroutines.CancellationException() })
            fail("Cancellation was swallowed")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertTrue(closed)
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
