package de.mm20.launcher2.ui.settings.filterbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.KeyboardFilterBarItem
import de.mm20.launcher2.preferences.search.SearchFilterSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FilterBarSettingsScreenVM(
) : ViewModel(), KoinComponent {
    private val searchFilterSettings: SearchFilterSettings by inject()

    val filterBarItems = searchFilterSettings.filterBarItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun moveItem(item: KeyboardFilterBarItem, toItem: KeyboardFilterBarItem) {
        val items = filterBarItems.value?.toMutableList() ?: return
        val fromIndex = items.indexOf(item)
        val toIndex = items.indexOf(toItem)
        if (fromIndex > items.lastIndex) return
        if (toIndex > items.lastIndex) return
        if (fromIndex != -1) items.removeAt(fromIndex)
        if (toIndex != -1) items.add(toIndex, item)
        searchFilterSettings.setFilterBarItems(items)
    }

    fun addAction(item: KeyboardFilterBarItem) {
        val items = filterBarItems.value?.toMutableList() ?: return
        items.add(item)
        searchFilterSettings.setFilterBarItems(items)
    }

    fun removeAction(item: KeyboardFilterBarItem) {
        val items = filterBarItems.value?.toMutableList() ?: return
        items.remove(item)
        searchFilterSettings.setFilterBarItems(items)
    }
}