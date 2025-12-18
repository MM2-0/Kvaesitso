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
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.searchable.VisibilityLevel
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
    private val searchableRepository: SavableSearchableRepository by inject()

    private val widgetConfig = MutableStateFlow(CalendarWidgetConfig())

    val calendarEvents = mutableStateOf<List<CalendarEvent>>(emptyList())
    val pinnedCalendarEvents =
        favoritesService.getFavorites(
            includeTypes = listOf("calendar", "tasks.org", "plugin.calendar"),
            minPinnedLevel = PinnedLevel.AutomaticallySorted,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val nextEvents = mutableStateOf<List<CalendarEvent>>(emptyList())
    var availableDates = listOf(LocalDate.now())

    private val permissionsManager: PermissionsManager by inject()
    val hasPermission = permissionsManager.hasPermission(PermissionGroup.Calendar)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private var showRunningPastDayEvents = false
    private var showRunningTasks = false
    val hiddenPastEvents = mutableStateOf(0)
    val hiddenRunningTasks = mutableStateOf(0)

    val selectedDate = mutableStateOf(LocalDate.now())

    fun updateWidget(widget: CalendarWidget) {
        widgetConfig.value = widget.config
    }

    private var upcomingEvents: List<CalendarEvent> = emptyList()
        set(value) {
            field = value
            val dates = value.flatMap {
                val startDate =
                    it.startTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                val endDate =
                    Instant.ofEpochMilli(it.endTime).atZone(ZoneId.systemDefault()).toLocalDate()
                return@flatMap listOfNotNull(
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
        showRunningTasks = false
        if (dates.contains(date)) {
            selectedDate.value = date
            updateEvents()
        }
    }

    fun showAllEvents() {
        showRunningPastDayEvents = true
        updateEvents()
    }

    fun showAllTasks() {
        showRunningTasks = true
        updateEvents()
    }

    fun createEvent(context: Context) {
        val intent = Intent(Intent.ACTION_EDIT)
        intent.data = CalendarContract.Events.CONTENT_URI
        val zoneOffset = OffsetDateTime.now().offset
        val beginTime = selectedDate.value.atTime(12, 0).toInstant(zoneOffset).toEpochMilli()
        val endTime = selectedDate.value.atTime(13, 0).toInstant(zoneOffset).toEpochMilli()
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
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
            if (it.isTask && it.isCompleted == true) {
                it.endTime >= dayStart && it.endTime < dayEnd
            } else {
                it.endTime >= dayStart && (it.startTime ?: 0L) < dayEnd
            }
        }

        val startOfDay = date.atStartOfDay().toEpochSecond(offset) * 1000
        val startOfNextDay = date.atStartOfDay().plusDays(1).toEpochSecond(offset) * 1000

        if (!showRunningPastDayEvents) {
            val totalCount = events.size


            events = events.filter {
                ((it.startTime != null && it.startTime!! >= startOfDay) ||
                        it.endTime < startOfNextDay) || it.isTask
            }

            val hiddenCount = totalCount - events.size
            hiddenPastEvents.value = hiddenCount
        } else {
            hiddenPastEvents.value = 0
        }
        if (!showRunningTasks) {
            val totalCount = events.size

            events = events.filter {
                ((it.startTime != null && it.startTime!! >= startOfDay) ||
                        it.endTime < startOfNextDay) || !it.isTask
            }

            val hiddenCount = totalCount - events.size
            hiddenRunningTasks.value = hiddenCount
        } else {
            hiddenRunningTasks.value = 0
        }

        calendarEvents.value = events
        val e = this.upcomingEvents
        if (events.isEmpty() && e.isNotEmpty()) {
            nextEvents.value = listOfNotNull(
                e.sortedBy { if (it.isTask) it.endTime else (it.startTime ?: 0L) }
                    .find { now < if (it.isTask) it.endTime else (it.startTime ?: 0L) }
            )
        } else {
            nextEvents.value = emptyList()
        }
    }

    suspend fun onActive() {
        selectDate(LocalDate.now())
        widgetConfig.collectLatest { config ->
            calendarRepository.findMany(
                from = System.currentTimeMillis(),
                to = System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000L,
                excludeAllDayEvents = !config.allDayEvents,
                excludeCalendars = config.excludedCalendarIds ?: config.legacyExcludedCalendarIds?.map { "local:$it" } ?: emptyList(),
            ).collectLatest { events ->
                searchableRepository.getKeys(
                    includeTypes = listOf("calendar", "tasks.org", "plugin.calendar"),
                    maxVisibility = VisibilityLevel.SearchOnly,
                    limit = 9999,
                ).collectLatest { hidden ->
                    upcomingEvents = events
                        .filter {
                            !hidden.contains(it.key) && !(!config.completedTasks && it.isCompleted == true)
                        }.sortedBy { it.startTime ?: it.endTime }
                }
            }

        }
    }

    fun requestCalendarPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Calendar)
    }
}