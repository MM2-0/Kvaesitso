package de.mm20.launcher2.favorites

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    fun getTopFavorites(count: Int): LiveData<List<Searchable>> {
        return favoritesRepository.getTopFavorites(count)
    }

    fun getFavorites(columns: Int): LiveData<List<Searchable>> {
        return favoritesRepository.getFavorites(columns)
    }

    fun pinItem(searchable: Searchable) {
        favoritesRepository.pinItem(searchable)
    }

    fun unpinItem(searchable: Searchable) {
        favoritesRepository.unpinItem(searchable)
    }

    fun isPinned(searchable: Searchable): LiveData<Boolean> {
        return favoritesRepository.isPinned(searchable)
    }

    fun isHidden(searchable: Searchable): LiveData<Boolean> {
        return favoritesRepository.isHidden(searchable)
    }

    fun hideItem(searchable: Searchable) {
        favoritesRepository.hideItem(searchable)
    }

    fun unhideItem(searchable: Searchable) {
        favoritesRepository.unhideItem(searchable)
    }

    suspend fun getAllFavoriteItems(): List<FavoritesItem> {
        return favoritesRepository.getAllFavoriteItems()
    }

    fun saveFavorites(favorites: MutableList<FavoritesItem>) {
        favoritesRepository.saveFavorites(favorites)
    }

    val hiddenItems: LiveData<List<Searchable>> = this.favoritesRepository.hiddenItems
    val pinnedCalendarEvents: LiveData<List<CalendarEvent>> = this.favoritesRepository.pinnedCalendarEvents
}