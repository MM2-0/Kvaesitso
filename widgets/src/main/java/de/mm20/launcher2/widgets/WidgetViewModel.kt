package de.mm20.launcher2.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetViewModel(
    private val widgetRepository: WidgetRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {


    suspend fun getWidgets(): List<Widget> {
        return withContext(viewModelScope.coroutineContext + Dispatchers.IO) {
            widgetRepository.getWidgets()
        }
    }

    fun saveWidgets(widgets: List<Widget>) {
        viewModelScope.launch(Dispatchers.IO) {
            widgetRepository.saveWidgets(widgets)
        }
    }

    fun getInternalWidgets(): List<Widget> {
        return widgetRepository.getInternalWidgets()
    }

    fun requestCalendarUpdate() {
        calendarRepository.requestCalendarUpdate()
    }
}