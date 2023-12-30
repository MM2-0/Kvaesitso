package de.mm20.launcher2.badges.settings.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.badges.settings.BadgeSettingsData
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.first

class Migration1(
    private val dataStore: LauncherDataStore,
): DataMigration<BadgeSettingsData> {
    override suspend fun cleanUp() {
    }

    override suspend fun shouldMigrate(currentData: BadgeSettingsData): Boolean {
        return currentData.schemaVersion < 1
    }

    override suspend fun migrate(currentData: BadgeSettingsData): BadgeSettingsData {
        val data = dataStore.data.first().badges
        return currentData.copy(
            notifications = data.notifications,
            suspendedApps = data.suspendedApps,
            cloudFiles = data.cloudFiles,
            shortcuts = data.shortcuts,
            schemaVersion = 1,
        )
    }
}