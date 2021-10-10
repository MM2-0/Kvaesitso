package de.mm20.launcher2.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel

class SearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    val isSearching: LiveData<Boolean> = searchRepository.isSearching

    fun search(query: String) {
        searchRepository.currentQuery.value = query
    }

}