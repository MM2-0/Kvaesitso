package de.mm20.launcher2.ui.settings.search

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.openstreetmaps.settings.LocationSearchSettings
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()

    val favorites = dataStore.data.map { it.favorites.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFavorites(favorites: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setFavorites(
                    it.favorites.toBuilder().setEnabled(favorites)
                ).build()
            }
        }
    }


    val hasContactsPermission = permissionsManager.hasPermission(PermissionGroup.Contacts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val contacts = dataStore.data.map { it.contactsSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setContacts(contacts: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setContactsSearch(
                    it.contactsSearch.toBuilder().setEnabled(contacts)
                ).build()
            }
        }
    }

    fun requestContactsPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Contacts)
    }

    val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val calendar = dataStore.data.map { it.calendarSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setCalendar(calendar: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setCalendarSearch(
                    it.calendarSearch.toBuilder().setEnabled(calendar)
                ).build()
            }
        }
    }

    fun requestCalendarPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Calendar)
    }

    val calculator = dataStore.data.map { it.calculatorSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setCalculator(calculator: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setCalculatorSearch(
                    it.calculatorSearch.toBuilder().setEnabled(calculator)
                ).build()
            }
        }
    }

    val unitConverter = dataStore.data.map { it.unitConverterSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setUnitConverter(unitConverter: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setUnitConverterSearch(
                    it.unitConverterSearch.toBuilder().setEnabled(unitConverter)
                ).build()
            }
        }
    }

    val wikipedia = dataStore.data.map { it.wikipediaSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setWikipedia(wikipedia: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setWikipediaSearch(
                    it.wikipediaSearch.toBuilder().setEnabled(wikipedia)
                ).build()
            }
        }
    }

    val websites = dataStore.data.map { it.websiteSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setWebsites(websites: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setWebsiteSearch(
                    it.websiteSearch.toBuilder().setEnabled(websites)
                ).build()
            }
        }
    }

    val locations = locationSearchSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLocations(locations: Boolean) {
        locationSearchSettings.setEnabled(locations)
    }

    val autoFocus = dataStore.data.map { it.searchBar.autoFocus }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setAutoFocus(autoFocus: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setSearchBar(
                    it.searchBar.toBuilder().setAutoFocus(autoFocus)
                ).build()
            }
        }
    }

    val launchOnEnter = dataStore.data.map { it.searchBar.launchOnEnter }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLaunchOnEnter(launchOnEnter: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setSearchBar(
                    it.searchBar.toBuilder().setLaunchOnEnter(launchOnEnter)
                ).build()
            }
        }
    }

    val hasAppShortcutPermission = permissionsManager.hasPermission(PermissionGroup.AppShortcuts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val appShortcuts = dataStore.data.map { it.appShortcutSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setAppShortcuts(appShortcuts: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setAppShortcutSearch(
                    it.appShortcutSearch.toBuilder().setEnabled(appShortcuts)
                ).build()
            }
        }
    }

    val searchResultOrdering = dataStore.data.map { it.resultOrdering.ordering }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setSearchResultOrdering(searchResultOrdering: Settings.SearchResultOrderingSettings.Ordering) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setResultOrdering(
                    it.resultOrdering.toBuilder().setOrdering(searchResultOrdering)
                ).build()
            }
        }
    }


    val reverseSearchResults = dataStore.data.map { it.layout.reverseSearchResults }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setReverseSearchResults(reverseSearchResults: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLayout(it.layout.toBuilder().setReverseSearchResults(reverseSearchResults))
                    .build()
            }
        }
    }

    fun requestAppShortcutsPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.AppShortcuts)
    }
}