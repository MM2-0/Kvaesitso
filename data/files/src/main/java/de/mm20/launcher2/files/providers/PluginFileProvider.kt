package de.mm20.launcher2.files.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import de.mm20.launcher2.plugin.PluginApi
import de.mm20.launcher2.plugin.QueryPluginApi
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.FilePluginContract.FileColumns
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.plugin.data.set
import de.mm20.launcher2.plugin.data.withColumns
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult
import de.mm20.launcher2.search.file.FileMetadata
import kotlinx.collections.immutable.toPersistentMap
import java.time.Year
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri
import java.time.Instant

class PluginFileProvider(
    private val context: Context,
    private val pluginAuthority: String,
) : QueryPluginApi<String, PluginFile>(
    context, pluginAuthority
), FileProvider {

    private fun getPluginConfig(): QueryPluginConfig? {
        return PluginApi(pluginAuthority, context.contentResolver).getSearchPluginConfig()
    }

    override fun Cursor.getData(): List<PluginFile>? {
        val config = getPluginConfig()
        val cursor = this

        if (config == null) {
            Log.e("MM20", "Plugin $pluginAuthority returned null config")
            cursor.close()
            return null
        }

        val results = mutableListOf<PluginFile>()
        val timestamp = System.currentTimeMillis()
        cursor.withColumns(FileColumns) {
            while (cursor.moveToNext()) {
                results.add(
                    PluginFile(
                        id = cursor[FileColumns.Id] ?: continue,
                        path = cursor[FileColumns.Path],
                        mimeType = cursor[FileColumns.MimeType] ?: "application/octet-stream",
                        size = cursor[FileColumns.Size] ?: 0L,
                        owner = cursor[FileColumns.Owner],
                        metaData = FileMetadata(
                            title = cursor[FileColumns.MetaTitle],
                            artist = cursor[FileColumns.MetaArtist],
                            album = cursor[FileColumns.MetaAlbum],
                            duration = cursor[FileColumns.MetaDuration]?.milliseconds,
                            year = cursor[FileColumns.MetaYear]?.let { Year.of(it) },
                            date = cursor[FileColumns.MetaDate]?.let { ZonedDateTime.from(Instant.ofEpochSecond(it)) },
                            dimensions = cursor[FileColumns.MetaWidth]?.let { width ->
                                cursor[FileColumns.MetaHeight]?.let { height ->
                                    FileMetadata.Dimensions(width, height)
                                }
                            },
                            location = cursor[FileColumns.MetaLocation],
                            appName = cursor[FileColumns.MetaAppName],
                            appPackageName = cursor[FileColumns.MetaAppPackageName],
                        ),
                        label = cursor[FileColumns.DisplayName] ?: continue,
                        uri = cursor[FileColumns.ContentUri]?.toUri() ?: continue,
                        thumbnailUri = cursor[FileColumns.ThumbnailUri]?.toUri(),
                        storageStrategy = config.storageStrategy,
                        isDirectory = cursor[FileColumns.IsDirectory] == true,
                        authority = pluginAuthority,
                        timestamp = timestamp,
                        updatedSelf = {
                            if (it !is PluginFile) UpdateResult.TemporarilyUnavailable()
                            else refresh(it, timestamp).asUpdateResult()
                        }
                    )
                )
            }
        }
        cursor.close()
        return results
    }

    override fun PluginFile.toBundle(): Bundle {
        return Bundle().apply {
            set(FileColumns.Id, id)
            set(FileColumns.Path, path)
            set(FileColumns.ContentUri, uri.toString())
            set(FileColumns.MimeType, mimeType)
            set(FileColumns.Size, size)
            set(FileColumns.MetaTitle, metaData.title)
            set(FileColumns.MetaArtist, metaData.artist)
            set(FileColumns.MetaAlbum, metaData.album)
            set(FileColumns.MetaDuration, metaData.duration?.inWholeMilliseconds)
            set(FileColumns.MetaYear, metaData.year?.value)
            set(FileColumns.MetaDate, metaData.date?.toEpochSecond())
            set(
                FileColumns.MetaWidth,
                metaData.dimensions?.width,
            )
            set(
                FileColumns.MetaHeight,
                metaData.dimensions?.height,
            )
            set(FileColumns.MetaLocation, metaData.location)
            set(FileColumns.MetaAppName, metaData.appName)
            set(FileColumns.MetaAppPackageName, metaData.appPackageName)
            set(FileColumns.Owner, owner)
            set(FileColumns.DisplayName, label)
            set(FileColumns.ThumbnailUri, thumbnailUri?.toString())
            set(FileColumns.IsDirectory, isDirectory)
        }
    }

    override fun Uri.Builder.appendQueryParameters(query: String): Uri.Builder = apply {
        appendQueryParameter(SearchPluginContract.Params.Query, query)
    }
}