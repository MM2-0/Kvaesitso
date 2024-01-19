package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class BadgeSettingsData(
    val notifications: Boolean = true,
    val suspendedApps: Boolean = true,
    val cloudFiles: Boolean = true,
    val shortcuts: Boolean = true,
    val plugins: Boolean = true,
)

class BadgeSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) : Flow<BadgeSettingsData> by (launcherDataStore.data.map {
    BadgeSettingsData(
        notifications = it.badgesNotifications,
        suspendedApps = it.badgesSuspendedApps,
        cloudFiles = it.badgesCloudFiles,
        shortcuts = it.badgesShortcuts,
        plugins = it.badgesPlugins,
    )
}) {

    val notifications
        get() = launcherDataStore.data.map { it.badgesNotifications }

    fun setNotifications(notifications: Boolean) {
        launcherDataStore.update {
            it.copy(badgesNotifications = notifications)
        }
    }

    val suspendedApps
        get() = launcherDataStore.data.map { it.badgesSuspendedApps }

    fun setSuspendedApps(suspendedApps: Boolean) {
        launcherDataStore.update {
            it.copy(badgesSuspendedApps = suspendedApps)
        }
    }

    val cloudFiles
        get() = launcherDataStore.data.map { it.badgesCloudFiles }

    fun setCloudFiles(cloudFiles: Boolean) {
        launcherDataStore.update {
            it.copy(badgesCloudFiles = cloudFiles)
        }
    }

    val shortcuts
        get() = launcherDataStore.data.map { it.badgesShortcuts }

    fun setShortcuts(shortcuts: Boolean) {
        launcherDataStore.update {
            it.copy(badgesShortcuts = shortcuts)
        }
    }

    val plugins
        get() = launcherDataStore.data.map { it.badgesPlugins }

    fun setPlugins(plugins: Boolean) {
        launcherDataStore.update {
            it.copy(badgesPlugins = plugins)
        }
    }
}