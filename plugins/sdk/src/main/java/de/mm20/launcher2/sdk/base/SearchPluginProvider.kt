package de.mm20.launcher2.sdk.base

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import de.mm20.launcher2.plugin.config.SearchPluginConfig
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.sdk.config.toBundle
import de.mm20.launcher2.sdk.utils.launchWithCancellationSignal
import kotlinx.coroutines.runBlocking

abstract class SearchPluginProvider<T>(
    private val config: SearchPluginConfig,
) : QueryPluginProvider<String, T>() {

    /**
     * Search for items matching the given query
     * @param query The query to search for
     */
    abstract override suspend fun search(query: String, allowNetwork: Boolean): List<T>

    /**
     * Get an item by its id.
     * This only needs to be implemented if `config.storageStrategy` is set to `StoreReference`
     */
    override suspend fun get(id: String): T? {
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor? {
        val context = context ?: return null
        checkPermissionOrThrow(context)
        when {
            uri.pathSegments.size == 1 && uri.pathSegments.first() == SearchPluginContract.Paths.Search -> {
                val query =
                    uri.getQueryParameter(SearchPluginContract.Paths.QueryParam) ?: return null
                val allowNetwork =
                    uri.getQueryParameter(SearchPluginContract.Paths.AllowNetworkParam)?.toBoolean()
                        ?: false
                val results = search(query, allowNetwork, cancellationSignal)
                val cursor = createCursor(results.size)
                for (result in results) {
                    writeToCursor(cursor, result)
                }
                return cursor
            }

            uri.pathSegments.size == 2 && uri.pathSegments.first() == SearchPluginContract.Paths.Root -> {
                val id = uri.pathSegments[1]
                val result = runBlocking {
                    get(id)
                }
                return if (result != null) {
                    val cursor = createCursor(1)
                    writeToCursor(cursor, result)
                    cursor
                } else {
                    createCursor(0)
                }
            }
        }
        return null
    }

    private fun search(
        query: String,
        allowNetwork: Boolean,
        cancellationSignal: CancellationSignal?
    ): List<T> {
        return launchWithCancellationSignal(cancellationSignal) {
            search(query, allowNetwork)
        }
    }

    final override fun getPluginConfig(): Bundle {
        return config.toBundle()
    }

    internal abstract fun createCursor(capacity: Int): MatrixCursor
    internal abstract fun writeToCursor(cursor: MatrixCursor, item: T)
}