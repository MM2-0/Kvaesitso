package de.mm20.launcher2.search

import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.contacts.ContactRepository
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.preferences.Settings
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
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.actions.SearchAction
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
        shortcuts: AppShortcutSearchSettings = Settings.AppShortcutSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        contacts: ContactsSearchSettings = Settings.ContactsSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        calendars: CalendarSearchSettings = Settings.CalendarSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        files: FilesSearchSettings = Settings.FilesSearchSettings.newBuilder()
            .setLocalFiles(false)
            .setGdrive(false)
            .setOnedrive(false)
            .setOwncloud(false)
            .setNextcloud(false)
            .build(),
        calculator: CalculatorSearchSettings = Settings.CalculatorSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        unitConverter: UnitConverterSearchSettings = Settings.UnitConverterSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        websites: WebsiteSearchSettings = Settings.WebsiteSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
        wikipedia: WikipediaSearchSettings = Settings.WikipediaSearchSettings.newBuilder()
            .setEnabled(false)
            .build(),
    ): Flow<SearchResults>
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
                        .map { it?.let { listOf(it) } ?: listOf() }
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
                    wikipediaRepository.search(query, loadImages = wikipedia.images)
                        .map { it?.let { listOf(it) } ?: listOf() }
                        .withCustomLabels(customAttributesRepository)
                        .collectLatest { r ->
                            results.update {
                                it.copy(wikipedia = r.toImmutableList())
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
                results.collectLatest { send(it) }
            }

        }
    }
}

data class SearchResults(
    val apps: ImmutableList<LauncherApp>? = null,
    val shortcuts: ImmutableList<AppShortcut>? = null,
    val contacts: ImmutableList<Contact>? = null,
    val calendars: ImmutableList<CalendarEvent>? = null,
    val files: ImmutableList<File>? = null,
    val calculators: ImmutableList<Calculator>? = null,
    val unitConverters: ImmutableList<UnitConverter>? = null,
    val websites: ImmutableList<Website>? = null,
    val wikipedia: ImmutableList<Wikipedia>? = null,
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