package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_7_8: VersionedMigration(7, 8) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder.setAppearance(
            builder.appearance.toBuilder()
                .setBlurWallpaper(true)
        )
    }

}