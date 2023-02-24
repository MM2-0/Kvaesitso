package de.mm20.launcher2.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchablePickerVM: ViewModel(), KoinComponent {

    private val dataStore: LauncherDataStore by inject()
    private val searchService: SearchService by inject()
    private val iconRepository: IconRepository by inject()

    var searchQuery by mutableStateOf("")

    init {
        onSearchQueryChanged("", true)
    }

    var items by mutableStateOf(emptyList<SavableSearchable>())

    var searchJob: Job? = null
    fun onSearchQueryChanged(query: String, forceRestart: Boolean = false) {
        if (searchQuery == query && !forceRestart) return
        searchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val settings = dataStore.data.first()
            searchService.search(
                query = query,
                shortcuts = settings.appShortcutSearch,
                contacts = settings.contactsSearch,
                calendars = settings.calendarSearch,
                files = settings.fileSearch,
            ).collectLatest {
                if (searchQuery != query) return@collectLatest
                items  = withContext(Dispatchers.Default) {
                    it.toList().filterIsInstance<SavableSearchable>().sorted()
                }
            }
        }
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon> {
        return iconRepository.getIcon(searchable, size)
    }
}