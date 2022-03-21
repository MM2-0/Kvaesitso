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
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.WebsearchRepository
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import de.mm20.launcher2.websites.WebsiteRepository
import de.mm20.launcher2.wikipedia.WikipediaRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
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

    val favorites by lazy {
        favoritesRepository.getFavorites().asLiveData()
    }

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

    val hideFavorites = MutableLiveData(false)

    init {
        search("")
    }

    var searchJob: Job? = null
    fun search(query: String) {
        searchQuery.value = query
        isSearchEmpty.value = query.isEmpty()
        try {
            searchJob?.cancel()
        } catch (e: CancellationException) {
        }
        hideFavorites.postValue(query.isNotEmpty())
        searchJob = viewModelScope.launch {
            isSearching.postValue(true)
            val jobs = mutableListOf<Deferred<Any>>()
            jobs += async {
                appRepository.search(query).collectLatest {
                    appResults.postValue(it)
                }
            }
            jobs += async {
                contactRepository.search(query).collectLatest {
                    contactResults.postValue(it)
                }
            }
            jobs += async {
                calendarRepository.search(query).collectLatest {
                    calendarResults.postValue(it)
                }
            }
            jobs += async {
                wikipediaRepository.search(query).collectLatest {
                    wikipediaResult.postValue(it)
                }
            }
            jobs += async {
                unitConverterRepository.search(query).collectLatest {
                    unitConverterResult.postValue(it)
                }
            }
            jobs += async {
                calculatorRepository.search(query).collectLatest {
                    calculatorResult.postValue(it)
                }
            }
            jobs += async {
                websiteRepository.search(query).collectLatest {
                    websiteResult.postValue(it)
                }
            }
            jobs += async {
                fileRepository.search(query).collectLatest {
                    fileResults.postValue(it)
                }
            }
            jobs += async {
                websearchRepository.search(query).collectLatest {
                    websearchResults.postValue(it)
                }
            }
            jobs += async {
                appShortcutRepository.search(query).collectLatest {
                    appShortcutResults.postValue(it)
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

}