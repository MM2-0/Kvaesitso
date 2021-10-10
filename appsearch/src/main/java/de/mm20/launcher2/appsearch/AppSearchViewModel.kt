package de.mm20.launcher2.appsearch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.AppSearchResult

class AppSearchViewModel(
    appSearchRepository: AppSearchRepository
) : ViewModel() {
    private val appSearch: LiveData<List<AppSearchResult>?> = appSearchRepository.appSearchResults
}