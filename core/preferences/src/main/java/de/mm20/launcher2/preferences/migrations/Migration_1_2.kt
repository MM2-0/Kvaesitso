package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_1_2: VersionedMigration(1, 2) {

    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder.setSystemBars(
                builder.systemBars.toBuilder()
                    .setHideNavBar(false)
                    .setHideStatusBar(false)
            )
            .setSearchBar(builder.searchBar.toBuilder().setAutoFocus(false))
    }
}