package de.mm20.launcher2.ui.launcher.search

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.calculator.CalculatorRepository
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.contacts.ContactRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.WebsearchRepository
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.unitconverter.UnitConverterRepository
import de.mm20.launcher2.websites.WebsiteRepository
import de.mm20.launcher2.wikipedia.WikipediaRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchVM : ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()

    private val calendarRepository: CalendarRepository by inject()
    private val contactRepository: ContactRepository by inject()
    private val appRepository: AppRepository by inject()
    private val wikipediaRepository: WikipediaRepository by inject()
    private val unitConverterRepository: UnitConverterRepository by inject()
    private val calculatorRepository: CalculatorRepository by inject()
    private val websiteRepository: WebsiteRepository by inject()
    private val fileRepository: FileRepository by inject()
    private val websearchRepository: WebsearchRepository by inject()

    val isSearching = MutableLiveData(false)
    val searchQuery = MutableLiveData("")

    val favorites by lazy {
        favoritesRepository.getFavorites().asLiveData()
    }

    val appResults = MutableLiveData<List<Application>>(emptyList())
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
            jobs.map { it.await() }
            isSearching.postValue(false)
        }
    }

}