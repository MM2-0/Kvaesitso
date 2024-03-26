package de.mm20.launcher2.plugin.config

/**
 * Defines how the launcher should store search results from a plugin (i.e. when the search result is
 * added to favorites).
 */
enum class StorageStrategy {
    /**
     * The launcher only stores the ID and the plugin provider for the search result. To restore the
     * search result, the launcher will query the plugin provider again. This strategy allows the
     * plugin provider to update a search result at a later point in time. However, plugins that use
     * this strategy must guarantee that a search result can be restored in a timely manner. In
     * particular, the plugin provider must be able to restore a search result without any network
     * requests.
     */
    StoreReference,

    /**
     * The launcher stores all relevant information in its own internal database. This strategy
     * is easier to implement, but search results cannot be updated at a later point in time.
     * Use this strategy if your plugin needs to perform network requests to retrieve search
     * results and if you don't want to implement a cache for search results.
     */
    StoreCopy;
}