package de.mm20.launcher2.sdk.files

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.FilePluginContract.FileColumns
import de.mm20.launcher2.plugin.data.buildCursor
import de.mm20.launcher2.plugin.data.get
import de.mm20.launcher2.sdk.base.StringPluginProvider

abstract class FileProvider(
    config: QueryPluginConfig,
) : StringPluginProvider<File>(config) {

    final override fun getPluginType(): PluginType {
        return PluginType.FileSearch
    }

    override fun List<File>.toCursor(): Cursor {
        return buildCursor(FileColumns, this) {
            put(FileColumns.Id, it.id)
            put(FileColumns.DisplayName, it.displayName)
            put(FileColumns.MimeType, it.mimeType)
            put(FileColumns.Size, it.size)
            put(FileColumns.Path, it.path)
            put(FileColumns.ContentUri, it.uri.toString())
            put(FileColumns.ThumbnailUri, it.thumbnailUri?.toString())
            put(FileColumns.IsDirectory, it.isDirectory)
            put(FileColumns.Owner, it.owner)
            put(FileColumns.MetaTitle, it.metadata.title)
            put(FileColumns.MetaArtist, it.metadata.artist)
            put(FileColumns.MetaAlbum, it.metadata.album)
            put(FileColumns.MetaDuration, it.metadata.duration)
            put(FileColumns.MetaYear, it.metadata.year)
            put(FileColumns.MetaWidth, it.metadata.dimensions?.width)
            put(FileColumns.MetaHeight, it.metadata.dimensions?.height)
            put(FileColumns.MetaLocation, it.metadata.location)
            put(FileColumns.MetaAppName, it.metadata.appName)
            put(FileColumns.MetaAppPackageName, it.metadata.appPackageName)
        }
    }

    override fun Bundle.toResult(): File? {
        return File(
            id = get(FileColumns.Id) ?: return null,
            displayName = get(FileColumns.DisplayName) ?: return null,
            mimeType = get(FileColumns.MimeType) ?: return null,
            size = get(FileColumns.Size) ?: 0L,
            path = get(FileColumns.Path),
            uri = get(FileColumns.ContentUri)?.let { Uri.parse(it) } ?: return null,
            thumbnailUri = get(FileColumns.ThumbnailUri)?.let { Uri.parse(it) },
            isDirectory = get(FileColumns.IsDirectory) == false,
            owner = get(FileColumns.Owner),
            metadata = FileMetadata(
                title = get(FileColumns.MetaTitle),
                artist = get(FileColumns.MetaArtist),
                album = get(FileColumns.MetaAlbum),
                duration = get(FileColumns.MetaDuration),
                year = get(FileColumns.MetaYear),
                dimensions = run {
                    val width = get(FileColumns.MetaWidth)
                    val height = get(FileColumns.MetaHeight)
                    if (width != null && height != null) {
                        FileDimensions(width, height)
                    } else {
                        null
                    }
                },
                location = get(FileColumns.MetaLocation),
                appName = get(FileColumns.MetaAppName),
                appPackageName = get(FileColumns.MetaAppPackageName),
            ),
        )
    }
}