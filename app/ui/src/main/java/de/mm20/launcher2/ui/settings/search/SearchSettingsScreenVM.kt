package de.mm20.launcher2.ui.settings.search

import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugins.PluginService
import de.mm20.launcher2.preferences.search.CalculatorSearchSettings
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.preferences.search.SearchFilterSettings
import de.mm20.launcher2.preferences.search.ShortcutSearchSettings
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.preferences.search.WebsiteSearchSettings
import de.mm20.launcher2.preferences.search.WikipediaSearchSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.search.SearchFilters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchSettingsScreenVM : ViewModel(), KoinComponent {
    private val searchUiSettings: SearchUiSettings by inject()
    private val contactSearchSettings: ContactSearchSettings by inject()
    private val calendarSearchSettings: CalendarSearchSettings by inject()
    private val shortcutSearchSettings: ShortcutSearchSettings by inject()
    private val wikipediaSearchSettings: WikipediaSearchSettings by inject()
    private val websiteSearchSettings: WebsiteSearchSettings by inject()
    private val unitConverterSettings: UnitConverterSettings by inject()
    private val calculatorSearchSettings: CalculatorSearchSettings by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()
    private val searchFilterSettings: SearchFilterSettings by inject()

    private val appRepository: AppRepository by inject()

    private val pluginService: PluginService by inject()
    private val permissionsManager: PermissionsManager by inject()

    val favorites = searchUiSettings.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFavorites(favorites: Boolean) {
        searchUiSettings.setFavorites(favorites)
    }

    val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val calendarSearch = calendarSearchSettings.isProviderEnabled("local")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCalendarSearch(enabled: Boolean) {
        calendarSearchSettings.setProviderEnabled("local", enabled)
    }

    val hasContactsPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val contacts = contactSearchSettings.isProviderEnabled("local")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setContacts(contacts: Boolean) {
        contactSearchSettings.setProviderEnabled("local", contacts)
    }

    val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val placesSearch = locationSearchSettings.osmLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setPlacesSearch(enabled: Boolean) {
        locationSearchSettings.setOsmLocations(enabled)
    }

    fun requestLocationPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Location)
    }

    fun requestCalendarPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Calendar)
    }

    fun requestContactsPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Contacts)
    }

    val calculator = calculatorSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setCalculator(calculator: Boolean) {
        calculatorSearchSettings.setEnabled(calculator)
    }

    val unitConverter = unitConverterSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setUnitConverter(unitConverter: Boolean) {
        unitConverterSettings.setEnabled(unitConverter)
    }

    val wikipedia = wikipediaSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setWikipedia(wikipedia: Boolean) {
        wikipediaSearchSettings.setEnabled(wikipedia)
    }

    val websites = websiteSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setWebsites(websites: Boolean) {
        websiteSearchSettings.setEnabled(websites)
    }

    val autoFocus = searchUiSettings.openKeyboard

    fun setAutoFocus(autoFocus: Boolean) {
        searchUiSettings.setOpenKeyboard(autoFocus)
    }

    val launchOnEnter = searchUiSettings.launchOnEnter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLaunchOnEnter(launchOnEnter: Boolean) {
        searchUiSettings.setLaunchOnEnter(launchOnEnter)
    }

    val hasAppShortcutPermission = permissionsManager.hasPermission(PermissionGroup.AppShortcuts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val appShortcuts = shortcutSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setAppShortcuts(appShortcuts: Boolean) {
        shortcutSearchSettings.setEnabled(appShortcuts)
    }

    val reverseSearchResults = searchUiSettings.reversedResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setReverseSearchResults(reverseSearchResults: Boolean) {
        searchUiSettings.setReversedResults(reverseSearchResults)
    }

    fun requestAppShortcutsPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.AppShortcuts)
    }

    val filterBar = searchFilterSettings.filterBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFilterBar(filterBar: Boolean) {
        searchFilterSettings.setFilterBar(filterBar)
    }

    val searchFilters = searchFilterSettings.defaultFilter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SearchFilters())

    fun setSearchFilters(searchFilters: SearchFilters) {
        searchFilterSettings.setDefaultFilter(searchFilters)
    }

    val plugins = pluginService.getPluginsWithState(enabled = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val isTasksAppInstalled = appRepository.findOne("org.tasks", Process.myUserHandle())
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val hidePrivateProfile = searchUiSettings.hidePrivateProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setHidePrivateProfile(hidePrivateProfile: Boolean) {
        searchUiSettings.setHidePrivateProfile(hidePrivateProfile)
    }
}