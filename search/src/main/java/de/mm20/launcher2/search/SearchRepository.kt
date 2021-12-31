package de.mm20.launcher2.search

import androidx.lifecycle.MutableLiveData

class SearchRepository {

    val isSearching = MutableLiveData<Boolean>(false)
    val currentQuery = MutableLiveData<String>()

    private var runningSearches = 0
        set(value) {
            synchronized(runningSearches) {
                field = value
                isSearching.value = value > 0
            }
        }

    @Synchronized
    fun startSearch() {
        synchronized(runningSearches) {
            runningSearches++
        }
    }

}