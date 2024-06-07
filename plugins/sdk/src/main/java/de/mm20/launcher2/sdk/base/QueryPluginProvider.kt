package de.mm20.launcher2.sdk.base

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.sdk.config.toBundle
import de.mm20.launcher2.sdk.utils.launchWithCancellationSignal
import kotlinx.coroutines.runBlocking

data class SearchParams(
    val allowNetwork: Boolean,
    val lang: String?,
)

data class GetParams(
    val lang: String?,
)

abstract class QueryPluginProvider<TQuery, TResult>(
    private val config: QueryPluginConfig,
) : BasePluginProvider() {

    abstract suspend fun search(query: TQuery, params: SearchParams): List<TResult>

    /**
     * Get an item by its id.
     * This only needs to be implemented if `config.storageStrategy` is set to `StoreReference` or `Deferred`
     */
    open suspend fun get(id: String, params: GetParams): TResult? = null

    internal abstract fun getQuery(uri: Uri): TQuery?

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = query(uri, projection, null, null)

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
                val query = getQuery(uri) ?: return null
                val params = getSearchParams(uri)
                val results = search(query, params, cancellationSignal)
                return results.toCursor()
            }

            uri.pathSegments.size == 2 && uri.pathSegments.first() == SearchPluginContract.Paths.Root -> {
                val id = uri.pathSegments[1]
                val params = getGetParams(uri)
                val result = runBlocking {
                    get(id, params)
                }
                return if (result != null) {
                    return listOf(result).toCursor()
                } else {
                    emptyList<TResult>().toCursor()
                }
            }
        }
        return null
    }

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

    private fun search(
        query: TQuery,
        params: SearchParams,
        cancellationSignal: CancellationSignal?
    ): List<TResult> {
        return launchWithCancellationSignal(cancellationSignal) {
            search(query, params)
        }
    }

    private fun getGetParams(uri: Uri): GetParams {
        val lang = uri.getQueryParameter(SearchPluginContract.Paths.LangParam)
        return GetParams(
            lang = lang,
        )
    }

    private fun getSearchParams(uri: Uri): SearchParams {
        val allowNetwork =
            uri.getQueryParameter(SearchPluginContract.Paths.AllowNetworkParam)?.toBoolean()
                ?: false
        val lang = uri.getQueryParameter(SearchPluginContract.Paths.LangParam)
        return SearchParams(
            allowNetwork = allowNetwork,
            lang = lang,
        )
    }

    internal abstract fun List<TResult>.toCursor(): Cursor

    final override fun getPluginConfig(): Bundle {
        return config.toBundle()
    }
}