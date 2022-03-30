package de.mm20.launcher2.ui.launcher.widgets.favorites

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FavoritesWidgetVM: ViewModel(), KoinComponent {
    private val favoritesRepository: FavoritesRepository by inject()
    private val widgetRepository: WidgetRepository by inject()
    val favorites = MutableLiveData<List<Searchable>>(emptyList())

    init {
        viewModelScope.launch {
                widgetRepository.isCalendarWidgetEnabled().collectLatest { excludeCalendar ->
                    favoritesRepository.getFavorites(excludeCalendarEvents = excludeCalendar).collectLatest {
                        favorites.value = it
                    }
                }
        }
    }
}