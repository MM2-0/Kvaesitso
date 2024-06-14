package de.mm20.launcher2.sdk.base

import android.net.Uri
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.SearchPluginContract

abstract class StringPluginProvider<T>(
    config: QueryPluginConfig,
) : QueryPluginProvider<String, T>(config) {

    override fun getQuery(uri: Uri): String? {
        return uri.getQueryParameter(SearchPluginContract.Paths.QueryParam)
    }
}