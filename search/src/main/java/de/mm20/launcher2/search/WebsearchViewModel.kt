package de.mm20.launcher2.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.mm20.launcher2.search.data.Websearch

class WebsearchViewModel(app:Application): AndroidViewModel(app) {

    private val repository = WebsearchRepository.getInstance(app)

    fun insertWebsearch(websearch: Websearch) {
        return repository.insertWebsearch(websearch)
    }

    fun deleteWebsearch(websearch: Websearch) {
        repository.deleteWebsearch(websearch)
    }

    val websearches = repository.websearches
    val allWebsearches = repository.allWebsearches

}