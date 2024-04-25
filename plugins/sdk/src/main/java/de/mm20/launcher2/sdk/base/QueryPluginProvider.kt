package de.mm20.launcher2.sdk.base

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

abstract class QueryPluginProvider<TQuery, TResult> : BasePluginProvider() {

    abstract suspend fun search(query: TQuery, allowNetwork: Boolean): List<TResult>

    abstract suspend fun get(id: String): TResult?

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = query(uri, projection, null, null)

    override fun getType(uri: Uri): String? =
        throw UnsupportedOperationException("This operation is not supported")

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("This operation is not supported")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("This operation is not supported")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("This operation is not supported")

}