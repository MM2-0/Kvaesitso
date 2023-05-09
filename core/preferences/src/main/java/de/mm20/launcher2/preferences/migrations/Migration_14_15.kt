package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_14_15: VersionedMigration(14, 15) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setSearchBar(
            builder.searchBar.toBuilder()
                .setHiddenItemsButton(true)
        )
    }
}