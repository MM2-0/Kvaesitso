package de.mm20.launcher2.sdk.files

import android.database.Cursor
import android.database.MatrixCursor
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.FilePluginContract
import de.mm20.launcher2.plugin.contracts.FilePluginContract.FileColumns
import de.mm20.launcher2.plugin.contracts.cursorOf
import de.mm20.launcher2.sdk.base.StringPluginProvider

abstract class FileProvider(
    config: QueryPluginConfig,
) : StringPluginProvider<File>(config) {

    final override fun getPluginType(): PluginType {
        return PluginType.FileSearch
    }

    override fun List<File>.toCursor(): Cursor {
        return cursorOf(FileColumns, this) {
            FileColumns.Id.set(it.id)
            FileColumns.DisplayName.set(it.displayName)
            FileColumns.MimeType.set(it.mimeType)
            FileColumns.Size.set(it.size)
            FileColumns.Path.set(it.path)
            FileColumns.ContentUri.set(it.uri.toString())
            FileColumns.ThumbnailUri.set(it.thumbnailUri?.toString())
            FileColumns.IsDirectory.set(it.isDirectory)
            FileColumns.Owner.set(it.owner)
            FileColumns.MetaTitle.set(it.metadata.title)
            FileColumns.MetaArtist.set(it.metadata.artist)
            FileColumns.MetaAlbum.set(it.metadata.album)
            FileColumns.MetaDuration.set(it.metadata.duration)
            FileColumns.MetaYear.set(it.metadata.year)
            FileColumns.MetaWidth.set(it.metadata.dimensions?.width)
            FileColumns.MetaHeight.set(it.metadata.dimensions?.height)
            FileColumns.MetaLocation.set(it.metadata.location)
            FileColumns.MetaAppName.set(it.metadata.appName)
            FileColumns.MetaAppPackageName.set(it.metadata.appPackageName)
        }
    }
}