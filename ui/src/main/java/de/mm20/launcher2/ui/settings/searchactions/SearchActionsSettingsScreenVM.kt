package de.mm20.launcher2.ui.settings.searchactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchActionsSettingsScreenVM : ViewModel(), KoinComponent {
    private val searchActionService: SearchActionService by inject()

    val searchActions = searchActionService
        .getSearchActionBuilders()
        .asLiveData()

    val disabledActions = searchActionService
        .getDisabledActionBuilders()
        .asLiveData()

    fun addAction(searchAction: SearchActionBuilder) {
        val actions =
            searchActions.value?.filter { it.key != searchAction.key }?.plus(searchAction) ?: return
        searchActionService.saveSearchActionBuilders(actions)
    }

    fun removeAction(searchAction: SearchActionBuilder) {
        val actions = searchActions.value?.filter { it.key != searchAction.key } ?: return
        searchActionService.saveSearchActionBuilders(actions)
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val actions = searchActions.value?.toMutableList() ?: return
        if (fromIndex > actions.lastIndex) return
        if (toIndex > actions.lastIndex) return
        val item = actions.removeAt(fromIndex)
        actions.add(toIndex, item)
        searchActionService.saveSearchActionBuilders(actions)
    }
}