package de.mm20.launcher2.ui.settings.hiddenitems

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.searchable.VisibilityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HiddenItemsSettingsScreenVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val searchableRepository: SavableSearchableRepository by inject()
    private val iconService: IconService by inject()
    private val searchUiSettings: SearchUiSettings by inject()

    val allApps = appRepository.findMany().map {
        withContext(Dispatchers.Default) { it.sorted() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val hiddenItems: StateFlow<List<SavableSearchable>> = flow {
        val hidden =
            searchableRepository.get(
                maxVisibility = VisibilityLevel.SearchOnly,
            ).first().filter { it !is Application }.sorted()
        emit(hidden)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun getVisibility(searchable: SavableSearchable): Flow<VisibilityLevel> {
        return searchableRepository.getVisibility(searchable)
    }

    fun setVisibility(searchable: SavableSearchable, visibilityLevel: VisibilityLevel) {
        searchableRepository.upsert(searchable, visibilityLevel)
    }

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }

    fun launch(context: Context, searchable: SavableSearchable) {
        val bundle = Bundle()
        if (isAtLeastApiLevel(31)) {
            bundle.putInt("android.activity.splashScreenStyle", 1)
        }
        searchable.launch(context, bundle)
    }

    fun openAppInfo(context: Context, app: Application) {
        app.openAppDetails(context)
    }

    val hiddenItemsButton = searchUiSettings.hiddenItemsButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setHiddenItemsButton(hidden: Boolean) {
        searchUiSettings.setHiddenItemsButton(hidden)
    }
}