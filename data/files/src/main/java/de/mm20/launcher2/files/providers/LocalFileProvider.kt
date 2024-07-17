package de.mm20.launcher2.files.providers

import android.content.Context
import android.provider.MediaStore
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalFileProvider(
    private val context: Context,
    private val permissionsManager: PermissionsManager
): FileProvider {
    override suspend fun search(query: String, allowNetwork: Boolean): List<File> = withContext(Dispatchers.IO) {
        if (!permissionsManager.checkPermissionOnce(PermissionGroup.ExternalStorage)) {
            return@withContext emptyList()
        }
        if (query.length < 2 || query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<LocalFile>()
        val uri = MediaStore.Files.getContentUri("external").buildUpon()
            .appendQueryParameter("limit", "10").build()
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.TITLE} LIKE ?"
        val selArgs = if (query.length > 3) arrayOf("%$query%") else arrayOf("$query%")
        val sort = "${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE ASC"


        val cursor = try {
            context.contentResolver.query(uri, projection, selection, selArgs, sort)
        } catch (e: IllegalArgumentException) {
            CrashReporter.logException(e)
            null
        } ?: return@withContext results
        while (cursor.moveToNext()) {
            if (results.size >= 10) {
                break
            }
            val path = cursor.getString(3)
            if (!java.io.File(path).exists()) continue
            val directory = java.io.File(path).isDirectory
            val mimeType = (cursor.getStringOrNull(4).takeIf { it != "application/octet-stream" }
                ?: if (directory) "resource/folder" else LocalFile.getMimetypeByFileExtension(
                    path.substringAfterLast(
                        '.'
                    )
                ))
            val file = LocalFile(
                path = path,
                mimeType = mimeType,
                size = cursor.getLong(2),
                isDirectory = directory,
                id = cursor.getLong(1),
                metaData = LocalFile.getMetaData(context, mimeType, path)
            )
            results.add(file)
        }
        cursor.close()
        return@withContext results
    }
}