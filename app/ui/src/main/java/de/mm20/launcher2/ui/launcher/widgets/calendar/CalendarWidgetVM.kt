package de.mm20.launcher2.ui.launcher.widgets.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.searchable.SearchableRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.CalendarWidgetConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Integer.min
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.max

class CalendarWidgetVM : ViewModel(), KoinComponent {

    private val calendarRepository: CalendarRepository by inject()
    private val favoritesService: FavoritesService by inject()
    private val searchableRepository: SearchableRepository by inject()

    private val widgetConfig = MutableStateFlow(CalendarWidgetConfig())

    val calendarEvents = mutableStateOf<List<CalendarEvent>>(emptyList())
    val pinnedCalendarEvents =
        favoritesService.getFavorites(
            includeTypes = listOf(CalendarEvent.Domain),
            automaticallySorted = true,
            manuallySorted = true,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val nextEvents = mutableStateOf<List<CalendarEvent>>(emptyList())
    var availableDates = listOf(LocalDate.now())

    private val permissionsManager: PermissionsManager by inject()
    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private var showRunningPastDayEvents = false
    val hiddenPastEvents = mutableStateOf(0)

    val selectedDate = mutableStateOf(LocalDate.now())

    fun updateWidget(widget: CalendarWidget) {
        widgetConfig.value = widget.config
    }

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
            hiddenPastEvents.value = hiddenCount
        } else {
            hiddenPastEvents.value = 0
        }

        calendarEvents.value = events
        val e = this.upcomingEvents
        if (events.isEmpty() && e.isNotEmpty()) {
            nextEvents.value = listOf(e[0])
        } else {
            nextEvents.value = emptyList()
        }
    }

    suspend fun onActive() {
        selectDate(LocalDate.now())
        widgetConfig.collectLatest { config ->
            calendarRepository.getUpcomingEvents(
                excludeAllDayEvents = !config.allDayEvents,
                excludeCalendars = config.excludedCalendarIds,
            ).collectLatest { events ->
                searchableRepository.getKeys(
                    includeTypes = listOf(CalendarEvent.Domain),
                    hidden = true,
                    limit = 9999,
                ).collectLatest { hidden ->
                    upcomingEvents = events.filter { !hidden.contains(it.key) }
                }
            }

        }
    }

    fun requestCalendarPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Calendar)
    }
}