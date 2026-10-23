package de.mm20.launcher2.ui.settings.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ExcludedFrequentlyUsedSettingsScreenVM : ViewModel(), KoinComponent {
    private val favoritesService: FavoritesService by inject()
    private val iconService: IconService by inject()

    val excludedItems: StateFlow<List<SavableSearchable>?> =
        favoritesService.getExcludedFromFrequentlyUsed()
            .map { it.sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun getIcon(searchable: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(searchable, size)
    }

    fun restore(searchable: SavableSearchable) {
        favoritesService.setExcludedFromFrequentlyUsed(searchable, false)
    }
}
