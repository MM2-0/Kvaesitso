package de.mm20.launcher2.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {

    val repository = FavoritesRepository.getInstance(app)

    fun getTopFavorites(count: Int): LiveData<List<Searchable>> {
        return repository.getTopFavorites(count)
    }

    fun getFavorites(columns: Int): LiveData<List<Searchable>> {
        return repository.getFavorites(columns)
    }

    fun pinItem(searchable: Searchable) {
        repository.pinItem(searchable)
    }

    fun unpinItem(searchable: Searchable) {
        repository.unpinItem(searchable)
    }

    fun isPinned(searchable: Searchable): LiveData<Boolean> {
        return repository.isPinned(searchable)
    }

    fun isHidden(searchable: Searchable): LiveData<Boolean> {
        return repository.isHidden(searchable)
    }

    fun hideItem(searchable: Searchable) {
        repository.hideItem(searchable)
    }

    fun unhideItem(searchable: Searchable) {
        repository.unhideItem(searchable)
    }

    suspend fun getAllFavoriteItems(): List<FavoritesItem> {
        return repository.getAllFavoriteItems()
    }

    fun saveFavorites(favorites: MutableList<FavoritesItem>) {
        repository.saveFavorites(favorites)
    }

    val hiddenItems: LiveData<List<Searchable>> = repository.hiddenItems
    val pinnedCalendarEvents: LiveData<List<CalendarEvent>> = repository.pinnedCalendarEvents
}