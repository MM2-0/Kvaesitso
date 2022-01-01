package de.mm20.launcher2.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore

typealias LauncherDataStore = DataStore<Settings>

val Context.dataStore: LauncherDataStore by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer
)