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
     * The launcher only stores the ID of the search result, and the plugin that created it. To
     * restore a result, the plugin is queried again. This allows the plugin to update key fields
     * (i.e. the label) immediately. However, plugins that use this strategy must guarantee, that
     * they can restore a search result at any time, in a timely manner. In particular, the plugin
     * must be able to restore a search result without any network requests. This strategy is best
     * suited for on-device search plugins.
     */
    @SerialName("ref")
    StoreReference,

    /**
     * The launcher stores all relevant information about this search result in its own internal
     * database. The result can be restored without querying the plugin again. The launcher will try
     * refresh the search result at its own discretion (e.g. when a user long-presses a restored
     * search result to view its details). This strategy is the default and should be used whenever
     * the plugin can't restore a search result immediately. It is best suited for online search
     * plugins.
     */
    @SerialName("copy")
    StoreCopy,
}