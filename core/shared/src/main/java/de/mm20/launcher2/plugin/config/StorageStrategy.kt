package de.mm20.launcher2.plugin.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines how the launcher should store search results from a plugin (i.e. when the search result is
 * added to favorites).
 */
@Serializable
enum class StorageStrategy {
    /**
     * The launcher only stores the ID and the plugin provider for the search result. To restore the
     * search result, the launcher will query the plugin provider again. This strategy allows the
     * plugin provider to update a search result at a later point in time. However, plugins that use
     * this strategy must guarantee that a search result can be restored in a timely manner. In
     * particular, the plugin provider must be able to restore a search result without any network
     * requests.
     */
    @SerialName("ref")
    StoreReference,

    /**
     * The launcher stores all relevant information in its own internal database. This strategy
     * is easier to implement, but search results cannot be updated at a later point in time.
     * Use this strategy if your plugin needs to perform network requests to retrieve search
     * results and if you don't want to implement a cache for search results.
     */
    @SerialName("copy")
    StoreCopy,

    /**
     * The launcher stores all relevant information in its own internal database, like [StoreCopy].
     * A fresh copy is fetched from the plugin provider when the user opens the search result's
     * detail view. This allows the plugin provider to update the search result at a later point in
     * time, without the time constraints of [StoreReference].
     */
    @SerialName("deferred")
    Deferred,
}