package de.mm20.launcher2.files.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.text.format.DateUtils
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.config.SearchPluginConfig
import de.mm20.launcher2.plugin.contracts.FilePluginContract
import de.mm20.launcher2.plugin.contracts.PluginContract
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.FileMetaType
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class PluginFileProvider(
    private val context: Context,
    private val pluginAuthority: String,
) : FileProvider {
    override suspend fun search(query: String): List<File> = withContext(Dispatchers.IO) {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(SearchPluginContract.Paths.Search)
            .appendQueryParameter(SearchPluginContract.Paths.QueryParam, query)
            .build()
        val cancellationSignal = CancellationSignal()

        return@withContext suspendCancellableCoroutine {
            it.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            val cursor = try {
                context.contentResolver.query(
                    uri,
                    null,
                    null,
                    cancellationSignal
                )
            } catch (e: Exception) {
                Log.e("MM20", "Plugin ${pluginAuthority} threw exception")
                CrashReporter.logException(e)
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "Plugin ${pluginAuthority} returned null cursor")
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val results = fromCursor(cursor) ?: emptyList()
            it.resume(results)
        }
    }

    private fun getPluginConfig(): SearchPluginConfig? {
        val configBundle = try {
            context.contentResolver.call(
                Uri.Builder()
                    .scheme("content")
                    .authority(pluginAuthority)
                    .build(),
                PluginContract.Methods.GetConfig,
                null,
                null
            ) ?: return null
        } catch (e: Exception) {
            Log.e("MM20", "Plugin ${pluginAuthority} threw exception")
            CrashReporter.logException(e)
            return null
        }

        return SearchPluginConfig(configBundle)
    }

    suspend fun getFile(id: String): File? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(SearchPluginContract.Paths.Root)
            .appendPath(id)
            .build()
        val cancellationSignal = CancellationSignal()

        return suspendCancellableCoroutine {
            it.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                cancellationSignal
            ) ?: return@suspendCancellableCoroutine it.resume(null)

            val results = fromCursor(cursor)
            it.resume(results?.firstOrNull())
        }
    }

    private fun fromCursor(cursor: Cursor): List<File>? {
        val config = getPluginConfig()

        if (config == null) {
            Log.e("MM20", "Plugin ${pluginAuthority} returned null config")
            cursor.close()
            return null
        }

        val idIndex = cursor
            .getColumnIndex(FilePluginContract.FileColumns.Id)
            .takeIf { it >= 0 }
            ?: return null
        val pathIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.Path).takeIf { it >= 0 }
        val typeIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MimeType).takeIf { it >= 0 }
        val sizeIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.Size).takeIf { it >= 0 }
        val nameIndex = cursor.getColumnIndex(FilePluginContract.FileColumns.DisplayName)
            .takeIf { it >= 0 }
            ?: return null
        val contentUriIndex = cursor.getColumnIndex(FilePluginContract.FileColumns.ContentUri)
            .takeIf { it >= 0 }
            ?: return null
        val thumbnailUriIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.ThumbnailUri)
                .takeIf { it >= 0 }
        val directoryIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.IsDirectory).takeIf { it >= 0 }

        val ownerIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.Owner).takeIf { it >= 0 }

        val metaTitleIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaTitle).takeIf { it >= 0 }

        val metaArtistIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaArtist).takeIf { it >= 0 }

        val metaAlbumIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaAlbum).takeIf { it >= 0 }

        val metaDurationIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaDuration).takeIf { it >= 0 }

        val metaYearIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaYear).takeIf { it >= 0 }

        val metaWidthIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaWidth).takeIf { it >= 0 }

        val metaHeightIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaHeight).takeIf { it >= 0 }

        val metaLocationIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaLocation).takeIf { it >= 0 }

        val metaAppNameIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaAppName).takeIf { it >= 0 }

        val metaAppPackageNameIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.MetaAppPackageName)
                .takeIf { it >= 0 }

        val results = mutableListOf<File>()
        while (cursor.moveToNext()) {
            results.add(
                PluginFile(
                    id = cursor.getString(idIndex),
                    path = pathIndex?.let { cursor.getString(it) } ?: "",
                    mimeType = typeIndex?.let { cursor.getString(it) }
                        ?: "application/octet-stream",
                    size = sizeIndex?.let { cursor.getLong(it) } ?: 0,
                    metaData = buildMap {
                        metaTitleIndex?.let { cursor.getStringOrNull(it) }?.let {
                            put(FileMetaType.Title, it)
                        }
                        metaArtistIndex?.let { cursor.getStringOrNull(it) }?.let {
                            put(FileMetaType.Artist, it)
                        }
                        metaAlbumIndex?.let { cursor.getStringOrNull(it) }?.let {
                            put(FileMetaType.Album, it)
                        }
                        metaDurationIndex?.let { cursor.getLongOrNull(it) }?.let {
                            put(FileMetaType.Duration, DateUtils.formatElapsedTime(it / 1000L))
                        }
                        metaYearIndex?.let { cursor.getIntOrNull(it) }?.let {
                            put(FileMetaType.Year, it.toString())
                        }
                        if (metaWidthIndex != null && metaHeightIndex != null) {
                            val width = cursor.getIntOrNull(metaWidthIndex)
                            val height = cursor.getIntOrNull(metaHeightIndex)
                            if (width != null && height != null) {
                                put(FileMetaType.Dimensions, "${width}x${height}")
                            }
                        }
                        metaLocationIndex?.let { cursor.getStringOrNull(it) }?.let {
                            put(FileMetaType.Location, it)
                        }
                        metaAppNameIndex?.let { cursor.getStringOrNull(it) }?.let {
                            put(FileMetaType.AppName, it)
                        }
                        metaAppPackageNameIndex?.let { cursor.getStringOrNull(it) }?.let {
                            put(FileMetaType.AppPackageName, it)
                        }
                        ownerIndex?.let { cursor.getStringOrNull(it) }?.let {
                            put(FileMetaType.Owner, it)
                        }
                    }.toPersistentMap(),
                    label = cursor.getString(nameIndex),
                    uri = Uri.parse(cursor.getString(contentUriIndex)),
                    thumbnailUri = thumbnailUriIndex?.let {
                        cursor.getStringOrNull(it)
                    }?.let { Uri.parse(it) },
                    storageStrategy = config.storageStrategy,
                    isDirectory = directoryIndex?.let { cursor.getInt(it) } == 1,
                    authority = pluginAuthority,
                )
            )
        }
        cursor.close()
        return results
    }
}