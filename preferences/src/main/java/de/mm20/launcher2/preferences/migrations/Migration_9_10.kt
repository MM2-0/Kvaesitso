package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_9_10: VersionedMigration(9, 10) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setFavorites(
            builder.favorites.toBuilder()
                .setFrequentlyUsed(true)
                .setFrequentlyUsedRows(1)
                .setEditButton(true)
        )
    }
}