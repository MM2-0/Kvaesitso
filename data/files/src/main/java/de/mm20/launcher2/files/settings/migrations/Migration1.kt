package de.mm20.launcher2.files.settings.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.files.settings.FileSearchSettingsData
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.first

/**
 * This migration is used to migrate the data from the old proto data store.
 * TODO: remove after a few releases
 */
internal class Migration1(
    private val dataStore: LauncherDataStore,
): DataMigration<FileSearchSettingsData> {
    override suspend fun cleanUp() {

    }

    override suspend fun shouldMigrate(currentData: FileSearchSettingsData): Boolean {
        return currentData.schemaVersion < 1
    }

    override suspend fun migrate(currentData: FileSearchSettingsData): FileSearchSettingsData {
        val data = dataStore.data.first().fileSearch
        return currentData.copy(
            localFiles = data.localFiles,
            gdriveFiles = data.gdrive,
            nextcloudFiles = data.nextcloud,
            owncloudFiles = data.owncloud,
            schemaVersion = 1,
        )
    }
}