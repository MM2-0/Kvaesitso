package de.mm20.launcher2.ui.settings.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.FavoritesWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetSettingsScreenVM : ViewModel(), KoinComponent {


    private val widgetRepository: WidgetRepository by inject()
    private val dataStore: LauncherDataStore by inject()

    val calendarWidget = widgetRepository.exists(CalendarWidget.Type).asLiveData()
    val musicWidget = widgetRepository.exists(MusicWidget.Type).asLiveData()
    val weatherWidget = widgetRepository.exists(WeatherWidget.Type).asLiveData()
    val favoritesWidget = widgetRepository.exists(FavoritesWidget.Type).asLiveData()
    val editButton = dataStore.data.map { it.widgets.editButton }.asLiveData()
    fun setEditButton(editButton: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWidgets(
                        it.widgets.toBuilder()
                            .setEditButton(editButton)
                    )
                    .build()
            }
        }
    }
}