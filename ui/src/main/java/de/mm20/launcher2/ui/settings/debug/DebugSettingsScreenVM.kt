package de.mm20.launcher2.ui.settings.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.customattrs.CustomAttributesRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DebugSettingsScreenVM: ViewModel(), KoinComponent {

    private val favoritesRepository: FavoritesRepository by inject()
    private val customAttributesRepository: CustomAttributesRepository by inject()
    suspend fun cleanUpDatabase(): Int {
        var removed = favoritesRepository.cleanupDatabase()
        removed += customAttributesRepository.cleanupDatabase()
        return removed
    }
}