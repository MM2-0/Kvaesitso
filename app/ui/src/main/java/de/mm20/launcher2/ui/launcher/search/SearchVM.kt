package de.mm20.launcher2.ui.launcher.search

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.favorites.SavedSearchableRankInfo
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchResultOrdering
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.Searchable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    val launchOnEnter = dataStore.data.map { it.searchBar.launchOnEnter }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val searchService: SearchService by inject()

    val searchQuery = mutableStateOf("")
    val isSearchEmpty = mutableStateOf(true)

    val appResults = mutableStateOf<List<LauncherApp>>(emptyList())
    val workAppResults = mutableStateOf<List<LauncherApp>>(emptyList())
    val appShortcutResults = mutableStateOf<List<AppShortcut>>(emptyList())
    val fileResults = mutableStateOf<List<File>>(emptyList())
    val contactResults = mutableStateOf<List<Contact>>(emptyList())
    val calendarResults = mutableStateOf<List<CalendarEvent>>(emptyList())
    val wikipediaResults = mutableStateOf<List<Wikipedia>>(emptyList())
    val websiteResults = mutableStateOf<List<Website>>(emptyList())
    val calculatorResults = mutableStateOf<List<Calculator>>(emptyList())
    val unitConverterResults = mutableStateOf<List<UnitConverter>>(emptyList())
    val searchActionResults = mutableStateOf<List<SearchAction>>(emptyList())

    val hiddenResults = mutableStateOf<List<SavableSearchable>>(emptyList())

    val favoritesEnabled = dataStore.data.map { it.favorites.enabled }
    val hideFavorites = mutableStateOf(false)

    private val hiddenItemKeys = favoritesRepository
        .getHiddenItemKeys()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val bestMatch = mutableStateOf<Searchable?>(null)

    init {
        search("", true)
    }

    fun launchBestMatchOrAction(context: Context) {
        val bestMatch = bestMatch.value
        if (bestMatch is SavableSearchable) {
            bestMatch.launch(context, null)
            return
        } else if (bestMatch is SearchAction) {
            bestMatch.start(context)
            return
        }
    }

    private var searchJob: Job? = null
    fun search(query: String, forceRestart: Boolean = false) {
        if (searchQuery.value == query && !forceRestart) return
        searchQuery.value = query
        isSearchEmpty.value = query.isEmpty()
        hiddenResults.value = emptyList()
        bestMatch.value = null

        try {
            searchJob?.cancel()
        } catch (_: CancellationException) {
        }
        hideFavorites.value = query.isNotEmpty()
        searchJob = viewModelScope.launch {
            dataStore.data.collectLatest { settings ->
                searchService.search(
                    query,
                    calculator = settings.calculatorSearch,
                    unitConverter = settings.unitConverterSearch,
                    calendars = settings.calendarSearch,
                    contacts = settings.contactsSearch,
                    files = settings.fileSearch,
                    shortcuts = settings.appShortcutSearch,
                    websites = settings.websiteSearch,
                    wikipedia = settings.wikipediaSearch,
                ).collectLatest { results ->
                    var resultsList = withContext(Dispatchers.Default) {
                        listOfNotNull(
                            results.apps,
                            results.other,
                            results.shortcuts,
                            results.files,
                            results.contacts,
                            results.calendars,
                            results.wikipedia,
                            results.websites,
                            results.calculators,
                            results.unitConverters,
                            results.searchActions,
                        ).flatten()
                            .distinctBy { if (it is SavableSearchable) it.key else it }
                            .sortedBy { (it as? SavableSearchable) }
                    }

                    val relevance =
                        if (query.isEmpty()) {
                            emptyList()
                        } else {
                            val keys = resultsList.mapNotNull { (it as? SavableSearchable)?.key }
                            when (settings.searchBar.searchResultOrdering) {

                                SearchResultOrdering.TotalLaunchCount -> favoritesRepository.sortByRelevance(
                                    keys
                                ).first()

                                SearchResultOrdering.RecentLaunchCount -> favoritesRepository.sortByRelevance(
                                    keys, settings.searchBar.recentLaunchCountTimespanMs
                                ).first()

                                SearchResultOrdering.Weighted -> favoritesRepository.sortByWeight(
                                    keys
                                ).first()

                                else -> emptyList()
                            }
                        }

                    resultsList = resultsList.sortedWith { a, b ->
                        when {
                            a is SavableSearchable && b !is SavableSearchable -> -1
                            a !is SavableSearchable && b is SavableSearchable -> 1
                            a is SavableSearchable && b is SavableSearchable -> {
                                val aKey = a.key
                                val bKey = b.key
                                val aRank = relevance.indexOf(aKey)
                                val bRank = relevance.indexOf(bKey)
                                when {
                                    aRank != -1 && bRank != -1 -> aRank.compareTo(bRank)
                                    aRank == -1 && bRank != -1 -> 1
                                    aRank != -1 && bRank == -1 -> -1
                                    else -> a.compareTo(b)
                                }
                            }

                            else -> 0
                        }
                    }


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
                        for (r in resultsList) {
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

                        if (query.isNotEmpty() && launchOnEnter.value) {
                            bestMatch.value = listOf(
                                apps,
                                workApps,
                                shortcuts,
                                unitConv,
                                calc,
                                events,
                                contacts,
                                wikipedia,
                                website,
                                files,
                                actions
                            ).firstNotNullOfOrNull { it.firstOrNull() }
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
                        if (results.searchActions != null) searchActionResults.value = actions
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

    private fun <T : SavableSearchable> MutableList<T>.reorderByRanks(ranks: List<SavedSearchableRankInfo>) {
        if (this.size < 2) // one element does not need reordering
            return

        var i = 0

        for (item in ranks) {
            val idx = this.indexOfFirst { it.key == item.key }
            if (idx == -1) continue

            this.add(i++, this.removeAt(idx))

            if (i >= this.size) break
        }
    }
}