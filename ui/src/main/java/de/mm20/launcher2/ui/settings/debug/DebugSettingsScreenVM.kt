package de.mm20.launcher2.ui.settings.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.favorites.FavoritesRepository
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DebugSettingsScreenVM: ViewModel(), KoinComponent {

    val favoritesRepository: FavoritesRepository by inject()
    suspend fun cleanUpDatabase(): Int {
        return favoritesRepository.cleanupDatabase()
    }
}