package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherSettingsData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class Migration6 : DataMigration<LauncherSettingsData>, KoinComponent  {
    private val permissionsManager: PermissionsManager by inject()

    override suspend fun cleanUp() {
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        return currentData.copy(
            schemaVersion = 6,
            calendarSearchProviders =
                if (!permissionsManager.checkPermissionOnce(PermissionGroup.Calendar))
                    currentData.calendarSearchProviders - "local"
                else
                    currentData.calendarSearchProviders,
            fileSearchProviders =
                if (!permissionsManager.checkPermissionOnce(PermissionGroup.ExternalStorage))
                    currentData.fileSearchProviders - "local"
                else
                    currentData.fileSearchProviders,
        )
    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 6
    }
}