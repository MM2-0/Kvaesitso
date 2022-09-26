package de.mm20.launcher2.ui.settings.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.widgets.WidgetRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WidgetSettingsScreenVM: ViewModel(), KoinComponent {

    private val widgetRepository: WidgetRepository by inject()

    val calendarWidget = widgetRepository.isCalendarWidgetEnabled().asLiveData()
    val musicWidget = widgetRepository.isMusicWidgetEnabled().asLiveData()
    val weatherWidget = widgetRepository.isWeatherWidgetEnabled().asLiveData()
    val favoritesWidget = widgetRepository.isFavoritesWidgetEnabled().asLiveData()
}