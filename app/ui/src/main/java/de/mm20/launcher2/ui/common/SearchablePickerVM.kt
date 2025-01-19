package de.mm20.launcher2.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchFilters
import de.mm20.launcher2.search.SearchService
import de.mm20.launcher2.search.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchablePickerVM : ViewModel(), KoinComponent {

    private val searchService: SearchService by inject()
    private val iconService: IconService by inject()

    var searchQuery by mutableStateOf("")

    var items by mutableStateOf(emptyList<SavableSearchable>())

    init {
        onSearchQueryChanged("", true)
    }

    var searchJob: Job? = null
    fun onSearchQueryChanged(query: String, forceRestart: Boolean = false) {
        if (searchQuery == query && !forceRestart) return
        searchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchService.search(
                query = query,
                filters = SearchFilters(allowNetwork = true)
            ).collectLatest {
                if (searchQuery != query) return@collectLatest
                items = withContext(Dispatchers.Default) {
                    it.toList().filterIsInstance<SavableSearchable>().sorted()
                }
            }
        }
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }
}