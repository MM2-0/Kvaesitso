package de.mm20.launcher2.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.migrations.FactorySettingsMigration

typealias LauncherDataStore = DataStore<Settings>

val Context.dataStore: LauncherDataStore by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer,
    produceMigrations = {
        listOf(FactorySettingsMigration(it))
    },
    corruptionHandler = ReplaceFileCorruptionHandler {
        CrashReporter.logException(it)
        Log.d("MM20", "corruptionHandler")
        Settings.getDefaultInstance()
    }
)

internal const val SchemaVersion = 1