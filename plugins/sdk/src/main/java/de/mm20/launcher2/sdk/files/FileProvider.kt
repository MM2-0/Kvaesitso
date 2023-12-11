package de.mm20.launcher2.sdk.files

import android.database.MatrixCursor
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.SearchPluginConfig
import de.mm20.launcher2.plugin.contracts.FilePluginContract
import de.mm20.launcher2.sdk.base.SearchPluginProvider

abstract class FileProvider(
    config: SearchPluginConfig = SearchPluginConfig(),
) : SearchPluginProvider<File>(config) {
    abstract override suspend fun search(query: String): List<File>

    final override fun getPluginType(): PluginType {
        return PluginType.FileSearch
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
                FilePluginContract.FileColumns.Owner,
                FilePluginContract.FileColumns.MetaTitle,
                FilePluginContract.FileColumns.MetaArtist,
                FilePluginContract.FileColumns.MetaAlbum,
                FilePluginContract.FileColumns.MetaDuration,
                FilePluginContract.FileColumns.MetaYear,
                FilePluginContract.FileColumns.MetaWidth,
                FilePluginContract.FileColumns.MetaHeight,
                FilePluginContract.FileColumns.MetaLocation,
                FilePluginContract.FileColumns.MetaAppName,
                FilePluginContract.FileColumns.MetaAppPackageName,
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
                item.owner,
                item.metadata.title,
                item.metadata.artist,
                item.metadata.album,
                item.metadata.duration,
                item.metadata.year,
                item.metadata.dimensions?.width,
                item.metadata.dimensions?.height,
                item.metadata.location,
                item.metadata.appName,
                item.metadata.appPackageName,
            )
        )
    }
}