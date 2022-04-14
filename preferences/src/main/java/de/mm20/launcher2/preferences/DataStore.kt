package de.mm20.launcher2.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.migrations.*

typealias LauncherDataStore = DataStore<Settings>

internal val Context.dataStore: LauncherDataStore by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer,
    produceMigrations = {
        listOf(
            FactorySettingsMigration(it),
            Migration_1_2(),
            Migration_2_3(),
            Migration_3_4(),
            Migration_4_5(),
            Migration_5_6(),
        )
    },
    corruptionHandler = ReplaceFileCorruptionHandler {
        CrashReporter.logException(it)
        Log.d("MM20", "corruptionHandler")
        Settings.getDefaultInstance()
    }
)

internal const val SchemaVersion = 6