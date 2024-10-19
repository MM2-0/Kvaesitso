package de.mm20.launcher2.plugin

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.plugin.contracts.PluginContract
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private class NotUpdated : Exception("Not updated")

abstract class QueryPluginApi<TQuery, TResult>(
    private val context: Context,
    private val pluginAuthority: String,
) {
    private fun getLanguage() = context.resources.configuration.locales[0].language

    fun getConfig(): QueryPluginConfig? {
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
            Log.e("MM20", "Plugin $pluginAuthority threw exception", e)
            return null
        }
        return QueryPluginConfig(configBundle)
    }

    suspend fun search(query: TQuery, allowNetwork: Boolean): List<TResult> = withContext(Dispatchers.IO) {
            val lang = getLanguage()
            val uri = Uri.Builder()
                .scheme("content")
                .authority(pluginAuthority)
                .path(SearchPluginContract.Paths.Search)
                .appendQueryParameters(query)
                .appendQueryParameter(
                    SearchPluginContract.Params.AllowNetwork,
                    allowNetwork.toString()
                )
                .appendQueryParameter(
                    SearchPluginContract.Params.Lang,
                    lang
                )
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
                    Log.e("MM20", "Plugin $pluginAuthority threw exception", e)
                    it.resume(emptyList())
                    return@suspendCancellableCoroutine
                }

                if (cursor == null) {
                    Log.e("MM20", "Plugin $pluginAuthority returned null cursor")
                    it.resume(emptyList())
                    return@suspendCancellableCoroutine
                }

                val results = cursor.getData() ?: emptyList()
                it.resume(results)
            }
        }

    suspend fun get(id: String): Result<TResult?> = withContext(Dispatchers.IO) {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(SearchPluginContract.Paths.Root)
            .appendPath(id)
            .appendQueryParameter(SearchPluginContract.Params.Lang, getLanguage())
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
                Log.e("MM20", "Plugin $pluginAuthority threw exception", e)
                it.resume(Result.failure(e))
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "Plugin $pluginAuthority returned null cursor")
                it.resume(Result.success(null))
                return@suspendCancellableCoroutine
            }

            val result = cursor.getData()?.firstOrNull()

            if (result == null) {
                it.resume(Result.success(null))
                return@suspendCancellableCoroutine
            }

            it.resume(Result.success(result))
        }
    }

    suspend fun refresh(item: TResult, lastUpdate: Long): Result<TResult?> = withContext(Dispatchers.IO) {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(SearchPluginContract.Paths.Refresh)
            .appendQueryParameter(SearchPluginContract.Params.Lang, getLanguage())
            .appendQueryParameter(SearchPluginContract.Params.UpdatedAt, lastUpdate.toString())
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
                    item.toBundle(),
                    cancellationSignal
                )
            } catch (e: Exception) {
                Log.e("MM20", "Plugin $pluginAuthority threw exception", e)
                it.resume(Result.failure(e))
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "Plugin $pluginAuthority returned null cursor")
                it.resume(Result.failure(IllegalArgumentException()))
                return@suspendCancellableCoroutine
            }

            if (cursor.extras?.getBoolean(SearchPluginContract.Extras.NotUpdated) == true) {
                it.resume(Result.failure(NotUpdated()))
                return@suspendCancellableCoroutine
            }

            val result = cursor.getData()?.firstOrNull()

            if (result == null) {
                it.resume(Result.success(null))
                return@suspendCancellableCoroutine
            }

            it.resume(Result.success(result))
        }
    }

    protected abstract fun Uri.Builder.appendQueryParameters(query: TQuery): Uri.Builder

    protected abstract fun Cursor.getData(): List<TResult>?

    protected abstract fun TResult.toBundle(): Bundle
}