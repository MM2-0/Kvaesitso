package de.mm20.launcher2.ui.settings.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.plugin.PluginPackage
import de.mm20.launcher2.plugins.PluginService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PluginsSettingsScreenVM : ViewModel(), KoinComponent {

    private val pluginService: PluginService by inject()

    val pluginPackages = pluginService
        .getPluginPackages()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(100), 1)

    val enabledPluginPackages = pluginPackages.mapLatest {
        it.filter { it.enabled }.sortedBy { it.label }
    }

    val disabledPluginPackages = pluginPackages.mapLatest {
        it.filter { !it.enabled }.sortedBy { it.label }
    }

    fun getIcon(plugin: PluginPackage) = flow {
        emit(pluginService.getPluginPackageIcon(plugin))
    }
}