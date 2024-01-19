package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_14_15: VersionedMigration(14, 15) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder.setSearchBar(
            builder.searchBar.toBuilder()
                .setHiddenItemsButton(true)
        )
    }
}