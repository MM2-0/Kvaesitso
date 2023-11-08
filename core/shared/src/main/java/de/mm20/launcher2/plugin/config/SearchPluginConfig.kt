package de.mm20.launcher2.plugin.config

import android.os.Bundle
import de.mm20.launcher2.plugin.config.StorageStrategy

data class SearchPluginConfig(
    /**
     * Icon resource to indicate that a result is from this provider
     */
    val providerIconRes: Int? = null,
    /**
     * Strategy to store items from this provider in the launcher database
     * @see [StorageStrategy]
     */
    val storageStrategy: StorageStrategy = StorageStrategy.StoreCopy,
) {
    fun toBundle(): Bundle {
        return Bundle().apply {
            putInt("providerIconRes", providerIconRes ?: 0)
            putString("storageStrategy", storageStrategy.name)
        }
    }

    companion object {
        operator fun invoke(bundle: Bundle): SearchPluginConfig? {
            return SearchPluginConfig(
                providerIconRes = bundle.getInt("providerIconRes", 0).takeIf { it != 0 },
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