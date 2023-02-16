package de.mm20.launcher2.ui.launcher.search

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.search.data.Website
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.searchactions.actions.SearchAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    private val searchService: SearchService by inject()

    val searchQuery = MutableLiveData("")
    val isSearchEmpty = MutableLiveData(true)

    val appResults = MutableLiveData<List<LauncherApp>>(emptyList())
    val workAppResults = MutableLiveData<List<LauncherApp>>(emptyList())
    val appShortcutResults = MutableLiveData<List<AppShortcut>>(emptyList())
    val fileResults = MutableLiveData<List<File>>(emptyList())
    val contactResults = MutableLiveData<List<Contact>>(emptyList())
    val calendarResults = MutableLiveData<List<CalendarEvent>>(emptyList())
    val wikipediaResults = MutableLiveData<List<Wikipedia>>(emptyList())
    val websiteResults = MutableLiveData<List<Website>>(emptyList())
    val calculatorResults = MutableLiveData<List<Calculator>>(emptyList())
    val unitConverterResults = MutableLiveData<List<UnitConverter>>(emptyList())
    val searchActionResults = MutableLiveData<List<SearchAction>>(emptyList())

    val hiddenResults = MutableLiveData<List<SavableSearchable>>(emptyList())

    val favoritesEnabled = dataStore.data.map { it.favorites.enabled }
    val hideFavorites = MutableLiveData(false)

    private val hiddenItemKeys = favoritesRepository
        .getHiddenItemKeys()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    init {
        search("", true)
    }

    fun launchBestMatch(context: Context) {
        if (!launchOnEnter)
            return

        suspend {
            searchJob?.join()
        }

        if (false == appResults.value?.first()?.launch(context, null))
        {
            searchActionResults.value?.first()?.start(context)
        }
    }

    private var searchJob: Job? = null
    fun search(query: String, forceRestart: Boolean = false) {
        if (searchQuery.value == query && !forceRestart) return
        searchQuery.value = query
        isSearchEmpty.value = query.isEmpty()
        hiddenResults.value = emptyList()

        try {
            searchJob?.cancel()
        } catch (_: CancellationException) {
        }
        hideFavorites.postValue(query.isNotEmpty())
        searchJob = viewModelScope.launch {

            dataStore.data.collectLatest {
                searchService.search(
                    query,
                    calculator = it.calculatorSearch,
                    unitConverter = it.unitConverterSearch,
                    calendars = it.calendarSearch,
                    contacts = it.contactsSearch,
                    files = it.fileSearch,
                    shortcuts = it.appShortcutSearch,
                    websites = it.websiteSearch,
                    wikipedia = it.wikipediaSearch,
                ).collectLatest { results ->
                    hiddenItemKeys.collectLatest { hiddenKeys ->
                        val hidden = mutableListOf<SavableSearchable>()
                        val apps = mutableListOf<LauncherApp>()
                        val workApps = mutableListOf<LauncherApp>()
                        val shortcuts = mutableListOf<AppShortcut>()
                        val files = mutableListOf<File>()
                        val contacts = mutableListOf<Contact>()
                        val events = mutableListOf<CalendarEvent>()
                        val unitConv = mutableListOf<UnitConverter>()
                        val calc = mutableListOf<Calculator>()
                        val wikipedia = mutableListOf<Wikipedia>()
                        val website = mutableListOf<Website>()
                        val actions = mutableListOf<SearchAction>()
                        for (r in results) {
                            when {
                                r is SavableSearchable && hiddenKeys.contains(r.key) -> {
                                    hidden.add(r)
                                }

                                r is LauncherApp && !r.isMainProfile -> workApps.add(r)
                                r is LauncherApp -> apps.add(r)
                                r is AppShortcut -> shortcuts.add(r)
                                r is File -> files.add(r)
                                r is Contact -> contacts.add(r)
                                r is CalendarEvent -> events.add(r)
                                r is UnitConverter -> unitConv.add(r)
                                r is Calculator -> calc.add(r)
                                r is Website -> website.add(r)
                                r is Wikipedia -> wikipedia.add(r)
                                r is SearchAction -> actions.add(r)
                            }
                        }
                        searchActionResults.value = actions
                        appResults.value = apps
                        workAppResults.value = workApps
                        appShortcutResults.value = shortcuts
                        fileResults.value = files
                        contactResults.value = contacts
                        calendarResults.value = events
                        wikipediaResults.value = wikipedia
                        websiteResults.value = website
                        calculatorResults.value = calc
                        unitConverterResults.value = unitConv
                        hiddenResults.value = hidden
                    }
                }
            }
        }
    }

    val missingCalendarPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.Calendar),
        dataStore.data.map { it.calendarSearch.enabled }.distinctUntilChanged()
    ) { perm, enabled -> !perm && enabled }

    fun requestCalendarPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Calendar)
    }

    fun disableCalendarSearch() {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setCalendarSearch(it.calendarSearch.toBuilder().setEnabled(false))
                    .build()
            }
        }
    }

    val launchOnEnter = true // todo make setting out of it

    val missingContactsPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.Contacts),
        dataStore.data.map { it.contactsSearch.enabled }.distinctUntilChanged()
    ) { perm, enabled -> !perm && enabled }

    fun requestContactsPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Contacts)
    }

    fun disableContactsSearch() {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setContactsSearch(it.contactsSearch.toBuilder().setEnabled(false))
                    .build()
            }
        }
    }

    val missingFilesPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.ExternalStorage),
        dataStore.data.map { it.fileSearch.localFiles }.distinctUntilChanged()
    ) { perm, enabled -> !perm && enabled }

    fun requestFilesPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.ExternalStorage)
    }

    fun disableFilesSearch() {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFileSearch(it.fileSearch.toBuilder().setLocalFiles(false))
                    .build()
            }
        }
    }

    val missingAppShortcutPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.AppShortcuts),
        dataStore.data.map { it.appShortcutSearch.enabled }.distinctUntilChanged()
    ) { perm, enabled -> !perm && enabled }

    fun requestAppShortcutPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.AppShortcuts)
    }

    fun disableAppShortcutSearch() {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppShortcutSearch(it.appShortcutSearch.toBuilder().setEnabled(false))
                    .build()
            }
        }
    }
}