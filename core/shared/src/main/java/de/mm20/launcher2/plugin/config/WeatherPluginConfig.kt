package de.mm20.launcher2.plugin.config

data class WeatherPluginConfig(
    /**
     * Minimum time (in ms) that needs to pass before the provider can be queried again.
     * Note that updates can be triggered sooner if the user manually changes their location
     * or weather provider.
     */
    val minUpdateInterval: Long = 60 * 60 * 1000L,
    /**
     * Whether the location is managed by the plugin. If true, the user cannot change the location
     * in the launcher settings.
     */
    val managedLocation: Boolean = false,
)