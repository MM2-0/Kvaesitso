package de.mm20.launcher2.plugin.config

import android.os.Bundle
import de.mm20.launcher2.plugin.config.StorageStrategy

data class SearchPluginConfig(
    /**
     * Strategy to store items from this provider in the launcher database
     * @see [StorageStrategy]
     */
    val storageStrategy: StorageStrategy = StorageStrategy.StoreCopy,
) {
    fun toBundle(): Bundle {
        return Bundle().apply {
            putString("storageStrategy", storageStrategy.name)
        }
    }

    companion object {
        operator fun invoke(bundle: Bundle): SearchPluginConfig? {
            return SearchPluginConfig(
                storageStrategy = StorageStrategy.valueOfOrElse(
                    bundle.getString(
                        "storageStrategy",
                        StorageStrategy.StoreCopy.name
                    ),
                    StorageStrategy.StoreCopy,
                )
            )
        }
    }
}