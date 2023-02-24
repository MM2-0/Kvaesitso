package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_12_13: VersionedMigration(12, 13) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder
            .setClockWidget(
                builder.clockWidget.toBuilder()
                    .setDatePart(true)
                    .build()
            )
    }
}