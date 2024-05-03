package de.mm20.launcher2.plugin.config

import android.os.Bundle

fun WeatherPluginConfig(bundle: Bundle): WeatherPluginConfig {
    return WeatherPluginConfig(
        minUpdateInterval = bundle.getLong(
            "minUpdateInterval",
            60 * 60 * 1000L
        ),
        managedLocation = bundle.getBoolean("managedLocation", false)
    )
}