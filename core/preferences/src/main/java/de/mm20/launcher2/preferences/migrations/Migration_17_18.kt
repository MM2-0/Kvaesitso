package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_17_18 : VersionedMigration(17, 18) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder
            .setAppearance(builder.appearance.toBuilder()
                .setBlurWallpaperRadius(32)
            )
            .setClockWidget(builder.clockWidget.toBuilder()
                    .setAlignment(Settings.ClockWidgetSettings.ClockWidgetAlignment.Bottom)
            )
    }
}