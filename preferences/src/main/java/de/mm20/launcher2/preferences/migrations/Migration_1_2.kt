package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_1_2: VersionedMigration(1, 2) {

    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setSystemBars(
                builder.systemBars.toBuilder()
                    .setHideNavBar(false)
                    .setHideStatusBar(false)
            )
    }
}