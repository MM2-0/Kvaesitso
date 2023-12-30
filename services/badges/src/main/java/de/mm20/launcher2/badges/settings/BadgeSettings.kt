package de.mm20.launcher2.badges.settings

import android.content.Context
import de.mm20.launcher2.badges.settings.migrations.Migration1
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.settings.BaseSettings
import kotlinx.coroutines.flow.map

class BadgeSettings(
    private val context: Context,
    dataStore: LauncherDataStore,
) : BaseSettings<BadgeSettingsData>(
    context = context,
    fileName = "badges.json",
    serializer = BadgeSettingsDataSerializer,
    migrations = listOf(
        Migration1(dataStore),
    )
) {

    internal val data
        get() = context.dataStore.data

    val notifications
        get() = context.dataStore.data.map { it.notifications }

    fun setNotifications(notifications: Boolean) {
        updateData {
            it.copy(notifications = notifications)
        }
    }

    val suspendedApps
        get() = context.dataStore.data.map { it.suspendedApps }

    fun setSuspendedApps(suspendedApps: Boolean) {
        updateData {
            it.copy(suspendedApps = suspendedApps)
        }
    }

    val cloudFiles
        get() = context.dataStore.data.map { it.cloudFiles }

    fun setCloudFiles(cloudFiles: Boolean) {
        updateData {
            it.copy(cloudFiles = cloudFiles)
        }
    }

    val shortcuts
        get() = context.dataStore.data.map { it.shortcuts }

    fun setShortcuts(shortcuts: Boolean) {
        updateData {
            it.copy(shortcuts = shortcuts)
        }
    }

    val plugins
        get() = context.dataStore.data.map { it.plugins }

    fun setPlugins(plugins: Boolean) {
        updateData {
            it.copy(plugins = plugins)
        }
    }
}
