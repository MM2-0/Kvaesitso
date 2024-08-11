package de.mm20.launcher2.ui.settings.searchactions

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.searchactions.SearchActionService
import de.mm20.launcher2.searchactions.builders.CustomizableSearchActionBuilder
import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class SearchActionsSettingsScreenVM : ViewModel(), KoinComponent {
    private val searchActionService: SearchActionService by inject()

    val searchActions = searchActionService
        .getSearchActionBuilders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val disabledActions = searchActionService
        .getDisabledActionBuilders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun addAction(searchAction: SearchActionBuilder) {
        val actions =
            searchActions.value?.filter { it.key != searchAction.key }?.plus(searchAction) ?: return
        searchActionService.saveSearchActionBuilders(actions)
        showCreateDialog.value = false
    }

    fun removeAction(searchAction: SearchActionBuilder) {
        val actions = searchActions.value?.filter { it.key != searchAction.key } ?: return
        searchActionService.saveSearchActionBuilders(actions)
        showEditDialogFor.value = null
        if(searchAction is CustomizableSearchActionBuilder && searchAction.customIcon != null) {
            viewModelScope.launch(Dispatchers.IO) {
                File(searchAction.customIcon).delete()
            }
        }
    }

    fun updateAction(old: SearchActionBuilder, new: SearchActionBuilder) {
        val actions =
            searchActions.value
                ?.mapNotNull { if (it.key == old.key) new else if (it.key == new.key) null else it }
                ?: return
        searchActionService.saveSearchActionBuilders(actions)
        showEditDialogFor.value = null
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val actions = searchActions.value?.toMutableList() ?: return
        if (fromIndex > actions.lastIndex || fromIndex < 0) return
        if (toIndex > actions.lastIndex || toIndex < 0) return
        val item = actions.removeAt(fromIndex)
        actions.add(toIndex, item)
        searchActionService.saveSearchActionBuilders(actions)
    }

    val showEditDialogFor = mutableStateOf<CustomizableSearchActionBuilder?>(null)
    val showCreateDialog = mutableStateOf(false)

    fun editAction(action: CustomizableSearchActionBuilder) {
        showEditDialogFor.value = action
    }

    fun createAction() {
        showCreateDialog.value = true
    }

    fun dismissDialogs() {
        showCreateDialog.value = false
        showEditDialogFor.value = null
    }
}