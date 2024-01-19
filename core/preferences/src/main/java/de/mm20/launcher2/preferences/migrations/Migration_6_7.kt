package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_6_7 : VersionedMigration(6, 7) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder.setIcons(
            builder.icons.toBuilder()
                .setAdaptify(true)
        )
    }
}