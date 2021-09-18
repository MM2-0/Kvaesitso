package de.mm20.launcher2.widgets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetViewModel(app: Application) : AndroidViewModel(app) {

    private val widgetRepository: WidgetRepository by lazy {
        WidgetRepository(app)
    }

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

    private val calendarRepository: CalendarRepository by lazy {
        CalendarRepository.getInstance(app)
    }

    fun requestCalendarUpdate() {
        calendarRepository.requestCalendarUpdate()
    }
}