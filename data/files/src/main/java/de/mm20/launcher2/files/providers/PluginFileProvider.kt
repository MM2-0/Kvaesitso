package de.mm20.launcher2.files.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.plugin.contracts.FilePluginContract
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.search.File
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PluginFileProvider(
    private val context: Context,
    private val plugin: Plugin,
) : FileProvider {
    override suspend fun search(query: String): List<File> {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(plugin.authority)
            .path(SearchPluginContract.Paths.Search)
            .appendQueryParameter(SearchPluginContract.Paths.QueryParam, query)
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
            )

            if (cursor == null) {
                Log.e("MM20", "Plugin ${plugin.authority} returned null cursor")
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val results = fromCursor(cursor) ?: emptyList()
            it.resume(results)
        }
    }

    suspend fun getFile(id: String): File? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(plugin.authority)
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
        val storageStrategyIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.StorageStrategy)
                .takeIf { it >= 0 }
        val directoryIndex =
            cursor.getColumnIndex(FilePluginContract.FileColumns.IsDirectory).takeIf { it >= 0 }

        val results = mutableListOf<File>()
        while (cursor.moveToNext()) {
            results.add(
                PluginFile(
                    id = cursor.getString(idIndex),
                    path = pathIndex?.let { cursor.getString(it) } ?: "",
                    mimeType = typeIndex?.let { cursor.getString(it) }
                        ?: "application/octet-stream",
                    size = sizeIndex?.let { cursor.getLong(it) } ?: 0,
                    metaData = persistentMapOf(),
                    label = cursor.getString(nameIndex),
                    uri = Uri.parse(cursor.getString(contentUriIndex)),
                    thumbnailUri = thumbnailUriIndex?.let {
                        cursor.getStringOrNull(it)
                    }?.let { Uri.parse(it) },
                    storageStrategy = try {
                        storageStrategyIndex?.let {
                            StorageStrategy.valueOf(cursor.getString(it))
                        }
                    } catch (e: IllegalArgumentException) {
                        null
                    } ?: StorageStrategy.StoreCopy,
                    isDirectory = directoryIndex?.let { cursor.getInt(it) } == 1,
                    authority = plugin.authority,
                )
            )
        }
        cursor.close()
        return results
    }
}