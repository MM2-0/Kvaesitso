package de.mm20.launcher2.ui.launcher.modals

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.favorites.FavoritesItem
import de.mm20.launcher2.favorites.FavoritesRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditFavoritesVM : ViewModel(), KoinComponent {

    private val repository: FavoritesRepository by inject()

    suspend fun getFavorites(): List<FavoritesItem> {
        return repository.getAllFavoriteItems()
    }

    fun saveFavorites(favorites: List<FavoritesItem>) {
        repository.saveFavorites(favorites)
    }
}