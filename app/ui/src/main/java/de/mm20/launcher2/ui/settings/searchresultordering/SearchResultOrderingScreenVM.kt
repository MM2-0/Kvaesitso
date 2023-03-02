package de.mm20.launcher2.ui.settings.searchresultordering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import de.mm20.launcher2.preferences.Settings.SearchResultOrderingSettings.Ordering
import de.mm20.launcher2.preferences.Settings.SearchResultOrderingSettings.WeightFactor

class SearchResultOrderingScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val searchResultOrdering = dataStore.data.map { it.resultOrdering.ordering }.asLiveData()
    fun setSearchResultOrdering(searchResultOrdering: Ordering) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setResultOrdering(
                    it.resultOrdering.toBuilder().setOrdering(searchResultOrdering)
                ).build()
            }
        }
    }

    val searchResultWeightFactor = dataStore.data.map { it.resultOrdering.weightFactor }.asLiveData()
    fun setSearchResultWeightFactor(searchResultWeightFactor: WeightFactor) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder().setResultOrdering(
                    it.resultOrdering.toBuilder().setWeightFactor(searchResultWeightFactor)
                ).build()
            }
        }
    }
}