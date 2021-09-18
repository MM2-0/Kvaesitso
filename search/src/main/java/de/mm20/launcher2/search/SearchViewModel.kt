package de.mm20.launcher2.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SearchRepository.getInstance()

    val isSearching: LiveData<Boolean> = repository.isSearching

    fun search(query: String) {
        repository.currentQuery.value = query
    }

}