package de.mm20.launcher2.plugin.config

import android.os.Bundle

data class WeatherPluginConfig(
    /**
     * Minimum time (in ms) that needs to pass before the provider can be queried again.
     * Note that updates can be triggered sooner if the user manually changes their location
     * or weather provider.
     */
    val minUpdateInterval: Long = 60 * 60 * 1000L,
)