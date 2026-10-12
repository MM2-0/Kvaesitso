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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import android.content.Context
import kotlinx.coroutines.CancellationException
import de.mm20.launcher2.searchactions.SearchActionRepository
import de.mm20.launcher2.searchactions.flow.*
import de.mm20.launcher2.ui.R

class SearchActionsSettingsScreenVM : ViewModel(), KoinComponent {
    private val aliasRepository: SearchActionRepository by inject()
    private val aliasRuntime: FlowAliasRuntime by inject()
    val aliasTargets = aliasRuntime.targets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val showAliasEditor = mutableStateOf(false)
    val editingAlias = mutableStateOf<FlowAlias?>(null)
    val aliasError = mutableStateOf<String?>(null)
    val savingAlias = mutableStateOf(false)

    fun editAlias(alias: FlowAlias?) {
        editingAlias.value = alias
        aliasError.value = null
        showAliasEditor.value = true
    }

    fun dismissAlias() {
        if (!savingAlias.value) showAliasEditor.value = false
    }

    fun saveAlias(context: Context, alias: FlowAlias) = mutateAlias(context) {
        val available = aliasRuntime.targets().first().any { it.key == alias.targetKey }
        val error = if (available) aliasRepository.saveFlowAlias(alias) else FlowAliasError.InvalidTarget
        aliasError.value = error?.let { context.getString(when (it) {
            FlowAliasError.Empty -> R.string.flow_alias_empty
            FlowAliasError.ControlCharacter -> R.string.flow_alias_control
            FlowAliasError.TooLong -> R.string.flow_alias_too_long
            FlowAliasError.Duplicate -> R.string.flow_alias_duplicate
            FlowAliasError.InvalidTarget -> R.string.flow_alias_target_unavailable
        }) }
        if (error == null) showAliasEditor.value = false
    }

    fun deleteAlias(context: Context, id: String) = mutateAlias(context) {
        aliasRepository.removeFlowAlias(id)
        showAliasEditor.value = false
    }

    private fun mutateAlias(context: Context, operation: suspend () -> Unit) {
        if (savingAlias.value) return
        savingAlias.value = true
        viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { aliasError.value = context.getString(R.string.flow_alias_save_failed) }
            finally { savingAlias.value = false }
        }
    }

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