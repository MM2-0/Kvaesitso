package de.mm20.launcher2.preferences.migrations

import de.mm20.launcher2.preferences.Settings

class Migration_11_12: VersionedMigration(11, 12) {
    override suspend fun applyMigrations(builder: Settings.Builder): Settings.Builder {
        return builder
            .setSearchActions(
                Settings.SearchActionSettings.newBuilder()
                    .setCall(true)
                    .setContact(true)
                    .setEmail(true)
                    .setMessage(true)
                    .setOpenUrl(true)
                    .setScheduleEvent(true)
                    .setSetAlarm(true)
                    .setStartTimer(true)
            )
    }
}