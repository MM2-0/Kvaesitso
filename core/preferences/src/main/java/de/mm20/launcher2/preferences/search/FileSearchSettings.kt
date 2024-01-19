package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileSearchSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val enabledProviders: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.fileSearchProviders }

    val localFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("local") }

    fun setLocalFiles(localFiles: Boolean) {
        launcherDataStore.update {
            if (localFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "local")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "local")
            }
        }
    }

    val gdriveFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("gdrive") }

    fun setGdriveFiles(gdriveFiles: Boolean) {
        launcherDataStore.update {
            if (gdriveFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "gdrive")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "gdrive")
            }
        }
    }

    val nextcloudFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("nextcloud") }

    fun setNextcloudFiles(nextcloudFiles: Boolean) {
        launcherDataStore.update {
            if (nextcloudFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "nextcloud")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "nextcloud")
            }
        }
    }

    val owncloudFiles
        get() = launcherDataStore.data.map { it.fileSearchProviders.contains("owncloud") }

    fun setOwncloudFiles(owncloudFiles: Boolean) {
        launcherDataStore.update {
            if (owncloudFiles) {
                it.copy(fileSearchProviders = it.fileSearchProviders + "owncloud")
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - "owncloud")
            }
        }
    }

    val enabledPlugins: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.fileSearchProviders - "local" - "gdrive" - "nextcloud" - "owncloud" }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        launcherDataStore.update {
            if (enabled) {
                it.copy(fileSearchProviders = it.fileSearchProviders + authority)
            } else {
                it.copy(fileSearchProviders = it.fileSearchProviders - authority)
            }
        }
    }
}