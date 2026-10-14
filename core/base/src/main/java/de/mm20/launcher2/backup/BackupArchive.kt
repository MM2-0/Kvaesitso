package de.mm20.launcher2.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Launcher backups contain flat files. Imported names never address arbitrary paths. */
object BackupArchive {
    const val MaxMetadataBytes = 64 * 1024
    private const val MaxEntryBytes = 64L * 1024 * 1024
    private const val MaxTotalBytes = 512L * 1024 * 1024
    private const val MaxEntries = 10_000
    private const val MaxRecordBytes = 4L * 1024 * 1024

    private fun entryLimit(name: String, requested: Long): Long = minOf(requested,
        when {
            name == "meta" -> MaxMetadataBytes.toLong()
            name.startsWith("asset.") -> MaxEntryBytes
            else -> MaxRecordBytes
        })

    private fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." ||
            name.any { it == '/' || it == '\\' || it == ':' || it.isISOControl() }) {
            throw IOException("Invalid backup file name")
        }
    }

    fun child(directory: File, name: String): File {
        validateName(name)
        val child = File(directory, name).canonicalFile
        if (child.parentFile != directory.canonicalFile) throw IOException("Backup path escapes its directory")
        return child
    }

    fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            if (count > maxBytes) throw IOException("Backup metadata exceeds its size limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun readMetadata(input: InputStream, checkCancelled: () -> Unit = {}): ByteArray? {
        val seen = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                checkCancelled()
                val entry = zip.nextEntry ?: return null
                validateName(entry.name)
                if (entry.isDirectory || !seen.add(entry.name) || seen.size > MaxEntries) {
                    throw IOException("Invalid or excessive backup entries")
                }
                val metadata = if (entry.name == "meta") ByteArrayOutputStream() else null
                val limit = entryLimit(entry.name, MaxEntryBytes)
                var bytes = 0L
                while (true) {
                    checkCancelled()
                    val read = zip.read(buffer)
                    if (read < 0) break
                    bytes += read
                    total += read
                    if (bytes > limit || total > MaxTotalBytes) throw IOException("Backup exceeds its size limit")
                    metadata?.write(buffer, 0, read)
                }
                if (metadata != null) return metadata.toByteArray()
                zip.closeEntry()
            }
        }
    }

    fun extract(
        input: InputStream,
        directory: File,
        maxEntryBytes: Long = MaxEntryBytes,
        maxTotalBytes: Long = MaxTotalBytes,
        maxEntries: Int = MaxEntries,
        checkCancelled: () -> Unit = {},
    ) {
        val seen = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(8192)
            while (true) {
                checkCancelled()
                val entry = zip.nextEntry ?: break
                val file = child(directory, entry.name)
                if (entry.isDirectory || !seen.add(entry.name) || seen.size > maxEntries) {
                    throw IOException("Invalid or excessive backup entries")
                }
                var bytes = 0L
                val limit = entryLimit(entry.name, maxEntryBytes)
                file.outputStream().use { output ->
                    while (true) {
                        checkCancelled()
                        val read = zip.read(buffer)
                        if (read < 0) break
                        bytes += read
                        total += read
                        if (bytes > limit || total > maxTotalBytes) {
                            throw IOException("Backup exceeds its size limit")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
    }
}
