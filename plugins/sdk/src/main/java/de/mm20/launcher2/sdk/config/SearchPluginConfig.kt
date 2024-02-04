package de.mm20.launcher2.sdk.config

import android.os.Bundle
import de.mm20.launcher2.plugin.config.SearchPluginConfig

internal fun SearchPluginConfig.toBundle(): Bundle {
    return Bundle().apply {
        putString("storageStrategy", storageStrategy.name)
    }
}