package de.mm20.launcher2.calendar

import android.content.Context
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

interface CalendarRepository {
    fun search(query: String): Flow<List<CalendarEvent>>

    fun getUpcomingEvents(): Flow<List<CalendarEvent>>
}

class CalendarRepositoryImpl(
    private val context: Context,
    hiddenItemsRepository: HiddenItemsRepository
) : CalendarRepository {

    private val hiddenItems = hiddenItemsRepository.hiddenItemsKeys

    override fun search(query: String): Flow<List<CalendarEvent>> = channelFlow {
        if (query.isBlank()) {
            send(emptyList())
            return@channelFlow
        }
        val events = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            CalendarEvent.search(
                context,
                query,
                intervalStart = now,
                intervalEnd = now + 14 * 24 * 60 * 60 * 1000L,
            )
        }

        hiddenItems.collectLatest { hiddenItems ->
            val calendarResults = withContext(Dispatchers.IO) {
                events.filter { !hiddenItems.contains(it.key) }
            }
            send(calendarResults)
        }

    }

    override fun getUpcomingEvents(): Flow<List<CalendarEvent>> = channelFlow {
        val unselectedCalendars = callbackFlow {
            val unregister =
                LauncherPreferences.instance.doOnPreferenceChange("unselected_calendars") {
                    trySendBlocking(LauncherPreferences.instance.unselectedCalendars)
                }
            trySendBlocking(LauncherPreferences.instance.unselectedCalendars)
            awaitClose {
                unregister()
            }
        }

        val hideAlldayEvents = callbackFlow {
            val unregister =
                LauncherPreferences.instance.doOnPreferenceChange("calendar_hide_allday") {
                    trySendBlocking(LauncherPreferences.instance.calendarHideAllday)
                }
            trySendBlocking(LauncherPreferences.instance.calendarHideAllday)
            awaitClose {
                unregister()
            }
        }

        merge(unselectedCalendars, hideAlldayEvents, hiddenItems).collectLatest {
            val now = System.currentTimeMillis()
            val end = now + 14 * 24 * 60 * 60 * 1000L
            val events = withContext(Dispatchers.IO) {
                CalendarEvent.search(
                    context = context,
                    query = "",
                    intervalStart = now,
                    intervalEnd = end,
                    limit = 700,
                    hideAllDayEvents = LauncherPreferences.instance.calendarHideAllday,
                    unselectedCalendars = LauncherPreferences.instance.unselectedCalendars
                ).filter {
                    !hiddenItems.value.contains(it.key)
                }
            }
            send(events)
        }
    }

    var unselectedCalendars: List<Long>
        get() = LauncherPreferences.instance.unselectedCalendars
        set(value) {
            LauncherPreferences.instance.unselectedCalendars = value
        }

}