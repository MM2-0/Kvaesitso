package de.mm20.launcher2.appsearch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.mm20.launcher2.search.data.AppSearchResult

class AppSearchViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AppSearchRepository.getInstance(app)
    private val appSearch: LiveData<List<AppSearchResult>?> = repository.appSearchResults
}