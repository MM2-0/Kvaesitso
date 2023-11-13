package de.mm20.launcher2.search

import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.preferences.Settings.LocationsSearchSettings
import de.mm20.launcher2.preferences.Settings.AppShortcutSearchSettings
import de.mm20.launcher2.preferences.Settings.CalculatorSearchSettings
import de.mm20.launcher2.preferences.Settings.CalendarSearchSettings
import de.mm20.launcher2.preferences.Settings.ContactsSearchSettings
import de.mm20.launcher2.preferences.Settings.FilesSearchSettings
import de.mm20.launcher2.preferences.Settings.UnitConverterSearchSettings
import de.mm20.launcher2.preferences.Settings.WebsiteSearchSettings
import de.mm20.launcher2.preferences.Settings.WikipediaSearchSettings
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

interface SearchService {
    fun search(
        query: String,
        shortcuts: AppShortcutSearchSettings = AppShortcutSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        contacts: ContactsSearchSettings = ContactsSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        calendars: CalendarSearchSettings = CalendarSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        files: FilesSearchSettings = FilesSearchSettings.newBuilder()
            .setLocalFiles(false)
            .setGdrive(false)
            .setOnedrive(false)
            .setOwncloud(false)
            .setNextcloud(false)
            .build(),
        calculator: CalculatorSearchSettings = CalculatorSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        unitConverter: UnitConverterSearchSettings = UnitConverterSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        websites: WebsiteSearchSettings = WebsiteSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        wikipedia: WikipediaSearchSettings = WikipediaSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        locations: LocationsSearchSettings = LocationsSearchSettings.newBuilder()
            .setEnabled(false)
            .setSearchRadius(1000)
            .build(),
    ): Flow<SearchResults>
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
) : SearchService {

    override fun search(
        query: String,
        shortcuts: AppShortcutSearchSettings,
        contacts: ContactsSearchSettings,
        calendars: CalendarSearchSettings,
        files: FilesSearchSettings,
        calculator: CalculatorSearchSettings,
        unitConverter: UnitConverterSearchSettings,
        websites: WebsiteSearchSettings,
        wikipedia: WikipediaSearchSettings,
        locations: LocationsSearchSettings,
    ): Flow<SearchResults> = channelFlow {
        val results = MutableStateFlow(SearchResults())
        supervisorScope {
            launch {
                searchActionService.search(query)
                    .collectLatest { r ->
                        results.update {
                            it.copy(searchActions = r)
                        }
                    }
            }
            launch {
                appRepository.search(query)
                    .withCustomLabels(customAttributesRepository)
                    .collectLatest { r ->
                        results.update {
                            it.copy(apps = r.toImmutableList())
                        }
                    }
            }
            if (shortcuts.enabled) {
                launch {
                    appShortcutRepository.search(query)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(shortcuts = r.toImmutableList())
                            }
                        }
                }
            }
            if (contacts.enabled) {
                launch {
                    contactRepository.search(query)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(contacts = r.toImmutableList())
                            }
                        }
                }
            }
            if (calendars.enabled) {
                launch {
                    calendarRepository.search(query)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(calendars = r.toImmutableList())
                            }
                        }
                }
            }
            if (calculator.enabled) {
                launch {
                    calculatorRepository.search(query).collectLatest { r ->
                        results.update {
                            it.copy(calculators = r?.let { persistentListOf(it) }
                                ?: persistentListOf())
                        }
                    }
                }
            }
            if (unitConverter.enabled) {
                launch {
                    unitConverterRepository.search(query, unitConverter.currencies)
                        .collectLatest { r ->
                            results.update {
                                it.copy(unitConverters = r?.let { persistentListOf(it) }
                                    ?: persistentListOf())
                            }
                        }
                }
            }
            if (websites.enabled) {
                launch {
                    websiteRepository.search(query)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(websites = r.toImmutableList())
                            }
                        }
                }
            }
            if (wikipedia.enabled) {
                launch {
                    delay(750)
                    articleRepository.search(query)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(wikipedia = r.toImmutableList())
                            }
                        }
                }
            }
            if (locations.enabled) {
                launch {
                    locationRepository.search(query)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(
                                    locations = r.filter {
                                        locations.hideUncategorized && it.getCategory() != LocationCategory.OTHER
                                    }.toImmutableList()
                                )
                            }
                        }
                }
            }
            if (files.localFiles || files.owncloud || files.onedrive || files.gdrive || files.nextcloud) {
                launch {
                    fileRepository.search(
                        query,
                    )
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(files = r.toImmutableList())
                            }
                        }
                }
            }
            launch {
                customAttributesRepository.search(query)
                    .withCustomLabels(customAttributesRepository)
                    .collectLatest { r ->
                        results.update {
                            it.copy(other = r.toImmutableList())
                        }
                    }
            }
            launch {
                results.collectLatest { send(it) }
            }

        }
    }
}

data class SearchResults(
    val apps: ImmutableList<Application>? = null,
    val shortcuts: ImmutableList<AppShortcut>? = null,
    val contacts: ImmutableList<Contact>? = null,
    val calendars: ImmutableList<CalendarEvent>? = null,
    val files: ImmutableList<File>? = null,
    val calculators: ImmutableList<Calculator>? = null,
    val unitConverters: ImmutableList<UnitConverter>? = null,
    val websites: ImmutableList<Website>? = null,
    val wikipedia: ImmutableList<Article>? = null,
    val locations: ImmutableList<Location>? = null,
    val searchActions: ImmutableList<SearchAction>? = null,
    val other: ImmutableList<SavableSearchable>? = null,
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
        other,
    ).flatten()
}