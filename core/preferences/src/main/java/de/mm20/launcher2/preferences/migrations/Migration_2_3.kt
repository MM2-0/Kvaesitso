package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_2_3: VersionedMigration(2, 3) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder.setClockWidget(
            builder.clockWidget.toBuilder()
                .setAlarmPart(true)
                .setBatteryPart(true)
                .setDatePart(true)
                .setMusicPart(true)
        )
    }
}