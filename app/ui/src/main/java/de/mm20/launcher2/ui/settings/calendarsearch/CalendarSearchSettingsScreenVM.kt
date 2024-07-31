package de.mm20.launcher2.ui.settings.calendarsearch

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugins.PluginService
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CalendarSearchSettingsScreenVM : ViewModel(), KoinComponent {
    private val settings: CalendarSearchSettings by inject()
    private val pluginService: PluginService by inject()
    private val permissionsManager: PermissionsManager by inject()

    val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)

    val availablePlugins = pluginService.getPluginsWithState(
        type = PluginType.Calendar,
        enabled = true,
    )

    val enabledProviders = settings.enabledProviders

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        settings.setProviderEnabled(providerId, enabled)
    }

    fun requestCalendarPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Calendar)
    }
}