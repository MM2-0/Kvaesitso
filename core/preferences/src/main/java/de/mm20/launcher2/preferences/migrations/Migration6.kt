package de.mm20.launcher2.preferences.migrations

import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.BatteryStatusVisibility
import de.mm20.launcher2.preferences.LauncherSettingsData

class Migration6 : DataMigration<LauncherSettingsData> {
    override suspend fun cleanUp() {
    }

    override suspend fun shouldMigrate(currentData: LauncherSettingsData): Boolean {
        return currentData.schemaVersion < 6
    }

    override suspend fun migrate(currentData: LauncherSettingsData): LauncherSettingsData {
        return currentData.copy(
            schemaVersion = 6,
            clockWidgetBatteryPart = if (currentData._clockWidgetBatteryPart) {
                BatteryStatusVisibility.Show
            } else {
                BatteryStatusVisibility.Hide
            }
        )
    }
}
