package de.mm20.launcher2.files.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.text.format.DateUtils
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.PluginApi
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.FilePluginContract.FileColumns
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.plugin.contracts.withColumns
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
    override suspend fun search(query: String, allowNetwork: Boolean): List<File> =
        withContext(Dispatchers.IO) {
            val lang = context.resources.configuration.locales.get(0).language
            val uri = Uri.Builder()
                .scheme("content")
                .authority(pluginAuthority)
                .path(SearchPluginContract.Paths.Search)
                .appendQueryParameter(SearchPluginContract.Paths.QueryParam, query)
                .appendQueryParameter(
                    SearchPluginContract.Paths.AllowNetworkParam,
                    allowNetwork.toString()
                )
                .appendQueryParameter(SearchPluginContract.Paths.LangParam, lang)
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

    private fun getPluginConfig(): QueryPluginConfig? {
        return PluginApi(pluginAuthority, context.contentResolver).getSearchPluginConfig()
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

        val results = mutableListOf<File>()
        cursor.withColumns(FileColumns) {
            while (cursor.moveToNext()) {
                results.add(
                    PluginFile(
                        id = cursor[FileColumns.Id] ?: continue,
                        path = cursor[FileColumns.Path] ?: "",
                        mimeType = cursor[FileColumns.MimeType] ?: "application/octet-stream",
                        size = cursor[FileColumns.Size] ?: 0L,
                        metaData = buildMap {
                            cursor[FileColumns.MetaTitle]?.let {
                                put(FileMetaType.Title, it)
                            }
                            cursor[FileColumns.MetaArtist]?.let {
                                put(FileMetaType.Artist, it)
                            }
                            cursor[FileColumns.MetaAlbum]?.let {
                                put(FileMetaType.Album, it)
                            }
                            cursor[FileColumns.MetaDuration]?.let {
                                put(FileMetaType.Duration, DateUtils.formatElapsedTime(it / 1000L))
                            }
                            cursor[FileColumns.MetaYear]?.let {
                                put(FileMetaType.Year, it.toString())
                            }
                            val width = cursor[FileColumns.MetaWidth]
                            val height = cursor[FileColumns.MetaHeight]
                            if (width != null && height != null) {
                                put(FileMetaType.Dimensions, "${width}x${height}")
                            }
                            cursor[FileColumns.MetaLocation]?.let {
                                put(FileMetaType.Location, it)
                            }
                            cursor[FileColumns.MetaAppName]?.let {
                                put(FileMetaType.AppName, it)
                            }
                            cursor[FileColumns.MetaAppPackageName]?.let {
                                put(FileMetaType.AppPackageName, it)
                            }
                            cursor[FileColumns.Owner]?.let {
                                put(FileMetaType.Owner, it)
                            }
                        }.toPersistentMap(),
                        label = cursor[FileColumns.DisplayName] ?: continue,
                        uri = cursor[FileColumns.DisplayName]?.let { Uri.parse(it) } ?: continue,
                        thumbnailUri = cursor[FileColumns.ThumbnailUri]?.let { Uri.parse(it) },
                        storageStrategy = config.storageStrategy,
                        isDirectory = cursor[FileColumns.IsDirectory] ?: false,
                        authority = pluginAuthority,
                    )
                )
            }
        }
        cursor.close()
        return results
    }
}