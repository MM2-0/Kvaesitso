package de.mm20.launcher2.ui.settings.contacts

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugins.PluginService
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContactsSettingsScreenVM : ViewModel(), KoinComponent {

    private val settings: ContactSearchSettings by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val pluginService: PluginService by inject()

    val hasCallPermission = permissionsManager.hasPermission(PermissionGroup.Call)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun requestCallPermission(activity: AppCompatActivity) =
        permissionsManager.requestPermission(activity, PermissionGroup.Call)

    val callOnTap = settings.callOnTap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setCallOnTap(callOnTap: Boolean) =
        settings.setCallOnTap(callOnTap)

    val hasContactsPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun requestContactsPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Contacts)
    }

    val availablePlugins = pluginService.getPluginsWithState(
        type = PluginType.ContactSearch,
        enabled = true,
    )

    val enabledProviders = settings.enabledProviders

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        settings.setProviderEnabled(providerId, enabled)
    }
}