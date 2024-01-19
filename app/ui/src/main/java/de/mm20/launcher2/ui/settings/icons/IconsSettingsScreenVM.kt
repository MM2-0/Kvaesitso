package de.mm20.launcher2.ui.settings.icons

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.ui.BadgeSettings
import de.mm20.launcher2.preferences.ui.IconSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class IconsSettingsScreenVM(
    private val uiSettings: UiSettings,
    private val iconSettings: IconSettings,
    private val badgeSettings: BadgeSettings,
    private val iconService: IconService,
    private val favoritesService: FavoritesService,
    private val permissionsManager: PermissionsManager,
) : ViewModel() {

    val grid = uiSettings.gridSettings

    fun setColumnCount(columnCount: Int) {
        uiSettings.setGridColumnCount(columnCount)
    }

    fun setIconSize(iconSize: Int) {
        uiSettings.setGridIconSize(iconSize)
    }

    fun setShowLabels(showLabels: Boolean) {
        uiSettings.setGridShowLabels(showLabels)
    }

    val iconShape = uiSettings.iconShape
    fun setIconShape(iconShape: IconShape) {
        uiSettings.setIconShape(iconShape)
    }

    val icons = iconSettings
    fun setAdaptifyLegacyIcons(adaptify: Boolean) {
        iconSettings.setAdaptifyLegacyIcons(adaptify)
    }

    fun setThemedIcons(themedIcons: Boolean) {
        iconSettings.setThemedIcons(themedIcons)
    }

    fun setForceThemedIcons(forceThemedIcons: Boolean) {
        iconSettings.setForceThemedIcons(forceThemedIcons)
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

    fun setIconPackThemed(iconPackThemed: Boolean) {
        iconSettings.setIconPackThemed(iconPackThemed)
    }

    fun setIconPack(iconPack: String?) {
        iconSettings.setIconPack(iconPack?.takeIf { it.isNotBlank() })
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
        return grid.flatMapLatest { grid ->
            favoritesService.getFavorites(
                includeTypes = listOf("app"),
                limit = grid.columnCount,
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
                    uiSettings = get(),
                    iconService = get(),
                    permissionsManager = get(),
                    favoritesService = get(),
                    badgeSettings = get(),
                    iconSettings = get(),
                )
            }
        }
    }
}