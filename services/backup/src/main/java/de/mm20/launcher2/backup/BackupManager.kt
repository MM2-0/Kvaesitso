package de.mm20.launcher2.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val components: List<Backupable>,
) {
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
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Cannot create backup")
            outputStream.use {
                val backupDir = File(context.cacheDir, "backup")
                if (backupDir.exists()) backupDir.deleteRecursively()
                if (!backupDir.mkdirs()) throw IOException("Cannot prepare backup directory")

                val metaFile = File(backupDir, "meta")
                meta.writeToFile(metaFile)

                var failure: Exception? = null
                for (component in components) {
                    try {
                        component.backup(backupDir)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("BackupManager", "Failed to back up ${component::class.java.name}", e)
                        failure = e
                    }
                }
                failure?.let { throw IOException("Backup could not be fully created", it) }
                createArchive(backupDir, outputStream)
            }
        }
    }

    suspend fun restore(
        uri: Uri,
    ) {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open backup")
            val restoreDir = File(context.cacheDir, "restore")
            inputStream.use {
                if (restoreDir.exists()) restoreDir.deleteRecursively()
                if (!restoreDir.mkdirs()) throw IOException("Cannot prepare backup directory")
                extractArchive(it, restoreDir)
            }

            var failure: Exception? = null
            for (component in components) {
                // Preserve component isolation, but never report a partial failure as success.
                try {
                    component.restore(restoreDir)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("BackupManager", "Failed to restore ${component::class.java.name}", e)
                    failure = e
                }
            }
            failure?.let { throw IOException("Backup could not be fully restored", it) }
        }
    }

    suspend fun readBackupMeta(uri: Uri): BackupMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream =
                    context.contentResolver.openInputStream(uri) ?: return@withContext null
                val owner = currentCoroutineContext()
                val metadata = BackupArchive.readMetadata(inputStream, checkCancelled = { owner.ensureActive() })
                    ?: return@withContext null
                return@withContext BackupMetadata.fromInputStream(ByteArrayInputStream(metadata))
            } catch (e: IOException) {
                Log.e("BackupManager", "Failed to read backup metadata", e)
            } catch (e: SecurityException) {
                Log.e("BackupManager", "Failed to read backup metadata", e)
            }
            return@withContext null
        }
    }

    private suspend fun createArchive(dir: File, outputStream: OutputStream) = withContext(Dispatchers.IO){
        ZipOutputStream(outputStream).use { zipStream ->
            val fileList = dir.listFiles() ?: throw IOException("Cannot read backup directory")
            for (file in fileList) {
                zipStream.putNextEntry(ZipEntry(file.name))
                file.inputStream().use {
                    it.copyTo(zipStream)
                }
                zipStream.closeEntry()
            }
        }
    }

    private suspend fun extractArchive(inputStream: InputStream, outDir: File) = withContext(Dispatchers.IO) {
        val owner = currentCoroutineContext()
        BackupArchive.extract(inputStream, outDir, checkCancelled = { owner.ensureActive() })
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
