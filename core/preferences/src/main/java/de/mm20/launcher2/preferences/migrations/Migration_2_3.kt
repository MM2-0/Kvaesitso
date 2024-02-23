package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.LegacySettings

class Migration_2_3: VersionedMigration(2, 3) {
    override suspend fun applyMigrations(builder: LegacySettings.Builder): LegacySettings.Builder {
        return builder.setClockWidget(
            builder.clockWidget.toBuilder()
                .setAlarmPart(true)
                .setBatteryPart(true)
                .setMusicPart(true)
        )
    }
}