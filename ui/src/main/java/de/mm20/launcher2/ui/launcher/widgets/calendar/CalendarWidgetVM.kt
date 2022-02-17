package de.mm20.launcher2.ui.launcher.widgets.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.CalendarEvent
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Integer.min
import java.time.*
import java.util.*
import kotlin.math.max

class CalendarWidgetVM : ViewModel(), KoinComponent {

    private val calendarRepository: CalendarRepository by inject()
    private val favoritesRepository: FavoritesRepository by inject()

    val calendarEvents = MutableLiveData<List<CalendarEvent>>(emptyList())
    val pinnedCalendarEvents =
        favoritesRepository.getPinnedCalendarEvents().asLiveData(viewModelScope.coroutineContext)
    var availableDates = listOf(LocalDate.now())

    private val permissionsManager: PermissionsManager by inject()
    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar).asLiveData()

    private var showRunningPastDayEvents = false
    val hiddenPastEvents = MutableLiveData(0)

    val selectedDate = MutableLiveData(LocalDate.now())

    private var upcomingEvents: List<CalendarEvent> = emptyList()
        set(value) {
            field = value
            val dates = value.flatMap {
                val startDate =
                    Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
                val endDate =
                    Instant.ofEpochMilli(it.endTime).atZone(ZoneId.systemDefault()).toLocalDate()
                return@flatMap listOf(
                    startDate,
                    endDate
                )
            }.union(listOf(LocalDate.now()))
                .distinct()
                .sorted()
            availableDates = dates
            val date = selectedDate.value?.takeIf { dates.contains(it) } ?: LocalDate.now()
            selectDate(date)
        }


    fun nextDay() {
        val dates = availableDates
        val date = selectedDate.value ?: return
        val currentIndex = dates.indexOf(date)
        val index = min(currentIndex + 1, dates.lastIndex)
        selectDate(dates[index])
    }

    fun previousDay() {
        val dates = availableDates
        val date = selectedDate.value ?: return
        val currentIndex = dates.indexOf(date)
        val index = max(currentIndex - 1, 0)
        selectDate(dates[index])
    }

    fun selectDate(date: LocalDate) {
        val dates = availableDates
        showRunningPastDayEvents = false
        if (dates.contains(date)) {
            selectedDate.value = date
            updateEvents()
        }
    }

    fun showAllEvents() {
        showRunningPastDayEvents = true
        updateEvents()
    }

    fun createEvent(context: Context) {
        val intent = Intent(Intent.ACTION_EDIT)
        intent.data = CalendarContract.Events.CONTENT_URI
        context.tryStartActivity(intent)
    }

    fun openCalendarApp(context: Context) {
        val startMillis = System.currentTimeMillis()
        val builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        ContentUris.appendId(builder, startMillis)
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(builder.build())
        context.tryStartActivity(intent)
    }

    private fun updateEvents() {
        val date = selectedDate.value ?: return
        val now = System.currentTimeMillis()
        val offset = OffsetDateTime.now().offset
        val dayStart = max(now, date.atStartOfDay().toEpochSecond(offset) * 1000)
        val dayEnd = date.plusDays(1).atStartOfDay().toEpochSecond(offset) * 1000
        var events = upcomingEvents.filter {
            it.endTime >= dayStart && it.startTime < dayEnd
        }

        if (!showRunningPastDayEvents) {
            val totalCount = events.size

            events = events.filter {
                it.startTime >= date.atStartOfDay().toEpochSecond(offset) * 1000 ||
                        it.endTime < date.atStartOfDay().plusDays(1).toEpochSecond(offset) * 1000
            }

            val hiddenCount = totalCount - events.size
            hiddenPastEvents.postValue(hiddenCount)
        } else {
            hiddenPastEvents.postValue(0)
        }

        calendarEvents.postValue(events)
    }

    suspend fun onActive() {
        calendarRepository.getUpcomingEvents().collectLatest {
            upcomingEvents = it
        }
    }

    fun requestCalendarPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Calendar)
    }
}