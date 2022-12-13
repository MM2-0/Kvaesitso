package de.mm20.launcher2.preferences.migrations

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import de.mm20.launcher2.preferences.SchemaVersion
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.createFactorySettings

class FactorySettingsMigration(private val context: Context): DataMigration<Settings> {
    override suspend fun cleanUp() {

    }

    override suspend fun migrate(currentData: Settings): Settings {
        Log.d("MM20", "Initializing user settings…")
        Log.d("MM20", "Done")
        val defaults = createFactorySettings(context)
        return defaults.toBuilder().setVersion(SchemaVersion).build()
    }

    override suspend fun shouldMigrate(currentData: Settings): Boolean {
        return currentData.version == 0
    }
}