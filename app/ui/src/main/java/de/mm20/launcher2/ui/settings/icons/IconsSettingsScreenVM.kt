package de.mm20.launcher2.ui.settings.icons

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.badges.settings.BadgeSettings
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class IconsSettingsScreenVM(
    private val dataStore: LauncherDataStore,
    private val badgeSettings: BadgeSettings,
    private val iconService: IconService,
    private val favoritesService: FavoritesService,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {


    val columnCount = dataStore.data.map { it.grid.columnCount }
    fun setColumnCount(columnCount: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setGrid(it.grid.toBuilder().setColumnCount(columnCount))
                    .build()
            }
        }
    }

    val iconSize = dataStore.data.map { it.grid.iconSize }
    fun setIconSize(iconSize: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setGrid(it.grid.toBuilder().setIconSize(iconSize))
                    .build()
            }
        }
    }


    val showLabels = dataStore.data.map { it.grid.showLabels }
    fun setShowLabels(showLabels: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setGrid(it.grid.toBuilder().setShowLabels(showLabels))
                    .build()
            }
        }
    }

    val iconShape = dataStore.data.map { it.icons.shape }
    fun setIconShape(iconShape: Settings.IconSettings.IconShape) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setShape(iconShape)
                    )
                    .build()
            }
        }
    }

    val adaptifyLegacyIcons = dataStore.data.map { it.icons.adaptify }
    fun setAdaptifyLegacyIcons(adaptify: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setAdaptify(adaptify)
                    )
                    .build()
            }
        }
    }

    val themedIcons = dataStore.data.map { it.icons.themedIcons }
    fun setThemedIcons(themedIcons: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setThemedIcons(themedIcons)
                    )
                    .build()
            }
        }
    }

    val forceThemedIcons = dataStore.data.map { it.icons.forceThemed }
    fun setForceThemedIcons(forceThemedIcons: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setForceThemed(forceThemedIcons)
                    )
                    .build()
            }
        }
    }

    val installedIconPacks: Flow<List<IconPack>> = iconService.getInstalledIconPacks().map {
        listOf(
            IconPack(
                name = "System",
                packageName = "",
                version = "",
            )
        ) + it
    }

    val iconPackThemed = dataStore.data.map { it.icons.iconPackThemed }
    fun setIconPackThemed(iconPackThemed: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons
                            .toBuilder()
                            .setIconPackThemed(iconPackThemed)
                    )
                    .build()
            }
        }
    }

    val iconPack = dataStore.data.map { it.icons.iconPack }
    fun setIconPack(iconPack: String) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setIcons(
                        it.icons.toBuilder()
                            .setIconPack(iconPack)
                    )
                    .build()
            }
        }
    }

    val hasNotificationsPermission = permissionsManager.hasPermission(PermissionGroup.Notifications)

    val notificationBadges = badgeSettings.notifications
    fun setNotifications(notifications: Boolean) {
        badgeSettings.setNotifications(notifications)
    }

    fun requestNotificationsPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Notifications)
    }

    val cloudFileBadges = badgeSettings.cloudFiles
    fun setCloudFiles(cloudFiles: Boolean) {
        badgeSettings.setCloudFiles(cloudFiles)
    }

    val shortcutBadges = badgeSettings.shortcuts
    fun setShortcuts(shortcuts: Boolean) {
        badgeSettings.setShortcuts(shortcuts)
    }

    val suspendedAppBadges = badgeSettings.suspendedApps
    fun setSuspendedApps(suspendedApps: Boolean) {
        badgeSettings.setSuspendedApps(suspendedApps)
    }

    val pluginBadges = badgeSettings.plugins
    fun setPluginBadges(plugins: Boolean) {
        badgeSettings.setPlugins(plugins)
    }

    fun getPreviewIcons(size: Int): Flow<List<LauncherIcon?>> {
        return columnCount.flatMapLatest { cols ->
            favoritesService.getFavorites(
                includeTypes = listOf("app"),
                limit = cols,
                manuallySorted = true,
                automaticallySorted = true,
                frequentlyUsed = true,
            )
        }.flatMapLatest { apps ->
            combine(apps.map {
                iconService.getIcon(it, size)
            }) {
                it.toList()
            }
        }
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                IconsSettingsScreenVM(
                    dataStore = get(),
                    iconService = get(),
                    permissionsManager = get(),
                    favoritesService = get(),
                    badgeSettings = get(),
                )
            }
        }
    }
}