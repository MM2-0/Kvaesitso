package de.mm20.launcher2.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.mm20.launcher2.search.data.CalendarEvent

class CalendarViewModel(app:Application): AndroidViewModel(app) {
    val calendarEvents: LiveData<List<CalendarEvent>?> = CalendarRepository.getInstance(app).calendarEvents
    val upcomingCalendarEvents: LiveData<List<CalendarEvent>> = CalendarRepository.getInstance(app).upcomingCalendarEvents
}