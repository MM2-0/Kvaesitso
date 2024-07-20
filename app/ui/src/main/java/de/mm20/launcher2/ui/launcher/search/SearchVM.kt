package de.mm20.launcher2.ui.launcher.search

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.SearchResultOrder
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.preferences.search.SearchFilterSettings
import de.mm20.launcher2.preferences.search.ShortcutSearchSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.Website
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val favoritesService: FavoritesService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val profileManager: ProfileManager by inject()

    private val fileSearchSettings: FileSearchSettings by inject()
    private val contactSearchSettings: ContactSearchSettings by inject()
    private val calendarSearchSettings: CalendarSearchSettings by inject()
    private val shortcutSearchSettings: ShortcutSearchSettings by inject()
    private val searchUiSettings: SearchUiSettings by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()
    private val devicePoseProvider: DevicePoseProvider by inject()
    private val searchFilterSettings: SearchFilterSettings by inject()

    val launchOnEnter = searchUiSettings.launchOnEnter
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val searchService: SearchService by inject()

    val searchQuery = mutableStateOf("")
    val isSearchEmpty = mutableStateOf(true)

    val expandedCategory = mutableStateOf<SearchCategory?>(null)

    val locationResults = mutableStateOf<List<Location>>(emptyList())

    val profiles = profileManager.profiles.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)
    val workProfile = profiles.map {
        it.find { it.type == Profile.Type.Work }
    }
    val privateProfile = profiles.map {
        it.find { it.type == Profile.Type.Private }
    }
    val workProfileState = workProfile.flatMapLatest {
        profileManager.getProfileState(it)
    }
    val privateProfileState = privateProfile.flatMapLatest {
        profileManager.getProfileState(it)
    }

    val hasProfilesPermission = permissionsManager.hasPermission(PermissionGroup.ManageProfiles)

    fun setProfileLock(profile: Profile?, locked: Boolean) {
        if (isAtLeastApiLevel(28) && profile != null) {
            if (locked) {
                profileManager.lockProfile(profile)
            } else {
                profileManager.unlockProfile(profile)
            }
        }
    }

    val appResults = mutableStateOf<List<Application>>(emptyList())
    val workAppResults = mutableStateOf<List<Application>>(emptyList())
    val privateSpaceAppResults = mutableStateOf<List<Application>>(emptyList())

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

    val showFilters = mutableStateOf(false)

    private val defaultFilters = searchFilterSettings.defaultFilter.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SearchFilters()
    )
    val filters = mutableStateOf(defaultFilters.value)
    val filterBar = searchFilterSettings.filterBar
    val filterBarItems = searchFilterSettings.filterBarItems

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

    fun setFilters(filters: SearchFilters) {
        this.filters.value = filters
        search(searchQuery.value, forceRestart = true)
    }

    fun closeFilters() {
        showFilters.value = false
    }

    fun reset() {
        closeFilters()
        filters.value = defaultFilters.value
        search("")
    }

    private var searchJob: Job? = null
    fun search(query: String, forceRestart: Boolean = false) {
        if (searchQuery.value == query && !forceRestart) return
        if (searchQuery.value != query) {
            showFilters.value = false
        }
        if (query.isEmpty() && searchQuery.value.isNotEmpty()) {
            filters.value = defaultFilters.value
        }
        searchQuery.value = query
        isSearchEmpty.value = query.isEmpty()
        hiddenResults.value = emptyList()

        val filters = filters.value

        if (filters.enabledCategories == 1) {
            expandedCategory.value = when {
                filters.apps -> SearchCategory.Apps
                filters.events -> SearchCategory.Calendar
                filters.contacts -> SearchCategory.Contacts
                filters.files -> SearchCategory.Files
                filters.websites -> SearchCategory.Website
                filters.articles -> SearchCategory.Articles
                filters.places -> SearchCategory.Location
                filters.shortcuts -> SearchCategory.Shortcuts
                else -> null
            }
        } else {
            expandedCategory.value = null
        }

        if (isSearchEmpty.value)
            bestMatch.value = null
        try {
            searchJob?.cancel()
        } catch (_: CancellationException) {
        }
        hideFavorites.value = query.isNotEmpty()

        searchJob = viewModelScope.launch {
            if (query.isEmpty()) {
                val hiddenItemKeys = searchableRepository.getKeys(
                    maxVisibility = VisibilityLevel.SearchOnly,
                    includeTypes = listOf("app"),
                )
                val allApps = searchService.getAllApps()
                fileResults.value = emptyList()
                contactResults.value = emptyList()
                calendarResults.value = emptyList()
                locationResults.value = emptyList()
                articleResults.value = emptyList()
                websiteResults.value = emptyList()
                calculatorResults.value = emptyList()
                unitConverterResults.value = emptyList()
                searchActionResults.value = emptyList()

                allApps
                    .combine(hiddenItemKeys) { results, hiddenKeys -> results to hiddenKeys }
                    .collectLatest { (results, hiddenKeys) ->
                        val hiddenItems = mutableListOf<SavableSearchable>()

                        val (hiddenApps, apps) = results.standardProfileApps.partition {
                            hiddenKeys.contains(
                                it.key
                            )
                        }
                        hiddenItems += hiddenApps

                        val (hiddenWorkApps, workApps) = results.workProfileApps.partition {
                            hiddenKeys.contains(
                                it.key
                            )
                        }
                        hiddenItems += hiddenWorkApps

                        val (hiddenPrivateApps, privateApps) = results.privateSpaceApps.partition {
                            hiddenKeys.contains(
                                it.key
                            )
                        }
                        hiddenItems += hiddenPrivateApps

                        appResults.value = apps
                        workAppResults.value = workApps
                        privateSpaceAppResults.value = privateApps
                        hiddenResults.value = hiddenItems
                    }

            } else {
                val hiddenItemKeys = searchableRepository.getKeys(
                    maxVisibility = VisibilityLevel.Hidden,
                )
                searchUiSettings.resultOrder.collectLatest { resultOrder ->
                    searchService.search(
                        query,
                        filters = if (query.isEmpty()) filters.copy(apps = true) else filters,
                    )
                        .combine(hiddenItemKeys) { results, hiddenKeys -> results to hiddenKeys }
                        .collectLatest { (results, hiddenKeys) ->
                            val hiddenItems = mutableListOf<SavableSearchable>()

                            if (results.apps != null) {
                                val (hiddenApps, apps) = results.apps!!.partition {
                                    hiddenKeys.contains(
                                        it.key
                                    )
                                }
                                hiddenItems += hiddenApps
                                appResults.value = apps.applyRanking(resultOrder)
                            } else {
                                appResults.value = emptyList()
                            }
                            workAppResults.value = emptyList()
                            privateSpaceAppResults.value = emptyList()

                            if (results.shortcuts != null) {
                                val (hiddenShortcuts, shortcuts) = results.shortcuts!!.partition {
                                    hiddenKeys.contains(
                                        it.key
                                    )
                                }
                                hiddenItems += hiddenShortcuts
                                appShortcutResults.value = shortcuts.applyRanking(resultOrder)
                            } else {
                                appShortcutResults.value = emptyList()
                            }

                            if (results.files != null) {
                                val (hiddenFiles, files) = results.files!!.partition {
                                    hiddenKeys.contains(
                                        it.key
                                    )
                                }
                                hiddenItems += hiddenFiles
                                fileResults.value = files.applyRanking(resultOrder)
                            } else {
                                fileResults.value = emptyList()
                            }

                            if (results.contacts != null) {
                                val (hiddenContacts, contacts) = results.contacts!!.partition {
                                    hiddenKeys.contains(
                                        it.key
                                    )
                                }
                                hiddenItems += hiddenContacts
                                contactResults.value = contacts.applyRanking(resultOrder)
                            } else {
                                contactResults.value = emptyList()
                            }

                            if (results.calendars != null) {
                                val (hiddenEvents, events) = results.calendars!!.partition {
                                    hiddenKeys.contains(
                                        it.key
                                    )
                                }
                                hiddenItems += hiddenEvents
                                calendarResults.value = events.applyRanking(resultOrder)
                            } else {
                                calendarResults.value = emptyList()
                            }

                            if (results.locations != null && results.locations!!.isNotEmpty()) {
                                val (hiddenLocations, locations) = results.locations!!.partition {
                                    hiddenKeys.contains(
                                        it.key
                                    )
                                }
                                hiddenItems += hiddenLocations
                                val lastLocation = devicePoseProvider.lastLocation
                                if (lastLocation != null) {
                                    locationResults.value = locations.asSequence()
                                        .sortedWith { a, b ->
                                            a.distanceTo(lastLocation)
                                                .compareTo(b.distanceTo(lastLocation))
                                        }
                                        .distinct()
                                        .toList()
                                } else {
                                    locationResults.value = locations.applyRanking(resultOrder)
                                }
                            } else {
                                locationResults.value = emptyList()
                            }

                            if (results.wikipedia != null) {
                                articleResults.value = results.wikipedia!!.applyRanking(resultOrder)
                            } else {
                                articleResults.value = emptyList()
                            }

                            if (results.websites != null) {
                                websiteResults.value = results.websites!!.applyRanking(resultOrder)
                            } else {
                                websiteResults.value = emptyList()
                            }


                            calculatorResults.value = results.calculators ?: emptyList()
                            unitConverterResults.value = results.unitConverters ?: emptyList()

                            if (results.searchActions != null) {
                                searchActionResults.value = results.searchActions!!
                            }

                            if (launchOnEnter.value) {
                                bestMatch.value = when {
                                    appResults.value.isNotEmpty() -> appResults.value.first()
                                    appShortcutResults.value.isNotEmpty() -> appShortcutResults.value.first()
                                    calendarResults.value.isNotEmpty() -> calendarResults.value.first()
                                    locationResults.value.isNotEmpty() -> locationResults.value.first()
                                    contactResults.value.isNotEmpty() -> contactResults.value.first()
                                    articleResults.value.isNotEmpty() -> articleResults.value.first()
                                    websiteResults.value.isNotEmpty() -> websiteResults.value.first()
                                    fileResults.value.isNotEmpty() -> fileResults.value.first()
                                    searchActionResults.value.isNotEmpty() -> searchActionResults.value.first()
                                    else -> null
                                }
                            } else {
                                bestMatch.value = null
                            }
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
        locationSearchSettings.osmLocations.distinctUntilChanged()
    ) { perm, enabled -> !perm && enabled }

    fun requestLocationPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Location)
    }

    fun disableLocationSearch() {
        locationSearchSettings.setOsmLocations(false)
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

    fun expandCategory(category: SearchCategory) {
        expandedCategory.value = category
    }

    private suspend fun <T : SavableSearchable> List<T>.applyRanking(order: SearchResultOrder): List<T> {
        if (size <= 1) return this
        val sequence = asSequence()
        val sorted = if (order == SearchResultOrder.Weighted) {
            val sortedKeys = searchableRepository.sortByWeight(map { it.key }).first()
            sequence.sortedWith { a, b ->
                val aRank = sortedKeys.indexOf(a.key)
                val bRank = sortedKeys.indexOf(b.key)
                when {
                    aRank != -1 && bRank != -1 -> aRank.compareTo(bRank)
                    aRank == -1 && bRank != -1 -> 1
                    aRank != -1 && bRank == -1 -> -1
                    else -> a.compareTo(b)
                }
            }
        } else {
            sequence.sorted()
        }
        return sorted.distinct().toList()
    }
}


enum class SearchCategory {
    Apps,
    Calculator,
    Calendar,
    Contacts,
    Files,
    UnitConverter,
    Articles,
    Website,
    Location,
    Shortcuts,
}