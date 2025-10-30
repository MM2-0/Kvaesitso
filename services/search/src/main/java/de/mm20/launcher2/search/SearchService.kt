package de.mm20.launcher2.search

import android.util.Log
import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

interface SearchService {
    fun search(
        query: String,
        filters: SearchFilters,
        initialResults: SearchResults? = null,
    ): Flow<SearchResults>

    fun getAllApps(): Flow<AllAppsResults>
}

internal class SearchServiceImpl(
    private val appRepository: SearchableRepository<Application>,
    private val appShortcutRepository: SearchableRepository<AppShortcut>,
    private val calendarRepository: SearchableRepository<CalendarEvent>,
    private val contactRepository: SearchableRepository<Contact>,
    private val fileRepository: SearchableRepository<File>,
    private val articleRepository: SearchableRepository<Article>,
    private val locationRepository: SearchableRepository<Location>,
    private val unitConverterRepository: UnitConverterRepository,
    private val calculatorRepository: CalculatorRepository,
    private val websiteRepository: SearchableRepository<Website>,
    private val searchActionService: SearchActionService,
    private val customAttributesRepository: CustomAttributesRepository,
    private val profileManager: ProfileManager,
) : SearchService {

    override fun search(
        query: String,
        filters: SearchFilters,
        initialResults: SearchResults?,
    ): Flow<SearchResults> = flow {
        supervisorScope {
            val results = MutableStateFlow(initialResults ?: SearchResults())

            val customAttrResults = customAttributesRepository.search(query)
                .map { items ->
                    val apps = mutableListOf<Application>()
                    val shortcuts = mutableListOf<AppShortcut>()
                    val contacts = mutableListOf<Contact>()
                    val events = mutableListOf<CalendarEvent>()
                    val files = mutableListOf<File>()
                    val unitConverters = mutableListOf<UnitConverter>()
                    val websites = mutableListOf<Website>()
                    val wikipedia = mutableListOf<Article>()
                    val locations = mutableListOf<Location>()
                    val searchActions = mutableListOf<SearchAction>()
                    for (it in items) {
                        when (it) {
                            is Application -> apps.add(it)
                            is AppShortcut -> shortcuts.add(it)
                            is Contact -> contacts.add(it)
                            is CalendarEvent -> events.add(it)
                            is File -> files.add(it)
                            is UnitConverter -> unitConverters.add(it)
                            is Website -> websites.add(it)
                            is Article -> wikipedia.add(it)
                            is Location -> locations.add(it)
                            is SearchAction -> searchActions.add(it)
                        }
                    }
                    SearchResults(
                        apps = apps,
                        shortcuts = shortcuts,
                        contacts = contacts,
                        calendars = events,
                        files = files,
                        unitConverters = unitConverters,
                        websites = websites,
                        wikipedia = wikipedia,
                        locations = locations,
                        searchActions = searchActions,
                    )
                }.shareIn(this, SharingStarted.WhileSubscribed(), 1)

            launch {
                searchActionService.search(query)
                    .collectLatest { r ->
                        results.update {
                            it.copy(searchActions = r)
                        }
                    }
            }
            if (filters.enabledCategories == 0) {
                /**
                 * Apps
                 */
                launch {
                    appRepository.search(query)
                        .combine(customAttrResults) { apps, customAttrs ->
                            if (customAttrs.apps != null) apps + customAttrs.apps
                            else apps
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(apps = r)
                            }
                        }
                }

                /**
                 * Tools
                 */
                launch {
                    calculatorRepository.search(query).collectLatest { r ->
                        results.update {
                            it.copy(calculators = r?.let { listOf(it) }
                                ?: listOf())
                        }
                    }
                }
                launch {
                    unitConverterRepository.search(query)
                        .collectLatest { r ->
                            results.update {
                                it.copy(unitConverters = r?.let { listOf(it) }
                                    ?: listOf())
                            }
                        }
                }

                /**
                 * Shortcuts
                 */
                launch {
                    appShortcutRepository.search(query)
                        .combine(customAttrResults) { shortcuts, customAttrs ->
                            if (customAttrs.shortcuts != null) shortcuts + customAttrs.shortcuts
                            else shortcuts
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(shortcuts = r)
                            }
                        }
                }

                /**
                 * Contacts
                 */

                launch {
                    contactRepository.search(query)
                        .combine(customAttrResults) { contacts, customAttrs ->
                            if (customAttrs.contacts != null) contacts + customAttrs.contacts
                            else contacts
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(contacts = r)
                            }
                        }
                }
            } else {
                launch {
                    results.update {
                        it.copy(apps = null, unitConverters = null, calculators = null, shortcuts = null, contacts = null)
                    }
                }
            }

            if (filters.events) {
                launch {
                    calendarRepository.search(query)
                        .combine(customAttrResults) { calendars, customAttrs ->
                            if (customAttrs.calendars != null) calendars + customAttrs.calendars
                            else calendars
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(calendars = r)
                            }
                        }
                }
            } else {
                launch {
                    results.update {
                        it.copy(calendars = null)
                    }
                }
            }

            if (filters.websites) {
                launch {
                    websiteRepository.search(query)
                        .combine(customAttrResults) { websites, customAttrs ->
                            if (customAttrs.websites != null) websites + customAttrs.websites
                            else websites
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(websites = r)
                            }
                        }
                }
            } else {
                launch {
                    results.update {
                        it.copy(websites = null)
                    }
                }
            }
            if (filters.articles) {
                launch {
                    delay(750)
                    articleRepository.search(query)
                        .combine(customAttrResults) { articles, customAttrs ->
                            if (customAttrs.wikipedia != null) articles + customAttrs.wikipedia
                            else articles
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(wikipedia = r)
                            }
                        }
                }
            } else {
                launch {
                    results.update {
                        it.copy(wikipedia = null)
                    }
                }
            }

            if (filters.places) {
                launch {
                    locationRepository.search(query)
                        .combine(customAttrResults) { locations, customAttrs ->
                            if (customAttrs.locations != null) locations + customAttrs.locations
                            else locations
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(locations = r)
                            }
                        }
                }
            }  else {
                launch {
                    results.update {
                        it.copy(locations = null)
                    }
                }
            }
            if (filters.files) {
                launch {
                    fileRepository.search(query)
                        .combine(customAttrResults) { files, customAttrs ->
                            if (customAttrs.files != null) files + customAttrs.files
                            else files
                        }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(files = r)
                            }
                        }
                }
            } else {
                launch {
                    results.update {
                        it.copy(files = null)
                    }
                }
            }

            emitAll(results)
        }
    }

    override fun getAllApps(): Flow<AllAppsResults> {
        return profileManager.profiles.flatMapLatest { profiles ->
            val standardProfile = profiles.find { it.type == Profile.Type.Personal }
            val workProfile = profiles.find { it.type == Profile.Type.Work }
            val privateSpace = profiles.find { it.type == Profile.Type.Private }
            appRepository.search("")
                .withCustomLabels(customAttributesRepository)
                .map { apps ->
                    val standardProfileApps = mutableListOf<Application>()
                    val workProfileApps = mutableListOf<Application>()
                    val privateSpaceApps = mutableListOf<Application>()
                    for (app in apps) {
                        when {
                            standardProfile != null && app.user == standardProfile.userHandle -> standardProfileApps.add(
                                app
                            )

                            workProfile != null && app.user == workProfile.userHandle -> workProfileApps.add(
                                app
                            )

                            privateSpace != null && app.user == privateSpace.userHandle -> privateSpaceApps.add(
                                app
                            )

                            else -> {
                                Log.w(
                                    "MM20",
                                    "App ${app.label} does not belong to any known profile. Ignoring."
                                )
                            }
                        }
                    }

                    AllAppsResults(
                        standardProfileApps = standardProfileApps.sorted(),
                        workProfileApps = workProfileApps.sorted(),
                        privateSpaceApps = privateSpaceApps.sorted(),
                    )
                }
        }
    }
}

data class SearchResults(
    val apps: List<Application>? = null,
    val shortcuts: List<AppShortcut>? = null,
    val contacts: List<Contact>? = null,
    val calendars: List<CalendarEvent>? = null,
    val files: List<File>? = null,
    val calculators: List<Calculator>? = null,
    val unitConverters: List<UnitConverter>? = null,
    val websites: List<Website>? = null,
    val wikipedia: List<Article>? = null,
    val locations: List<Location>? = null,
    val searchActions: List<SearchAction>? = null,
)

data class AllAppsResults(
    val standardProfileApps: List<Application>,
    val workProfileApps: List<Application>,
    val privateSpaceApps: List<Application>,
)

fun SearchResults.toList(): List<Searchable> {
    return listOfNotNull(
        apps,
        shortcuts,
        contacts,
        calendars,
        files,
        calculators,
        unitConverters,
        websites,
        wikipedia,
        searchActions,
    ).flatten()
}