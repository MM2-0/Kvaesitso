package de.mm20.launcher2.plugin.config

data class QueryPluginConfig(
    /**
     * Strategy to store items from this provider in the launcher database
     * @see [StorageStrategy]
     */
    val storageStrategy: StorageStrategy = StorageStrategy.StoreCopy,
)