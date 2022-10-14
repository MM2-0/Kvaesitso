package de.mm20.launcher2.ui.launcher.search

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    private val searchService: SearchService by inject()

    val isSearching = MutableLiveData(false)
    val searchQuery = MutableLiveData("")
    val isSearchEmpty = MutableLiveData(true)

    val showLabels = dataStore.data.map { it.grid.showLabels }.asLiveData()

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
    val websearchResults = MutableLiveData<List<Websearch>>(emptyList())

    val hiddenResults = MutableLiveData<List<PinnableSearchable>>(emptyList())

    val favoritesEnabled = dataStore.data.map { it.favorites.enabled }
    val hideFavorites = MutableLiveData(false)

    private val hiddenItemKeys = favoritesRepository
        .getHiddenItemKeys()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    init {
        search("")
    }

    var searchJob: Job? = null
    fun search(query: String) {
        searchQuery.value = query
        isSearchEmpty.value = query.isEmpty()
        hiddenResults.value = emptyList()

        try {
            searchJob?.cancel()
        } catch (_: CancellationException) {
        }
        hideFavorites.postValue(query.isNotEmpty())
        searchJob = viewModelScope.launch {
            isSearching.postValue(true)

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
                        val hidden = mutableListOf<PinnableSearchable>()
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
                        for (r in results) {
                            when {
                                r is PinnableSearchable && hiddenKeys.contains(r.key) -> {
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
                            }
                        }
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


            isSearching.postValue(false)
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


    private inline fun <reified T : PinnableSearchable> Flow<List<T>>.withCustomAttributeResults(
        customAttributeResults: Flow<List<PinnableSearchable>>
    ): Flow<List<T>> {
        return this.combine(customAttributeResults) { items, items2 ->
            (items + items2.filterIsInstance<T>()).distinctBy { it.key }
        }
    }

    private suspend fun <T : PinnableSearchable> Flow<List<T>>.collectWithHiddenItems(
        hiddenItemKeys: Flow<List<String>>,
        action: (items: List<T>, hidden: List<T>) -> Unit
    ) {
        return collectLatest { items ->
            hiddenItemKeys.collectLatest { hiddenKeys ->
                val (results, hidden) = items.partition { !hiddenKeys.contains(it.key) }
                action(results, hidden)
            }
        }
    }

    private fun <T : PinnableSearchable> Flow<List<T>>.sorted(): Flow<List<T>> = this.map { it.sorted() }

}

private data class HiddenItemResults(
    val apps: List<LauncherApp> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val files: List<File> = emptyList(),
    val appShortcuts: List<AppShortcut> = emptyList(),
) {
    fun joinToList(): List<PinnableSearchable> {
        return apps + contacts + calendarEvents + files + appShortcuts
    }
}