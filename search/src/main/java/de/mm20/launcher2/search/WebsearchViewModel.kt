package de.mm20.launcher2.search

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.Websearch

class WebsearchViewModel(
    private val websearchRepository: WebsearchRepository
): ViewModel() {


    fun insertWebsearch(websearch: Websearch) {
        return websearchRepository.insertWebsearch(websearch)
    }

    fun deleteWebsearch(websearch: Websearch) {
        websearchRepository.deleteWebsearch(websearch)
    }

    val websearches = websearchRepository.websearches
    val allWebsearches = websearchRepository.allWebsearches

}