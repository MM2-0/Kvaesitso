package de.mm20.launcher2.sdk.base

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import de.mm20.launcher2.plugin.contracts.PluginContract
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

abstract class SearchPluginProvider<T> : BasePluginProvider() {

    /**
     * Search for items matching the given query
     * @param query The query to search for
     */
    abstract suspend fun search(query: String): List<T>
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
            uri.path == SearchPluginContract.Paths.Search -> {
                val query =
                    uri.getQueryParameter(SearchPluginContract.Paths.QueryParam) ?: return null
                val results = search(query, cancellationSignal)
                val cursor = createCursor(results.size)
                for (result in results) {
                    writeToCursor(cursor, result)
                }
                return null
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
        cancellationSignal: CancellationSignal?
    ): List<T> {
        return runBlocking {
            val deferred = async {
                search(query)
            }
            cancellationSignal?.setOnCancelListener {
                deferred.cancel()
            }
            deferred.await()
        }
    }

    internal abstract fun createCursor(capacity: Int): MatrixCursor
    internal abstract fun writeToCursor(cursor: MatrixCursor, item: T)


    private fun checkPermissionOrThrow(context: Context) {
        if (context.checkCallingPermission(PluginContract.Permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        throw SecurityException("Caller does not have permission to use plugins")
    }
}