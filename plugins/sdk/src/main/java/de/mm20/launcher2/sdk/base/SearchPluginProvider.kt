package de.mm20.launcher2.sdk.base

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import de.mm20.launcher2.plugin.config.SearchPluginConfig
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.sdk.utils.launchWithCancellationSignal
import kotlinx.coroutines.runBlocking

abstract class SearchPluginProvider<T>(
    private val config: SearchPluginConfig,
) : BasePluginProvider() {

    /**
     * Search for items matching the given query
     * @param query The query to search for
     */
    abstract suspend fun search(query: String, allowNetwork: Boolean): List<T>
    abstract suspend fun get(id: String): T?

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return query(uri, projection, null, null)
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

    override fun getType(uri: Uri): String? {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("This operation is not supported")
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