package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_8_9: VersionedMigration(8, 9) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
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