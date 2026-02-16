package de.mm20.launcher2.preferences

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import de.mm20.launcher2.preferences.migrations.Migration2
import de.mm20.launcher2.preferences.migrations.Migration3
import de.mm20.launcher2.preferences.migrations.Migration4
import de.mm20.launcher2.preferences.migrations.Migration5
import de.mm20.launcher2.settings.BaseSettings

internal class LauncherDataStore(
    private val context: Context,
): BaseSettings<LauncherSettingsData>(
    context,
    fileName = "settings.json",
    serializer = LauncherSettingsDataSerializer(context),
    migrations = listOf(
        Migration2(),
        Migration3(),
        Migration4(),
        Migration5(),
    ),
    corruptionHandler = ReplaceFileCorruptionHandler { LauncherSettingsData() }
) {

    val data
        get() = context.dataStore.data

    fun update(block: (LauncherSettingsData) -> LauncherSettingsData) {
        updateData(block)
    }
}
