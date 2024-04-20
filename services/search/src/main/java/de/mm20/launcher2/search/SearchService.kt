package de.mm20.launcher2.search

import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
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
        filters: SearchFilters,
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
        filters: SearchFilters,
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
            if (filters.apps) {
                launch {
                    appRepository.search(query, filters.allowNetwork)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(apps = r.toImmutableList())
                            }
                        }
                }
            }
            if (filters.shortcuts) {
                launch {
                    appShortcutRepository.search(query, filters.allowNetwork)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(shortcuts = r.toImmutableList())
                            }
                        }
                }
            }
            if (filters.contacts) {
                launch {
                    contactRepository.search(query, filters.allowNetwork)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(contacts = r.toImmutableList())
                            }
                        }
                }
            }
            if (filters.events) {
                launch {
                    calendarRepository.search(query, filters.allowNetwork)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(calendars = r.toImmutableList())
                            }
                        }
                }
            }
            if (filters.tools) {
                launch {
                    calculatorRepository.search(query).collectLatest { r ->
                        results.update {
                            it.copy(calculators = r?.let { persistentListOf(it) }
                                ?: persistentListOf())
                        }
                    }
                }
                launch {
                    unitConverterRepository.search(query)
                        .collectLatest { r ->
                            results.update {
                                it.copy(unitConverters = r?.let { persistentListOf(it) }
                                    ?: persistentListOf())
                            }
                        }
                }
            }
            if (filters.websites) {
                launch {
                    websiteRepository.search(query, filters.allowNetwork)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(websites = r.toImmutableList())
                            }
                        }
                }
            }
            if (filters.articles) {
                launch {
                    delay(750)
                    articleRepository.search(query, filters.allowNetwork)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(wikipedia = r.toImmutableList())
                            }
                        }
                }
            }
            if (filters.places) {
                launch {
                    locationRepository.search(query, filters.allowNetwork)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(locations = r.toImmutableList())
                            }
                        }
                }
            }
            if (filters.files) {
                launch {
                    fileRepository.search(
                        query,
                        filters.allowNetwork
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