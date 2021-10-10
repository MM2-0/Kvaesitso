package de.mm20.launcher2.calendar

import android.content.Context
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarRepository(
    val context: Context,
    hiddenItemsRepository: HiddenItemsRepository
) : BaseSearchableRepository() {

    val calendarEvents = MediatorLiveData<List<CalendarEvent>?>()
    val upcomingCalendarEvents = MutableLiveData<List<CalendarEvent>>(emptyList())

    private val allEvents = MutableLiveData<List<CalendarEvent>?>(emptyList())
    private val hiddenItemKeys = hiddenItemsRepository.hiddenItemsKeys

    init {
        calendarEvents.addSource(hiddenItemKeys) { keys ->
            calendarEvents.value = allEvents.value?.filter { !keys.contains(it.key) }
        }
        calendarEvents.addSource(allEvents) { e ->
            calendarEvents.value = e?.filter { hiddenItemKeys.value?.contains(it.key) != true }
        }
        hiddenItemKeys.observeForever {
            requestCalendarUpdate()
        }

    }

    fun requestCalendarUpdate() {
        launch {
            val unselectedCalendars = LauncherPreferences.instance.unselectedCalendars
            val hideAlldayEvents = LauncherPreferences.instance.calendarHideAllday

            val now = System.currentTimeMillis()
            val end = now + 14 * 24 * 60 * 60 * 1000L
            val events = withContext(Dispatchers.IO) {
                CalendarEvent.search(
                    context = context,
                    query = "",
                    intervalStart = now,
                    intervalEnd = end,
                    limit = 700,
                    hideAllDayEvents = hideAlldayEvents,
                    unselectedCalendars = unselectedCalendars,
                    hiddenEvents = hiddenItemKeys.value?.mapNotNull {
                        if (it.startsWith("calendar")) it.substringAfterLast("/").toLong()
                        else null
                    } ?: emptyList()
                )
            }
            upcomingCalendarEvents.value = events
        }
    }

    override suspend fun search(query: String) {
        if (query.isBlank()) {
            allEvents.value = null
            return
        }
        val startTime = System.currentTimeMillis()
        val endTime = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
        val events = withContext(Dispatchers.IO) {
            CalendarEvent.search(context, query, startTime, endTime)
        }
        allEvents.value = events
    }
}