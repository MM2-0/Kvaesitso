package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_9_10 : VersionedMigration(9, 10) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder
            .setFavorites(
                builder.favorites.toBuilder()
                    .setFrequentlyUsed(true)
                    .setFrequentlyUsedRows(1)
                    .setEditButton(true)
            )
            .setWidgets(
                LegacySettings.WidgetSettings.newBuilder()
                    .setEditButton(true)
            )
    }
}