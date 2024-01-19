package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_4_5: VersionedMigration(4, 5) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder.setGrid(
            builder.grid.toBuilder()
                .setIconSize(48)
                .build()
        )
    }
}