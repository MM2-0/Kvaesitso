package de.mm20.launcher2.sdk.files

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import de.mm20.launcher2.plugin.contracts.FilePluginContract
import de.mm20.launcher2.sdk.base.SearchPluginProvider

abstract class FileProvider : SearchPluginProvider<File>() {
    abstract override suspend fun search(query: String): List<File>

    final override fun getPluginType(): de.mm20.launcher2.plugin.PluginType {
        return de.mm20.launcher2.plugin.PluginType.FileSearch
    }

    override fun createCursor(capacity: Int): MatrixCursor {
        return MatrixCursor(
            arrayOf(
                FilePluginContract.FileColumns.Id,
                FilePluginContract.FileColumns.DisplayName,
                FilePluginContract.FileColumns.MimeType,
                FilePluginContract.FileColumns.Size,
                FilePluginContract.FileColumns.Path,
                FilePluginContract.FileColumns.ContentUri,
                FilePluginContract.FileColumns.ThumbnailUri,
                FilePluginContract.FileColumns.IsDirectory,
                FilePluginContract.FileColumns.StorageStrategy
            ),
            capacity,
        )
    }

    override fun writeToCursor(cursor: MatrixCursor, item: File) {
        cursor.addRow(
            arrayOf(
                item.id,
                item.displayName,
                item.mimeType,
                item.size,
                item.path,
                item.uri.toString(),
                item.thumbnailUri?.toString(),
                if (item.isDirectory) 1 else 0,
                item.storageStrategy.name,
            )
        )
    }
}