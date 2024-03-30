package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.ClockWidgetStyle.Digital1
import de.mm20.launcher2.preferences.LauncherSettingsData

class Migration2 : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {
    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 2
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        return currentData.copy(
            schemaVersion = 2,
            clockWidgetUseThemeColor = (currentData.clockWidgetStyle as? Digital1)?.variant == Digital1.Variant.MDY
        )
    }
}