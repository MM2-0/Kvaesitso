package de.mm20.launcher2.plugin.config

import android.os.Bundle

fun SearchPluginConfig(bundle: Bundle): SearchPluginConfig? {
    return SearchPluginConfig(
        storageStrategy = valueOfOrElse(
            bundle.getString(
                "storageStrategy",
                StorageStrategy.StoreCopy.name
            ),
            StorageStrategy.StoreCopy,
        )
    )
}

private fun valueOfOrElse(value: String, default: StorageStrategy): StorageStrategy {
    return try {
        StorageStrategy.valueOf(value)
    } catch (e: IllegalArgumentException) {
        default
    }
}