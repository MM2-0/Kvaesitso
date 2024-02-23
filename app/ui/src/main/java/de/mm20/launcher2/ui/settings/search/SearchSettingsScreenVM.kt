package de.mm20.launcher2.ui.settings.search

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.openstreetmaps.settings.LocationSearchSettings
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.SearchResultOrder
import de.mm20.launcher2.preferences.search.CalculatorSearchSettings
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.search.ShortcutSearchSettings
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.preferences.search.WebsiteSearchSettings
import de.mm20.launcher2.preferences.search.WikipediaSearchSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val permissionsManager: PermissionsManager by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()

    val favorites = searchUiSettings.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFavorites(favorites: Boolean) {
        searchUiSettings.setFavorites(favorites)
    }


    val hasContactsPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val contacts = contactSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setContacts(contacts: Boolean) {
        contactSearchSettings.setEnabled(contacts)
    }

    fun requestContactsPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Contacts)
    }

    val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val calendar = calendarSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setCalendar(calendar: Boolean) {
        calendarSearchSettings.setEnabled(calendar)
    }

    fun requestCalendarPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Calendar)
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

    val locations = locationSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLocations(locations: Boolean) {
        locationSearchSettings.setEnabled(locations)
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

    val searchResultOrdering = searchUiSettings.resultOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setSearchResultOrdering(searchResultOrdering: SearchResultOrder) {
        searchUiSettings.setResultOrder(searchResultOrdering)
    }


    val reverseSearchResults = searchUiSettings.reversedResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setReverseSearchResults(reverseSearchResults: Boolean) {
        searchUiSettings.setReversedResults(reverseSearchResults)
    }

    fun requestAppShortcutsPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.AppShortcuts)
    }
}