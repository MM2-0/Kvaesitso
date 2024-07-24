package de.mm20.launcher2.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val components: List<Backupable>,
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    /**
     * Create a backup
     * @return Uri to the created backup archive
     */
    suspend fun backup(
        uri: Uri
    ) {

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val meta = BackupMetadata(
            appVersionName = packageInfo.versionName ?: "",
            timestamp = System.currentTimeMillis(),
            deviceName = Build.MODEL,
            format = BackupFormat,
        )

        withContext(Dispatchers.IO) {
            val outputStream = context.contentResolver.openOutputStream(uri) ?: return@withContext null
            val backupDir = File(context.cacheDir, "backup")
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }
            backupDir.mkdirs()

            val metaFile = File(backupDir, "meta")
            meta.writeToFile(metaFile)

            for (component in components) {
                component.backup(backupDir)
            }

            createArchive(backupDir, outputStream)
            outputStream.close()

        }
    }

    suspend fun restore(
        uri: Uri,
    ) {
        val job = scope.launch {
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext
                val restoreDir = File(context.cacheDir, "restore")
                if (restoreDir.exists()) {
                    restoreDir.deleteRecursively()
                }
                restoreDir.mkdirs()
                extractArchive(inputStream, restoreDir)
                inputStream.close()

                for (component in components) {
                    component.restore(restoreDir)
                }
            }
        }
        job.join()
    }

    suspend fun readBackupMeta(uri: Uri): BackupMetadata? {
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while(entry != null) {
                if (entry.name == "meta") {
                    val metadata = BackupMetadata.fromInputStream(zipStream)
                    zipStream.close()
                    return@withContext metadata
                }

                zipStream.closeEntry()

                entry = zipStream.nextEntry
            }
            return@withContext null
        }
    }

    private suspend fun createArchive(dir: File, outputStream: OutputStream) = withContext(Dispatchers.IO){
        val zipStream = ZipOutputStream(outputStream)

        val fileList = dir.listFiles()

        for (file in fileList) {
            zipStream.putNextEntry(ZipEntry(file.name))
            file.inputStream().use {
                it.copyTo(zipStream)
            }
            zipStream.closeEntry()
        }
        zipStream.close()
    }

    private suspend fun extractArchive(inputStream: InputStream, outDir: File) = withContext(Dispatchers.IO) {
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        while(entry != null) {
            val file = File(outDir, entry.name)
            file.outputStream().use {
                zipStream.copyTo(it)
            }
            zipStream.closeEntry()

            entry = zipStream.nextEntry
        }
    }

    fun checkCompatibility(meta: BackupMetadata): BackupCompatibility {
        val format = meta.format.split(".")
        val x = format.getOrNull(0)?.toIntOrNull() ?: return BackupCompatibility.Incompatible
        val y = format.getOrNull(1)?.toIntOrNull() ?: return BackupCompatibility.Incompatible
        if (x != BackupFormatMajor) return BackupCompatibility.Incompatible
        if (y != BackupFormatMinor) return BackupCompatibility.PartiallyCompatible
        return BackupCompatibility.Compatible
    }

    companion object {
        /**
         * Format changelog:
         * - 1.5: added `weight` to favorites
         * - 1.9: migrate from proto to json data store
         */

        private const val BackupFormatMajor = 1
        private const val BackupFormatMinor = 9
        internal const val BackupFormat = "$BackupFormatMajor.$BackupFormatMinor"
    }
}

enum class BackupCompatibility {
    /**
     * Fully compatible, can be fully restored
     */
    Compatible,

    /**
     * Incompatible, cannot be restored
     */
    Incompatible,

    /**
     * Compatible but has been created on a different version and parts of the backup use a different format
     * or were not supported / are not supported anymore so parts of the backup might not be restored.
     */
    PartiallyCompatible
}