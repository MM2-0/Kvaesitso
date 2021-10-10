package de.mm20.launcher2.calendar

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.search.data.CalendarEvent

class CalendarViewModel(
    calendarRepository: CalendarRepository
): ViewModel() {
    val calendarEvents: LiveData<List<CalendarEvent>?> = calendarRepository.calendarEvents
    val upcomingCalendarEvents: LiveData<List<CalendarEvent>> = calendarRepository.upcomingCalendarEvents
}