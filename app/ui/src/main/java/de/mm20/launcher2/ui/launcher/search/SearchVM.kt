package de.mm20.launcher2.ui.launcher.search

import de.mm20.launcher2.search.GreekFold
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.search.FileSearchSettings
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.preferences.search.SearchFilterSettings
import de.mm20.launcher2.preferences.search.ShortcutSearchSettings
import de.mm20.launcher2.preferences.ui.AgentSettings
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.ui.agents.AgentLauncher
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.search.SearchResults
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.SettingsSearchItem
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.search.Website
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.search.isUnspecified
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.appmanagement.FossUpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val keyboardLaunchStore = ListItemViewModelStore()
    private val keyboardLauncher by lazy {
        keyboardLaunchStore.get<de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM>("keyboard-launch")
    }

    override fun onCleared() {
        keyboardLaunchStore.clear()
        super.onCleared()
    }

    private val favoritesService: FavoritesService by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val profileManager: ProfileManager by inject()

    private val performanceSettings: de.mm20.launcher2.preferences.ui.PerformanceSettings by inject()
    private val fileSearchSettings: FileSearchSettings by inject()
    private val contactSearchSettings: ContactSearchSettings by inject()
    private val calendarSearchSettings: CalendarSearchSettings by inject()
    private val shortcutSearchSettings: ShortcutSearchSettings by inject()
    private val searchUiSettings: SearchUiSettings by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()
    private val devicePoseProvider: DevicePoseProvider by inject()
    private val searchFilterSettings: SearchFilterSettings by inject()
    private val freezeSettings: de.mm20.launcher2.preferences.freeze.FreezeSettings by inject()
    private val fossUpdateRepository: de.mm20.launcher2.appmanagement.FossUpdateRepository by inject()
    private val agentSettings: AgentSettings by inject()

    val searchBarVoice = agentSettings.searchBarVoice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val agentsEnabled = agentSettings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val agentId = agentSettings.agentId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "grok")
    val agentTransport = agentSettings.transport
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")
    val agentsSendSentences = agentSettings.sendSentences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pendingUpdates = fossUpdateRepository.pendingUpdates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val launchOnEnter = searchUiSettings.launchOnEnter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val searchService: SearchService by inject()

    val searchQuery = mutableStateOf("")
    val isSearchEmpty = mutableStateOf(true)

    val expandedCategory = mutableStateOf<SearchCategory?>(null)

    val profiles = profileManager.profiles.shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        replay = 1
    )
    val profileStates = profiles.flatMapLatest {
        combine(it.map { profileManager.getProfileState(it) }) {
            it.toList()
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val visibleProfiles = combine(profiles, profileStates) { profs, states ->
        profs.filterIndexed { i, _ -> states.getOrNull(i)?.hidden != true }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

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

    val appResults = mutableStateListOf<Application>()
    val workAppResults = mutableStateListOf<Application>()
    val privateSpaceAppResults = mutableStateListOf<Application>()

    val appShortcutResults = mutableStateListOf<AppShortcut>()
    val fileResults = mutableStateListOf<File>()
    val documentResults = mutableStateListOf<File>()
    val imageResults = mutableStateListOf<File>()
    val videoResults = mutableStateListOf<File>()
    val musicResults = mutableStateListOf<File>()
    val contactResults = mutableStateListOf<Contact>()
    val calendarResults = mutableStateListOf<CalendarEvent>()
    val reminderResults = mutableStateListOf<CalendarEvent>()
    val articleResults = mutableStateListOf<Article>()
    val websiteResults = mutableStateListOf<Website>()
    val webAppShortcutResults = mutableStateListOf<WebAppShortcut>()
    val settingsResults = mutableStateListOf<SettingsSearchItem>()
    val calculatorResults = mutableStateListOf<Calculator>()
    /** Telos Notes that match the query. They come straight from the notes store, not from the search service. */
    val noteResults = mutableStateListOf<de.mm20.launcher2.ui.notes.Note>()
    private val notesStore: de.mm20.launcher2.ui.notes.NotesStore by inject()
    /** Favorite Telos Radio stations that match the query (only when enabled in the search settings and Telos Radio is not removed) */
    val radioResults = mutableStateListOf<de.mm20.launcher2.comms.model.RadioStation>()
    private val radioRepository: de.mm20.launcher2.comms.repository.RadioRepository by inject()
    private val commsSettings: de.mm20.launcher2.preferences.comms.CommsSettings by inject()
    private var radioJob: Job? = null
    /** Favorite, custom and recent TV channels that match the query (only when enabled and Telos Media is not removed) */
    val tvResults = mutableStateListOf<de.mm20.launcher2.comms.tv.TvChannel>()
    private val tvLibrary: de.mm20.launcher2.comms.tv.TvLibrary by inject()
    private val tvCatalog: de.mm20.launcher2.comms.tv.TvCatalog by inject()
    private val tvPlayer: de.mm20.launcher2.comms.tv.TvPlayerController by inject()
    private var tvJob: Job? = null

    fun playTvChannel(channel: de.mm20.launcher2.comms.tv.TvChannel) {
        tvPlayer.play(channel)
    }
    val unitConverterResults = mutableStateListOf<UnitConverter>()
    val searchActionResults = mutableStateListOf<SearchAction>()
    val locationResults = mutableStateListOf<Location>()
    val updateResults = mutableStateListOf<Application>()

    var previousResults: SearchResults? = null

    val hiddenResultsButton = searchUiSettings.hiddenItemsButton
    val hiddenResults = mutableStateListOf<SavableSearchable>()

    val favoritesEnabled = searchUiSettings.favorites
    val hideFavorites = mutableStateOf(false)

    val allAppsEnabled = searchUiSettings.allApps

    val showFilters = mutableStateOf(false)

    private val defaultFilters = searchFilterSettings.defaultFilter.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SearchFilters()
    )
    val filters = mutableStateOf(defaultFilters.value)
    val filterBar = searchFilterSettings.filterBar
    val filterBarItems = searchFilterSettings.filterBarItems

    val bestMatch = mutableStateOf<Searchable?>(null)

    val selectedAppProfileIndex = mutableIntStateOf(0)

    init {
        search("", forceRestart = true)

        /*
        * Handle clearing the search query when the user changes the private space
        * lock from the search action chip
        */
        viewModelScope.launch {
            var prevPrivateLocked: Boolean? = null
            combine(profiles, profileStates) { profiles, states -> profiles to states }
                .collect { (profiles, states) ->
                    val privateIdx = profiles.indexOfFirst { it.type == Profile.Type.Private }
                    val isLocked = states.getOrNull(privateIdx)?.locked
                    if (prevPrivateLocked != null && isLocked != null && prevPrivateLocked != isLocked && searchQuery.value.isNotEmpty()) {
                        search("")
                        if (!isLocked) selectedAppProfileIndex.intValue = privateIdx
                    }
                    prevPrivateLocked = isLocked
                }
        }
    }

    fun launchBestMatchOrAction(context: Context) {
        val query = searchQuery.value.trim()
        if (agentsEnabled.value && agentsSendSentences.value &&
            query.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 3
        ) {
            AgentLauncher.send(context, query, agentId.value, agentTransport.value)
            return
        }
        val bestMatch = bestMatch.value
        if (bestMatch is SavableSearchable) {
            keyboardLauncher.launchTarget(context, bestMatch)
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
        noteResults.clear()
        radioJob?.cancel()
        radioResults.clear()
        if (query.isNotBlank()) {
            radioJob = viewModelScope.launch {
                combine(
                    searchUiSettings.radioStationsInSearch,
                    commsSettings.disabledVirtualApps,
                    radioRepository.observeFavorites(),
                ) { enabled, disabled, favorites ->
                    if (enabled && "telos_radio_app://radio" !in disabled) {
                        de.mm20.launcher2.comms.search.TelosSearch.filter(favorites, query) {
                            listOf(it.name, it.homepage, it.streamContent)
                        }.take(8)
                    } else emptyList()
                }.collectLatest { radioResults.updateItems(it) }
            }
        }
        tvJob?.cancel()
        tvResults.clear()
        if (query.isNotBlank()) {
            tvJob = viewModelScope.launch {
                combine(
                    searchUiSettings.tvChannelsInSearch,
                    commsSettings.disabledVirtualApps,
                ) { enabled, disabled -> enabled && "telos_tv_app://tv" !in disabled }
                    .distinctUntilChanged()
                    .collectLatest { active ->
                        if (!active) {
                            tvResults.updateItems(emptyList())
                            return@collectLatest
                        }
                        // local storage only: never downloads the catalog
                        tvCatalog.loadCachedOnly()
                        combine(
                            tvLibrary.observeFavorites(),
                            tvLibrary.observeCustomChannels(),
                            tvLibrary.observeRecents(),
                        ) { favorites, custom, recents ->
                            val pool = (favorites + custom + recents).distinctBy { it.id }
                            de.mm20.launcher2.comms.search.TelosSearch
                                .filter(pool, query) { listOf(it.name) + it.altNames }.take(8)
                        }.collectLatest { tvResults.updateItems(it) }
                    }
            }
        }
        if (query.length >= 2 && this.filters.value.tools) {
            notesStore.load()
            val foldedQuery = GreekFold.fold(query)
            if (foldedQuery.isNotEmpty()) {
                noteResults.addAll(notesStore.notes.value.asSequence().filter {
                    !it.trashed && (GreekFold.matches(it.title, foldedQuery) || it.labels.any { l -> GreekFold.matches(l, foldedQuery) } || GreekFold.matches(it.body, foldedQuery))
                }.take(5).toList())
            }
        }

        val filters = filters.value

        if (filters.enabledCategories == 1) {
            expandedCategory.value = when {
                filters.apps -> SearchCategory.Apps
                filters.events -> SearchCategory.Calendar
                filters.reminders -> SearchCategory.Reminders
                filters.contacts -> SearchCategory.Contacts
                filters.files -> SearchCategory.Files
                filters.documents -> SearchCategory.Documents
                filters.images -> SearchCategory.Images
                filters.video -> SearchCategory.Video
                filters.music -> SearchCategory.Music
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
                val hiddenItemKeys = if (!filters.hiddenItems) {
                    searchableRepository.getKeys(
                        maxVisibility = VisibilityLevel.SearchOnly,
                        includeTypes = listOf("app"),
                    )
                } else {
                    flowOf(emptyList())
                }
                val allApps = searchService.getAllApps()

                allApps
                    .combine(hiddenItemKeys) { results, hiddenKeys -> results to hiddenKeys }
                    .combine(freezeSettings.hideFromLauncher) { (results, hiddenKeys), hideFrozen -> Triple(results, hiddenKeys, hideFrozen) }
                    // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
                    .combine(searchUiSettings.moveFrozenAppsToEnd) { (results, hiddenKeys, hideFrozen), moveFrozenToEnd -> 
                        Triple(results, hiddenKeys, Pair(hideFrozen, moveFrozenToEnd)) 
                    }
                    .collectLatest { (results, hiddenKeys, freezeFlags) ->
                        val hideFrozen = freezeFlags.first
                        val moveFrozenToEnd = freezeFlags.second
                        val hiddenItems = mutableListOf<SavableSearchable>()

                        // Frozen apps are dropped outright when hideFrozen is on, not routed into
                        // hiddenItems - that bucket is for the user's own hidden-items list (with
                        // its own "N hidden results" reveal UI), a different concept from "don't
                        // show this at all right now because it's frozen".
                        var (hiddenApps, apps) = results.standardProfileApps
                            .filterNot { hideFrozen && it.isSuspended }
                            .partition { hiddenKeys.contains(it.key) }
                        if (moveFrozenToEnd) {
                            apps = apps.sortedBy { it.isSuspended }
                        }
                        hiddenItems += hiddenApps

                        var (hiddenWorkApps, workApps) = results.workProfileApps
                            .filterNot { hideFrozen && it.isSuspended }
                            .partition { hiddenKeys.contains(it.key) }
                        if (moveFrozenToEnd) {
                            workApps = workApps.sortedBy { it.isSuspended }
                        }
                        hiddenItems += hiddenWorkApps

                        var (hiddenPrivateApps, privateApps) = results.privateSpaceApps
                            .filterNot { hideFrozen && it.isSuspended }
                            .partition { hiddenKeys.contains(it.key) }
                        if (moveFrozenToEnd) {
                            privateApps = privateApps.sortedBy { it.isSuspended }
                        }
                        hiddenItems += hiddenPrivateApps
                        // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
                        previousResults = SearchResults(apps = apps)

                        searchActionResults.clear()
                        appResults.updateItems(apps)
                        workAppResults.updateItems(workApps)
                        privateSpaceAppResults.updateItems(privateApps)
                        webAppShortcutResults.updateItems(results.webAppShortcuts?.distinctBy { it.key })
                        hiddenResults.updateItems(hiddenItems)

                        val updates = (apps + workApps + privateApps).filter { 
                            pendingUpdates.value.contains(it.componentName.packageName) 
                        }
                        updateResults.updateItems(updates)
                    }

            } else {
                val debounceMs = performanceSettings.searchDebounceMs.first()
                if (debounceMs > 0) delay(debounceMs.toLong())

                val hiddenItemKeys = if (!filters.hiddenItems) searchableRepository.getKeys(
                    maxVisibility = VisibilityLevel.Hidden,
                ) else flowOf(emptyList())
                searchService.search(
                    query,
                    filters = filters,
                    previousResults,
                )
                    .combine(hiddenItemKeys) { results, hiddenKeys -> results to hiddenKeys }
                    .collectLatest { (results, hiddenKeys) ->
                        previousResults = results

                        hiddenResults.clear()
                        workAppResults.clear()
                        privateSpaceAppResults.clear()

                        appResults.updateItems(
                            results.apps
                            ?.filterNot { hiddenKeys.contains(it.key) }
                            ?.applyRanking(query)
                        )
                        appShortcutResults.updateItems(
                            results.shortcuts
                            ?.filterNot { hiddenKeys.contains(it.key) }
                            ?.applyRanking(query)
                        )
                        fileResults.updateItems(
                            results.files
                            ?.filterNot { hiddenKeys.contains(it.key) }
                            ?.applyRanking(query)
                        )
                        documentResults.updateItems(
                            results.documents
                            ?.filterNot { hiddenKeys.contains(it.key) }
                            ?.applyRanking(query)
                        )
                        imageResults.updateItems(
                            results.images
                            ?.filterNot { hiddenKeys.contains(it.key) }
                            ?.applyRanking(query)
                        )
                        videoResults.updateItems(
                            results.video
                            ?.filterNot { hiddenKeys.contains(it.key) }
                            ?.applyRanking(query)
                        )
                        musicResults.updateItems(
                            results.music
                            ?.filterNot { hiddenKeys.contains(it.key) }
                            ?.applyRanking(query)
                        )

                        contactResults.updateItems(
                            results.contacts?.filterNot { hiddenKeys.contains(it.key) }
                                ?.applyRanking(query)
                        )
                        calendarResults.updateItems(
                            results.calendars?.filterNot { hiddenKeys.contains(it.key) }
                                ?.applyRanking(query)
                        )
                        reminderResults.updateItems(
                            results.reminders?.filterNot { hiddenKeys.contains(it.key) }
                                ?.applyRanking(query)
                        )
                        locationResults.updateItems(
                            results.locations?.filterNot { hiddenKeys.contains(it.key) }
                                ?.let { locations ->
                                    devicePoseProvider.lastCachedLocation?.let {
                                        locations.asSequence()
                                            .sortedWith { a, b ->
                                                a.distanceTo(it).compareTo(b.distanceTo(it))
                                            }
                                            .distinctBy { it.key }
                                            .toList()
                                    } ?: locations.applyRanking(query)
                                }
                        )
                        articleResults.updateItems(
                            results.wikipedia?.filterNot { hiddenKeys.contains(it.key) }?.applyRanking(query)
                        )
                        websiteResults.updateItems(
                            results.websites?.filterNot { hiddenKeys.contains(it.key) }?.applyRanking(query)
                        )
                        settingsResults.updateItems(results.settings)
                        webAppShortcutResults.updateItems(
                            results.webAppShortcuts?.filterNot { hiddenKeys.contains(it.key) }?.applyRanking(query)
                        )
                        calculatorResults.updateItems(results.calculators?.distinctBy { it.term to it.solution })
                        unitConverterResults.updateItems(results.unitConverters?.distinctBy { it.dimension to it.inputValue })

                        if (results.searchActions != null) {
                            searchActionResults.updateItems(results.searchActions!!)
                        }

                        if (launchOnEnter.value) {
                            bestMatch.value = when {
                                appResults.isNotEmpty() -> appResults.first()
                                appShortcutResults.isNotEmpty() -> appShortcutResults.first()
                                calendarResults.isNotEmpty() -> calendarResults.first()
                                reminderResults.isNotEmpty() -> reminderResults.first()
                                locationResults.isNotEmpty() -> locationResults.first()
                                contactResults.isNotEmpty() -> contactResults.first()
                                articleResults.isNotEmpty() -> articleResults.first()
                                websiteResults.isNotEmpty() -> websiteResults.first()
                                fileResults.isNotEmpty() -> fileResults.first()
                                documentResults.isNotEmpty() -> documentResults.first()
                                imageResults.isNotEmpty() -> imageResults.first()
                                videoResults.isNotEmpty() -> videoResults.first()
                                musicResults.isNotEmpty() -> musicResults.first()
                                searchActionResults.isNotEmpty() -> searchActionResults.first()
                                settingsResults.isNotEmpty() -> settingsResults.first()
                                else -> null
                            }
                        } else {
                            bestMatch.value = null
                        }
                    }
            }
        }
    }

    val missingCalendarPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.Calendar),
        calendarSearchSettings.providers,
    ) { perm, providers -> !perm && providers.contains("local") }

    fun requestCalendarPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Calendar)
    }

    fun disableCalendarSearch() {
        calendarSearchSettings.setProviderEnabled("local", false)
    }

    val missingContactsPermission = combine(
        permissionsManager.hasPermission(PermissionGroup.Contacts),
        contactSearchSettings.isProviderEnabled("local")
    ) { perm, enabled -> !perm && enabled }

    fun requestContactsPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Contacts)
    }

    fun disableContactsSearch() {
        contactSearchSettings.setProviderEnabled("local", false)
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

    fun dismissUpdate(packageName: String) {
        fossUpdateRepository.removeUpdate(packageName)
    }

    private suspend fun <T : SavableSearchable> List<T>.applyRanking(query: String): List<T> {
        if (size <= 1) return this
        val weights = searchableRepository.getWeights(map { it.key }).first()
        val keyed = map { item ->
            val keywords = when (item) {
                is de.mm20.launcher2.search.Application -> item.keywords + de.mm20.launcher2.search.TelosAppKeywords.forKey(item.key)
                is de.mm20.launcher2.search.SettingsSearchItem -> item.keywords
                else -> emptyList()
            }
            val primary = listOfNotNull(item.labelOverride, item.label)
            val computedScore = ResultScore.from(
                query = query,
                primaryFields = primary,
                secondaryFields = keywords
            ).score

            val score = if (!item.score.isUnspecified) {
                maxOf(item.score.score, computedScore)
            } else {
                computedScore
            }
            val weight = weights[item.key] ?: 0.0
            val normalizedWeight = if (weight > 0) (weight.toFloat() / (weight.toFloat() + 5.0f)) * 0.9f else 0.0f
            item to (score + normalizedWeight)
        }
        return keyed.sortedByDescending { it.second }.map { it.first }.distinctBy { it.key }
    }

    /**
     * Merges a list of new items into the current SnapshotStateList.
     * It removes items that are in the current list but not in the new list.
     * Then, it updates existing items or adds new items from the new list.
     *
     * @param T The type of items in the list.
     * @param newItems The list of new items to merge with. If null, an empty list is used.
     */
    private fun <T> SnapshotStateList<T>.updateItems(newItems: List<T>?) {
        clear()
        addAll(newItems ?: emptyList())
    }
}


enum class SearchCategory {
    Apps,
    Calculator,
    Calendar,
    Reminders,
    Contacts,
    Files,
    Documents,
    Images,
    Video,
    Music,
    UnitConverter,
    Articles,
    Website,
    Location,
    Shortcuts,
    Settings,
}
