package de.mm20.launcher2.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.export
import de.mm20.launcher2.preferences.import
import de.mm20.launcher2.search.WebsearchRepository
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val dataStore: LauncherDataStore,
    private val favoritesRepository: FavoritesRepository,
    private val widgetRepository: WidgetRepository,
    private val websearchRepository: WebsearchRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    /**
     * Create a backup
     * @return Uri to the created backup archive
     */
    suspend fun backup(
        uri: Uri,
        include: Set<BackupComponent> = BackupComponent.values().toSet()
    ) {

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val meta = BackupMetadata(
            appVersionName = packageInfo.versionName,
            timestamp = System.currentTimeMillis(),
            deviceName = Build.MODEL,
            components = include,
            format = BackupFormat,
        )

        withContext(Dispatchers.IO) {
            val outputStream = context.contentResolver.openOutputStream(uri) ?: return@withContext null
            val backupDir = File(context.externalCacheDir, "backup")
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }
            backupDir.mkdirs()

            val metaFile = File(backupDir, "meta")
            meta.writeToFile(metaFile)

            if (include.contains(BackupComponent.Settings)) {
                dataStore.export(backupDir)
            }

            if (include.contains(BackupComponent.Favorites)) {
                favoritesRepository.export(backupDir)
            }

            if (include.contains(BackupComponent.Widgets)) {
                widgetRepository.export(backupDir)
            }

            if (include.contains(BackupComponent.Websearches)) {
                websearchRepository.export(backupDir)
            }

            createArchive(backupDir, outputStream)
            outputStream.close()

        }
    }

    suspend fun restore(
        uri: Uri,
        include: Set<BackupComponent> = BackupComponent.values().toSet()
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

                if (include.contains(BackupComponent.Settings)) {
                    dataStore.import(context, restoreDir)
                }

                if (include.contains(BackupComponent.Favorites)) {
                    favoritesRepository.import(restoreDir)
                }

                if (include.contains(BackupComponent.Widgets)) {
                    widgetRepository.import(restoreDir)
                }

                if (include.contains(BackupComponent.Websearches)) {
                    websearchRepository.import(restoreDir)
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
        private const val BackupFormatMajor = 1
        private const val BackupFormatMinor = 0
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