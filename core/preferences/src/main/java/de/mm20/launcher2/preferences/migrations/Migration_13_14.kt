package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_13_14 : VersionedMigration(13, 14) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder
            .setIcons(
                builder.icons.toBuilder()
                    .setIconPackThemed(true)
            )
    }
}