package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_3_4: VersionedMigration(3, 4) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setAppShortcutSearch(
            Settings.AppShortcutSearchSettings.newBuilder()
            .setEnabled(true)
        )
    }
}