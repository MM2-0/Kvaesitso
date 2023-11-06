package de.mm20.launcher2.ui.settings.plugins

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.plugins.PluginService
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PluginsSettingsScreenVM : ViewModel(), KoinComponent {

    private val pluginService: PluginService by inject()
    private val permissionsManager: PermissionsManager by inject()

    val hostInstalled = pluginService.isPluginHostInstalled()
    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Plugins)
    val plugins = hasPermission.flatMapLatest {
        if (it) pluginService.getPluginsWithState() else emptyFlow()
    }

    fun setPluginEnabled(plugin: Plugin, value: Boolean) {
        if (value) {
            pluginService.enablePlugin(plugin)
        } else {
            pluginService.disablePlugin(plugin)
        }
    }

    fun requestPermission(context: Context) {
        permissionsManager.requestPermission(context as AppCompatActivity, PermissionGroup.Plugins)
    }

    fun getIcon(plugin: Plugin) = flow {
        emit(pluginService.getPluginIcon(plugin))
    }
}