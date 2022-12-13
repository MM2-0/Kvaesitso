package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_10_11: VersionedMigration(10, 11) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setAppearance(
            builder.appearance.toBuilder()
                .setCustomColors(
                    builder.appearance.customColors.toBuilder()
                        .setLightScheme(
                            builder.appearance.customColors.lightScheme.toBuilder()
                                .setSurfaceTint(builder.appearance.customColors.lightScheme.primary)
                        )
                        .setDarkScheme(
                            builder.appearance.customColors.darkScheme.toBuilder()
                                .setSurfaceTint(builder.appearance.customColors.darkScheme.primary)
                        )
                )
        )
    }
}