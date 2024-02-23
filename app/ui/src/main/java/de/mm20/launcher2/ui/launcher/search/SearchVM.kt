package de.mm20.launcher2.ui.launcher.search

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.openstreetmaps.settings.LocationSearchSettings
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.SearchResultOrder
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.search.FavoritesSettings
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.preferences.search.ShortcutSearchSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.search.AppProfile
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.Website
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.services.favorites.FavoritesService
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

    private val favoritesService: FavoritesService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    private val fileSearchSettings: FileSearchSettings by inject()
    private val contactSearchSettings: ContactSearchSettings by inject()
    private val calendarSearchSettings: CalendarSearchSettings by inject()
    private val shortcutSearchSettings: ShortcutSearchSettings by inject()
    private val searchUiSettings: SearchUiSettings by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()
    private val devicePoseProvider: DevicePoseProvider by inject()

    val launchOnEnter = searchUiSettings.launchOnEnter
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val searchService: SearchService by inject()

    val searchQuery = mutableStateOf("")
    val isSearchEmpty = mutableStateOf(true)

    val locationResults = mutableStateOf<List<Location>>(emptyList())
    val appResults = mutableStateOf<List<Application>>(emptyList())
    val workAppResults = mutableStateOf<List<Application>>(emptyList())
    val appShortcutResults = mutableStateOf<List<AppShortcut>>(emptyList())
    val fileResults = mutableStateOf<List<File>>(emptyList())
    val contactResults = mutableStateOf<List<Contact>>(emptyList())
    val calendarResults = mutableStateOf<List<CalendarEvent>>(emptyList())
    val articleResults = mutableStateOf<List<Article>>(emptyList())
    val websiteResults = mutableStateOf<List<Website>>(emptyList())
    val calculatorResults = mutableStateOf<List<Calculator>>(emptyList())
    val unitConverterResults = mutableStateOf<List<UnitConverter>>(emptyList())
    val searchActionResults = mutableStateOf<List<SearchAction>>(emptyList())

    val hiddenResultsButton = searchUiSettings.hiddenItemsButton
    val hiddenResults = mutableStateOf<List<SavableSearchable>>(emptyList())

    val favoritesEnabled = searchUiSettings.favorites
    val hideFavorites = mutableStateOf(false)

    private val hiddenItemKeys = searchableRepository
        .getKeys(
            hidden = true,
            limit = 9999,
        )
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val bestMatch = mutableStateOf<Searchable?>(null)

    init {
        search("", forceRestart = true)
    }

    fun launchBestMatchOrAction(context: Context) {
        val bestMatch = bestMatch.value
        if (bestMatch is SavableSearchable) {
            bestMatch.launch(context, null)
            favoritesService.reportLaunch(bestMatch)
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

        if (isSearchEmpty.value)
            bestMatch.value = null

        try {
            searchJob?.cancel()
        } catch (_: CancellationException) {
        }
        hideFavorites.value = query.isNotEmpty()
        searchJob = viewModelScope.launch {
            searchUiSettings.resultOrder.collectLatest { resultOrder ->
                searchService.search(
                    query,
                    allowNetwork = true,
                ).collectLatest { results ->
                    var resultsList = withContext(Dispatchers.Default) {
                        listOfNotNull(
                            results.apps,
                            results.other,
                            results.shortcuts,
                            results.files,
                            results.contacts,
                            results.calendars,
                            results.locations,
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
                            when (resultOrder) {

                                SearchResultOrder.LaunchCount -> searchableRepository.sortByRelevance(
                                    keys
                                ).first()

                                SearchResultOrder.Weighted -> searchableRepository.sortByWeight(
                                    keys
                                ).first()

                                else -> emptyList()
                            }
                        }

                    resultsList = resultsList.sortedWith { a, b ->
                        when {
                            a is Location && b is Location && devicePoseProvider.lastLocation != null -> {
                                a.distanceTo(devicePoseProvider.lastLocation!!)
                                    .compareTo(b.distanceTo(devicePoseProvider.lastLocation!!))
                            }

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
                        val apps = mutableListOf<Application>()
                        val workApps = mutableListOf<Application>()
                        val shortcuts = mutableListOf<AppShortcut>()
                        val files = mutableListOf<File>()
                        val contacts = mutableListOf<Contact>()
                        val events = mutableListOf<CalendarEvent>()
                        val unitConv = mutableListOf<UnitConverter>()
                        val calc = mutableListOf<Calculator>()
                        val articles = mutableListOf<Article>()
                        val locations = mutableListOf<Location>()
                        val website = mutableListOf<Website>()
                        val actions = mutableListOf<SearchAction>()
                        for (r in resultsList) {
                            when {
                                r is SavableSearchable && hiddenKeys.contains(r.key) -> {
                                    hidden.add(r)
                                }

                                r is Application && r.profile == AppProfile.Work -> workApps.add(r)
                                r is Application -> apps.add(r)
                                r is AppShortcut -> shortcuts.add(r)
                                r is File -> files.add(r)
                                r is Contact -> contacts.add(r)
                                r is CalendarEvent -> events.add(r)
                                r is UnitConverter -> unitConv.add(r)
                                r is Calculator -> calc.add(r)
                                r is Website -> website.add(r)
                                r is Article -> articles.add(r)
                                r is Location -> locations.add(r)
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
                                locations,
                                contacts,
                                articles,
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
                        articleResults.value = articles
                        locationResults.value = locations
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
        calendarSearchSettings.enabled,
    ) { perm, enabled -> !perm && enabled }

    fun requestCalendarPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Calendar)
    }

    fun disableCalendarSearch() {
        calendarSearchSettings.setEnabled(false)
    }

    val missingContactsPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.Contacts),
        contactSearchSettings.enabled
    ) { perm, enabled -> !perm && enabled }

    fun requestContactsPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Contacts)
    }

    fun disableContactsSearch() {
        contactSearchSettings.setEnabled(false)
    }

    val missingLocationPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.Location),
        locationSearchSettings.enabled.distinctUntilChanged()
    ) { perm, enabled -> !perm && enabled }

    fun requestLocationPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Location)
    }

    fun disableLocationSearch() {
        locationSearchSettings.setEnabled(false)
    }

    val missingFilesPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.ExternalStorage),
        fileSearchSettings.localFiles
    ) { perm, enabled -> !perm && enabled }

    fun requestFilesPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.ExternalStorage)
    }

    fun disableFilesSearch() {
        fileSearchSettings.setLocalFiles(false)
    }

    val missingAppShortcutPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.AppShortcuts),
        shortcutSearchSettings.enabled,
    ) { perm, enabled -> !perm && enabled }

    fun requestAppShortcutPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.AppShortcuts)
    }

    fun disableAppShortcutSearch() {
        shortcutSearchSettings.setEnabled(false)
    }
}