package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_8_9: VersionedMigration(8, 9) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder
            .setClockWidget(
                builder.clockWidget.toBuilder()
                    .setFillHeight(true)
            )
            .setGrid(
                builder.grid.toBuilder()
                    .setShowLabels(true)
            )
    }

}