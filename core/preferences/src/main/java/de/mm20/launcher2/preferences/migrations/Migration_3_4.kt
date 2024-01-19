package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_3_4: VersionedMigration(3, 4) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder.setAppShortcutSearch(
            LegacySettings.AppShortcutSearchSettings.newBuilder()
            .setEnabled(true)
        )
    }
}