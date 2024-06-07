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

/**
 * Parameters that are passed to the [refresh] method.
 */
data class RefreshParams(
    /**
     * The current language of the launcher.
     */
    val lang: String?,
)

abstract class QueryPluginProvider<TQuery, TResult>(
    private val config: QueryPluginConfig,
) : BasePluginProvider() {

    abstract suspend fun search(query: TQuery, params: SearchParams): List<TResult>

    /**
     * Get an item by its id.
     * This only needs to be implemented if `config.storageStrategy` is set to `StoreReference`
     */
    open suspend fun get(id: String, params: GetParams): TResult? = null

    /**
     * Request an updated copy of the item.
     * This is called when `config.storageStrategy` is set to `StoreCopy` and the launcher wants to refresh the item.
     * By default, this method returns the same item.
     * @param item the old item that should be refreshed
     * @param params the parameters that should be used to refresh the item
     */
    open suspend fun refresh(item: TResult, params: RefreshParams): TResult? = item

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
                val result = if (queryArgs != null) {
                    val oldItem = queryArgs.toResult() ?: return null
                    val params = getRefreshParams(uri)
                    refresh(oldItem, params, cancellationSignal)
                } else {
                    val params = getGetParams(uri)
                    get(id, params, cancellationSignal)
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

    private fun refresh(
        item: TResult,
        params: RefreshParams,
        cancellationSignal: CancellationSignal?
    ): TResult? {
        return launchWithCancellationSignal(cancellationSignal) {
            refresh(item, params)
        }
    }

    private fun get(
        id: String,
        params: GetParams,
        cancellationSignal: CancellationSignal?
    ): TResult? {
        return launchWithCancellationSignal(cancellationSignal) {
            get(id, params)
        }
    }

    private fun getGetParams(uri: Uri): GetParams {
        val lang = uri.getQueryParameter(SearchPluginContract.Params.Lang)
        return GetParams(
            lang = lang,
        )
    }

    private fun getSearchParams(uri: Uri): SearchParams {
        val allowNetwork =
            uri.getQueryParameter(SearchPluginContract.Params.AllowNetwork)?.toBoolean()
                ?: false
        val lang = uri.getQueryParameter(SearchPluginContract.Params.Lang)
        return SearchParams(
            allowNetwork = allowNetwork,
            lang = lang,
        )
    }

    private fun getRefreshParams(uri: Uri): RefreshParams {
        val lang = uri.getQueryParameter(SearchPluginContract.Params.Lang)
        return RefreshParams(
            lang = lang,
        )
    }

    internal abstract fun List<TResult>.toCursor(): Cursor

    internal abstract fun Bundle.toResult(): TResult?

    final override fun getPluginConfig(): Bundle {
        return config.toBundle()
    }
}