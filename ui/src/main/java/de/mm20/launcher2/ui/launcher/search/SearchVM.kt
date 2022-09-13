package de.mm20.launcher2.ui.launcher.search

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.contacts.ContactRepository
import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.WebsearchRepository
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import de.mm20.launcher2.websites.WebsiteRepository
import de.mm20.launcher2.widgets.WidgetRepository
import de.mm20.launcher2.wikipedia.WikipediaRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val widgetRepository: WidgetRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    private val dataStore: LauncherDataStore by inject()

    private val calendarRepository: CalendarRepository by inject()
    private val contactRepository: ContactRepository by inject()
    private val appRepository: AppRepository by inject()
    private val appShortcutRepository: AppShortcutRepository by inject()
    private val wikipediaRepository: WikipediaRepository by inject()
    private val unitConverterRepository: UnitConverterRepository by inject()
    private val calculatorRepository: CalculatorRepository by inject()
    private val websiteRepository: WebsiteRepository by inject()
    private val fileRepository: FileRepository by inject()
    private val websearchRepository: WebsearchRepository by inject()

    val isSearching = MutableLiveData(false)
    val searchQuery = MutableLiveData("")
    val isSearchEmpty = MutableLiveData(true)

    val showLabels = dataStore.data.map { it.grid.showLabels }.asLiveData()

    val favorites = MutableLiveData<List<Searchable>>(emptyList())

    val appResults = MutableLiveData<List<Application>>(emptyList())
    val appShortcutResults = MutableLiveData<List<AppShortcut>>(emptyList())
    val fileResults = MutableLiveData<List<File>>(emptyList())
    val contactResults = MutableLiveData<List<Contact>>(emptyList())
    val calendarResults = MutableLiveData<List<CalendarEvent>>(emptyList())
    val wikipediaResult = MutableLiveData<Wikipedia?>(null)
    val websiteResult = MutableLiveData<Website?>(null)
    val calculatorResult = MutableLiveData<Calculator?>(null)
    val unitConverterResult = MutableLiveData<UnitConverter?>(null)
    val websearchResults = MutableLiveData<List<Websearch>>(emptyList())

    val hiddenResults = MutableLiveData<List<Searchable>>(emptyList())

    val hideFavorites = MutableLiveData(false)

    private val hiddenItemKeys = favoritesRepository
        .getHiddenItemKeys()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    init {
        search("")
        viewModelScope.launch {
            dataStore.data.map { it.favorites.enabled }.collectLatest { enabled ->
                if (!enabled) {
                    favorites.value = emptyList()
                    return@collectLatest
                }
                widgetRepository.isCalendarWidgetEnabled().collectLatest { excludeCalendar ->
                    dataStore.data.map { it.grid.columnCount }.collectLatest { columns ->
                        favoritesRepository
                            .getFavorites(
                                columns = columns,
                                excludeCalendarEvents = excludeCalendar
                            )
                            .withCustomLabels()
                            .collectLatest {
                                favorites.value = it
                            }
                    }
                }
            }
        }
    }

    var searchJob: Job? = null
    fun search(query: String) {
        searchQuery.value = query
        isSearchEmpty.value = query.isEmpty()
        hiddenResults.value = emptyList()

        val hiddenItems = MutableStateFlow(HiddenItemResults())

        try {
            searchJob?.cancel()
        } catch (_: CancellationException) {
        }
        hideFavorites.postValue(query.isNotEmpty())
        searchJob = viewModelScope.launch {
            isSearching.postValue(true)
            val customAttrResults = customAttributesRepository.search(query)
                .combine(dataStore.data) { items, settings ->
                    items.filter {
                        it is Application
                                || it is Contact && settings.contactsSearch.enabled
                                || it is CalendarEvent && settings.calendarSearch.enabled
                                || it is AppShortcut && settings.appShortcutSearch.enabled
                                || it is LocalFile && settings.fileSearch.localFiles
                                || it is GDriveFile && settings.fileSearch.gdrive
                                || it is OneDriveFile && settings.fileSearch.onedrive
                    }
                }
            val jobs = mutableListOf<Deferred<Any>>()
            jobs += async(Dispatchers.Default) {
                appRepository
                    .search(query)
                    .withCustomAttributeResults(customAttrResults)
                    .withCustomLabels()
                    .sorted()
                    .collectWithHiddenItems(hiddenItemKeys) { results, hidden ->
                        appResults.postValue(results)
                        hiddenItems.update {
                            it.copy(apps = hidden)
                        }
                    }
            }
            jobs += async(Dispatchers.Default) {
                contactRepository
                    .search(query)
                    .withCustomAttributeResults(customAttrResults)
                    .withCustomLabels()
                    .sorted()
                    .collectWithHiddenItems(hiddenItemKeys) { results, hidden ->
                        contactResults.postValue(results)
                        hiddenItems.update {
                            it.copy(contacts = hidden)
                        }
                    }
            }
            jobs += async(Dispatchers.Default) {
                calendarRepository
                    .search(query)
                    .withCustomAttributeResults(customAttrResults)
                    .withCustomLabels()
                    .sorted()
                    .collectWithHiddenItems(hiddenItemKeys) { results, hidden ->
                        calendarResults.postValue(results)
                        hiddenItems.update {
                            it.copy(calendarEvents = hidden)
                        }
                    }
            }
            jobs += async(Dispatchers.Default) {
                wikipediaRepository.search(query).collectLatest {
                    wikipediaResult.postValue(it)
                }
            }
            jobs += async(Dispatchers.Default) {
                unitConverterRepository.search(query).collectLatest {
                    unitConverterResult.postValue(it)
                }
            }
            jobs += async(Dispatchers.Default) {
                calculatorRepository.search(query).collectLatest {
                    calculatorResult.postValue(it)
                }
            }
            jobs += async(Dispatchers.Default) {
                websiteRepository.search(query).collectLatest {
                    websiteResult.postValue(it)
                }
            }
            jobs += async(Dispatchers.Default) {
                fileRepository
                    .search(query)
                    .withCustomAttributeResults(customAttrResults)
                    .withCustomLabels()
                    .sorted()
                    .collectWithHiddenItems(hiddenItemKeys) { results, hidden ->
                        fileResults.postValue(results)
                        hiddenItems.update {
                            it.copy(files = hidden)
                        }
                    }
            }
            jobs += async(Dispatchers.Default) {
                websearchRepository.search(query).collectLatest {
                    websearchResults.postValue(it)
                }
            }
            jobs += async(Dispatchers.Default) {
                appShortcutRepository
                    .search(query)
                    .withCustomAttributeResults(customAttrResults)
                    .withCustomLabels()
                    .sorted()
                    .collectWithHiddenItems(hiddenItemKeys) { results, hidden ->
                        appShortcutResults.postValue(results)
                        hiddenItems.update {
                            it.copy(appShortcuts = hidden)
                        }
                    }
            }
            launch(Dispatchers.Default) {
                hiddenItems.collectLatest {
                    hiddenResults.postValue(it.joinToList())
                }
            }
            jobs.map { it.await() }
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

    /**
     * Inject custom labels and sort by the actual label
     */
    private fun <T : Searchable> Flow<List<T>>.withCustomLabels(): Flow<List<T>> = channelFlow {
        this@withCustomLabels.collectLatest { items ->
            val labelsFlow = customAttributesRepository.getCustomLabels(items)
            labelsFlow.collectLatest { labels ->
                for (item in items) {
                    val customLabel = labels.find { it.key == item.key }
                    item.labelOverride = customLabel?.label
                }
                send(items)
            }
        }
    }

    private inline fun <reified T : Searchable> Flow<List<T>>.withCustomAttributeResults(
        customAttributeResults: Flow<List<Searchable>>
    ): Flow<List<T>> {
        return this.combine(customAttributeResults) { items, items2 ->
            (items + items2.filterIsInstance<T>()).distinctBy { it.key }
        }
    }

    private suspend fun <T : Searchable> Flow<List<T>>.collectWithHiddenItems(
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

    private fun <T : Searchable> Flow<List<T>>.sorted(): Flow<List<T>> = this.map { it.sorted() }

}

private data class HiddenItemResults(
    val apps: List<Application> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val files: List<File> = emptyList(),
    val appShortcuts: List<AppShortcut> = emptyList(),
) {
    fun joinToList(): List<Searchable> {
        return apps + contacts + calendarEvents + files + appShortcuts
    }
}