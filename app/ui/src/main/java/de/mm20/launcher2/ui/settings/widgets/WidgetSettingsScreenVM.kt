package de.mm20.launcher2.ui.settings.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.widgets.WidgetRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetSettingsScreenVM : ViewModel(), KoinComponent {


    private val widgetRepository: WidgetRepository by inject()
    private val dataStore: LauncherDataStore by inject()

    val calendarWidget = widgetRepository.isCalendarWidgetEnabled().asLiveData()
    val musicWidget = widgetRepository.isMusicWidgetEnabled().asLiveData()
    val weatherWidget = widgetRepository.isWeatherWidgetEnabled().asLiveData()
    val favoritesWidget = widgetRepository.isFavoritesWidgetEnabled().asLiveData()
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