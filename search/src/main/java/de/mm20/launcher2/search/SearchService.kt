package de.mm20.launcher2.search

import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.contacts.ContactRepository
import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.customattrs.utils.withCustomLabels
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.preferences.Settings.AppShortcutSearchSettings
import de.mm20.launcher2.preferences.Settings.CalculatorSearchSettings
import de.mm20.launcher2.preferences.Settings.CalendarSearchSettings
import de.mm20.launcher2.preferences.Settings.ContactsSearchSettings
import de.mm20.launcher2.preferences.Settings.FilesSearchSettings
import de.mm20.launcher2.preferences.Settings.UnitConverterSearchSettings
import de.mm20.launcher2.preferences.Settings.WebsiteSearchSettings
import de.mm20.launcher2.preferences.Settings.WikipediaSearchSettings
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.Calculator
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Contact
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.GDriveFile
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.LocalFile
import de.mm20.launcher2.search.data.NextcloudFile
import de.mm20.launcher2.search.data.OneDriveFile
import de.mm20.launcher2.search.data.OwncloudFile
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.search.data.Website
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import de.mm20.launcher2.websites.WebsiteRepository
import de.mm20.launcher2.wikipedia.WikipediaRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

interface SearchService {
    fun search(
        query: String,
        shortcuts: AppShortcutSearchSettings,
        contacts: ContactsSearchSettings,
        calendars: CalendarSearchSettings,
        files: FilesSearchSettings,
        calculator: CalculatorSearchSettings,
        unitConverter: UnitConverterSearchSettings,
        websites: WebsiteSearchSettings,
        wikipedia: WikipediaSearchSettings,
    ): Flow<ImmutableList<Searchable>>
}

internal class SearchServiceImpl(
    private val appRepository: AppRepository,
    private val appShortcutRepository: AppShortcutRepository,
    private val calendarRepository: CalendarRepository,
    private val contactRepository: ContactRepository,
    private val fileRepository: FileRepository,
    private val wikipediaRepository: WikipediaRepository,
    private val unitConverterRepository: UnitConverterRepository,
    private val calculatorRepository: CalculatorRepository,
    private val websiteRepository: WebsiteRepository,
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
    ): Flow<ImmutableList<Searchable>> = channelFlow {
        var searchActionsReady = false
        supervisorScope {
            val results = MutableStateFlow(SearchResults())
            launch {
                appRepository.search(query)
                    .withCustomLabels(customAttributesRepository)
                    .collectLatest { r ->
                        results.update {
                            it.copy(apps = r)
                        }
                    }
            }
            if (shortcuts.enabled) {
                launch {
                    appShortcutRepository.search(query)
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(shortcuts = r)
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
                                it.copy(contacts = r)
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
                                it.copy(calendars = r)
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
                    unitConverterRepository.search(query, unitConverter.currencies).collectLatest { r ->
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
                        .map { it?.let { listOf(it) } ?: listOf() }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(websites = r)
                            }
                        }
                }
            }
            if (wikipedia.enabled) {
                launch {
                    wikipediaRepository.search(query, loadImages = wikipedia.images)
                        .map { it?.let { listOf(it) } ?: listOf() }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(wikipedia = r)
                            }
                        }
                }
            }
            if (files.localFiles || files.owncloud || files.onedrive || files.gdrive || files.nextcloud) {
                launch {
                    fileRepository.search(
                        query,
                        local = files.localFiles,
                        nextcloud = files.nextcloud,
                        owncloud = files.owncloud,
                        onedrive = files.onedrive,
                        gdrive = files.gdrive,
                    )
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(files = r)
                            }
                        }
                }
            }
            launch {
                customAttributesRepository.search(query)
                    .withCustomLabels(customAttributesRepository)
                    .collectLatest { r ->
                        results.update {
                            it.copy(
                                other = r
                                    .filter {
                                        it is LauncherApp ||
                                                shortcuts.enabled && it is AppShortcut ||
                                                files.localFiles && it is LocalFile ||
                                                files.nextcloud && it is NextcloudFile ||
                                                files.owncloud && it is OwncloudFile ||
                                                files.onedrive && it is OneDriveFile ||
                                                files.gdrive && it is GDriveFile ||
                                                wikipedia.enabled && it is Wikipedia ||
                                                websites.enabled && it is Website ||
                                                calendars.enabled && it is CalendarEvent ||
                                                contacts.enabled && it is Contact
                                    }.toImmutableList()
                            )
                        }
                    }
            }
            launch {
                searchActionService.search(query)
                    .collectLatest { r ->
                        results.update {
                            searchActionsReady = true
                            it.copy(searchActions = r)
                        }
                    }
            }
            launch {
                results
                    .map { it.toList().sortedBy { it as? SavableSearchable }.toImmutableList() }
                    .collectLatest {
                        if (searchActionsReady) send(it)
                    }
            }
        }
    }
}

internal data class SearchResults(
    val apps: List<LauncherApp> = emptyList(),
    val shortcuts: List<AppShortcut> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val calendars: List<CalendarEvent> = emptyList(),
    val files: List<File> = emptyList(),
    val calculators: List<Calculator> = emptyList(),
    val unitConverters: List<UnitConverter> = emptyList(),
    val websites: List<Website> = emptyList(),
    val wikipedia: List<Wikipedia> = emptyList(),
    val searchActions: List<SearchAction> = emptyList(),
    val other: List<SavableSearchable> = emptyList(),
) {
    fun toList(): List<Searchable> {
        return searchActions + (apps + shortcuts + contacts + calendars  + files + websites + wikipedia + other).distinctBy { it.key } + calculators + unitConverters
    }
}