package de.mm20.launcher2.plugin.config

import android.os.Bundle
import de.mm20.launcher2.plugin.config.StorageStrategy

data class SearchPluginConfig(
    /**
     * Strategy to store items from this provider in the launcher database
     * @see [StorageStrategy]
     */
    val storageStrategy: StorageStrategy = StorageStrategy.StoreCopy,
)