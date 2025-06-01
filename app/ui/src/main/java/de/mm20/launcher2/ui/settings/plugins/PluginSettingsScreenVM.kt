package de.mm20.launcher2.ui.settings.plugins

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.calendar.providers.CalendarList
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.Plugin
import de.mm20.launcher2.plugin.PluginPackage
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugins.PluginService
import de.mm20.launcher2.plugins.PluginWithState
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.preferences.weather.WeatherSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PluginSettingsScreenVM : ViewModel(), KoinComponent {
    private val pluginService by inject<PluginService>()
    private val calendarRepository by inject<CalendarRepository>()
    private val fileSearchSettings: FileSearchSettings by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()
    private val calendarSearchSettings: CalendarSearchSettings by inject()
    private val contactSearchSettings: ContactSearchSettings by inject()
    private val weatherSettings: WeatherSettings by inject()

    private var pluginPackageName = MutableStateFlow<String?>(null)

    val pluginPackage: StateFlow<PluginPackage?> = pluginPackageName.flatMapLatest {
        if (it == null) {
            emptyFlow()
        } else {
            pluginService.getPluginPackage(it)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(100), null)

    val icon: Flow<Drawable?> = pluginPackage
        .distinctUntilChangedBy { it?.packageName }
        .map {
            it?.let { pluginService.getPluginPackageIcon(it) }
        }

    val types: Flow<List<PluginType>> = pluginPackage.map {
        it?.plugins?.map { it.type }?.distinct() ?: emptyList()
    }

    val states = pluginPackage
        .map {
            it?.plugins?.map {
                val state = pluginService.getPluginState(it)
                PluginWithState(it, state)
            } ?: emptyList()
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    val hasPermission = states
        .map {
            it.none { it.state is PluginState.NoPermission }
        }

    val filePlugins = states
        .map {
            it.filter { it.plugin.type == PluginType.FileSearch }
        }

    val locationPlugins = states
        .map {
            it.filter { it.plugin.type == PluginType.LocationSearch }
        }

    val calendarPlugins = states
        .map {
            it.filter { it.plugin.type == PluginType.Calendar }
        }

    val contactPlugins = states
        .map {
            it.filter { it.plugin.type == PluginType.ContactSearch }
        }

    val weatherPlugins = states
        .map {
            it.filter { it.plugin.type == PluginType.Weather }
        }


    fun init(pluginId: String) {
        this.pluginPackageName.value = pluginId
    }

    fun setPluginEnabled(enabled: Boolean) {
        val plugin = pluginPackage.value ?: return
        if (enabled) {
            pluginService.enablePluginPackage(plugin)
        } else {
            pluginService.disablePluginPackage(plugin)
        }
    }

    fun openAppInfo(context: Context) {
        val plugin = pluginPackage.value ?: return
        context.tryStartActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${plugin.packageName}")
        })
    }

    fun uninstall(context: Context) {
        val plugin = pluginPackage.value ?: return
        pluginService.uninstallPluginPackage(context, plugin)
    }


    val enabledFileSearchPlugins = fileSearchSettings.enabledPlugins
    fun setFileSearchPluginEnabled(authority: String, enabled: Boolean) {
        fileSearchSettings.setPluginEnabled(authority, enabled)
    }

    val enabledContactPlugins = contactSearchSettings.enabledPlugins
    fun setContactPluginEnabled(authority: String, enabled: Boolean) {
        contactSearchSettings.setPluginEnabled(authority, enabled)
    }

    val enabledLocationSearchPlugins = locationSearchSettings.enabledPlugins
    fun setLocationSearchPluginEnabled(authority: String, enabled: Boolean) {
        locationSearchSettings.setPluginEnabled(authority, enabled)
    }

    val enabledCalendarSearchPlugins = calendarSearchSettings.providers
    fun setCalendarSearchPluginEnabled(authority: String, enabled: Boolean) {
        calendarSearchSettings.setProviderEnabled(authority, enabled)
    }

    val weatherProvider = weatherSettings.providerId
    fun setWeatherProvider(providerId: String) {
        weatherSettings.setProvider(providerId)
    }

    fun getCalendarLists(plugin: Plugin): Flow<List<CalendarList>> {
        return calendarRepository.getCalendars(plugin.authority)
    }

    val excludedCalendars = calendarSearchSettings.excludedCalendars
    fun setCalendarExcluded(calendarId: String, excluded: Boolean) {
        calendarSearchSettings.setCalendarExcluded(calendarId, excluded)
    }
}