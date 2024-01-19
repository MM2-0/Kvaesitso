package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_17_18 : VersionedMigration(17, 18) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder
            .setAppearance(builder.appearance.toBuilder()
                .setBlurWallpaperRadius(32)
            )
            .setClockWidget(builder.clockWidget.toBuilder()
                    .setAlignment(LegacySettings.ClockWidgetSettings.ClockWidgetAlignment.Bottom)
            )
    }
}