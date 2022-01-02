package de.mm20.launcher2.preferences

import android.content.Context

fun createFactorySettings(context: Context): Settings {
    return Settings.newBuilder()
        .setAppearance(Settings.AppearanceSettings
            .newBuilder()
            .setTheme(Settings.AppearanceSettings.Theme.System)
            .setColorScheme(Settings.AppearanceSettings.ColorScheme.Default)
            .build()
        )
        .setWeather(Settings.WeatherSettings
            .newBuilder()
            .setProvider(Settings.WeatherSettings.WeatherProvider.MetNo)
            .setImperialUnits(context.resources.getBoolean(R.bool.default_imperialUnits))
            .build()
        )
        .build()
}