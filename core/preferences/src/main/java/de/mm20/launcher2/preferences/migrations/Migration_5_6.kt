package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.DefaultCustomColorsBase
import de.mm20.launcher2.preferences.DefaultDarkCustomColorScheme
import de.mm20.launcher2.preferences.DefaultLightCustomColorScheme
import de.mm20.launcher2.preferences.Settings

class Migration_5_6: VersionedMigration(5, 6) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setAppearance(
            builder.appearance.toBuilder()
                .setCustomColors(Settings.AppearanceSettings.CustomColors.newBuilder()
                    .setAdvancedMode(false)
                    .setBaseColors(DefaultCustomColorsBase)
                    .setLightScheme(DefaultLightCustomColorScheme)
                    .setDarkScheme(DefaultDarkCustomColorScheme)
                )
        ).setUnitConverterSearch(
            builder.unitConverterSearch.toBuilder()
                .setCurrencies(true)
        )
    }
}