package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.LauncherSettingsData

/** Turn on radio/TV favorite search; the previous default hid saved stations from the launcher. */
internal class Migration12 : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {}

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 12
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        return currentData.copy(
            schemaVersion = 12,
            searchResults = currentData.searchResults.copy(
                radioStationsInSearch = true,
                tvChannelsInSearch = true,
            ),
        )
    }
}
