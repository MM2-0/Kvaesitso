package de.mm20.launcher2.sdk.config

import android.os.Bundle
import de.mm20.launcher2.plugin.config.WeatherPluginConfig

internal fun WeatherPluginConfig.toBundle(): Bundle {
    return Bundle().apply {
        putLong("minUpdateInterval", minUpdateInterval)
        putBoolean("managedLocation", managedLocation)
    }
}