package de.mm20.launcher2.ui.legacy.widget

import android.animation.LayoutTransition
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.LiveData
import de.mm20.launcher2.calendar.CalendarViewModel
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.ui.legacy.data.InformationText
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewCalendarWidgetBinding
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.*
import kotlin.math.max
import kotlin.math.min

class CalendarWidget : LauncherWidget {

    override val canResize: Boolean
        get() = false

    private val calendarEvents: LiveData<List<CalendarEvent>>
    private val pinnedCalendarEvents: LiveData<List<CalendarEvent>>

    private val zoneOffset = Calendar.getInstance().timeZone.getOffset(System.currentTimeMillis())
    private var selectedDay = 0L
        set(value) {
            field = value
            binding.calendarDate.text = formatDay(value)
            updateEventList()
        }

    private var availableDays: List<Long> = listOf(0L)
        set(value) {
            if (value.indexOf(selectedDay) == -1) selectedDay = 0L
            field = value
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private fun formatDay(day: Long): String {
        return when (day) {
            0L -> context.getString(R.string.date_today)
            1L -> context.getString(R.string.date_tomorrow)
            else -> DateUtils.formatDateTime(context, (getToday() + day) * (1000 * 60 * 60 * 24), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
        }
    }

    private val binding = ViewCalendarWidgetBinding.inflate(LayoutInflater.from(context), this, true)


    init {
        clipToPadding = false
        clipChildren = false
        binding.calendarNewEvent.setOnClickListener {
            val intent = Intent(Intent.ACTION_EDIT)
            intent.data = CalendarContract.Events.CONTENT_URI
            ActivityStarter.start(context, this, intent = intent)
        }

        binding.calendarDate.setOnClickListener {
            val menu = PopupMenu(context, binding.calendarDate)
            for (d in availableDays) {
                menu.menu.add(
                        Menu.NONE,
                        d.toInt(),
                        Menu.NONE,
                        formatDay(d)
                )
            }
            menu.setOnMenuItemClickListener {
                selectedDay = it.itemId.toLong()
                true
            }
            menu.show()
        }

        binding.calendarOpenApp.setOnClickListener {
            val startMillis = System.currentTimeMillis()
            val builder = CalendarContract.CONTENT_URI.buildUpon()
            builder.appendPath("time")
            ContentUris.appendId(builder, startMillis)
            val intent = Intent(Intent.ACTION_VIEW)
                    .setData(builder.build())
            ActivityStarter.start(context, binding.calendarWidgetRoot, intent = intent)
        }

        binding.calendarDateNext.setOnClickListener {
            val i = min(availableDays.lastIndex - 1, availableDays.indexOf(selectedDay))
            selectedDay = availableDays[i + 1]
        }
        binding.calendarDatePrev.setOnClickListener {
            val i = max(1, availableDays.indexOf(selectedDay))
            selectedDay = availableDays[i - 1]
        }

        val viewModel: CalendarViewModel by (context as AppCompatActivity).viewModel()
        val favViewModel: FavoritesViewModel by (context as AppCompatActivity).viewModel()
        calendarEvents = viewModel.upcomingCalendarEvents
        pinnedCalendarEvents = favViewModel.pinnedCalendarEvents

        calendarEvents.observe(context as AppCompatActivity, {
            if (!PermissionsManager.checkPermission(context, PermissionsManager.CALENDAR)) {
                binding.calendarWidgetList.submitItems(listOf(
                        MissingPermission(
                                context.getString(R.string.permission_calendar_widget),
                                PermissionsManager.CALENDAR
                        )
                ))
                return@observe
            }
            val today = getToday()
            availableDays = it
                    .map { ((it.startTime + zoneOffset) / (1000 * 60 * 60 * 24)) - today }
                    .union(it.map { ((it.endTime + zoneOffset) / (1000 * 60 * 60 * 24)) - today })
                    .union(listOf(0L))
                    .toSet().toList().sorted()
            updateEventList()
        })
        pinnedCalendarEvents.observe(context as AppCompatActivity) {
            val today = getToday()
            binding.calendarWidgetPinnedList.submitItems(it.filter {
                it.endTime > System.currentTimeMillis() &&
                        (it.startTime + zoneOffset) / (1000 * 60 * 60 * 24) != today &&
                        (it.endTime + zoneOffset) / (1000 * 60 * 60 * 24) != today
            }.sortedBy { it.startTime })
            if (it.isEmpty()) {
                binding.calendarWidgetPinnedList.visibility = View.GONE
                binding.calendarUpcomingEventsTitle.visibility = View.GONE
            } else {
                binding.calendarWidgetPinnedList.visibility = View.VISIBLE
                binding.calendarUpcomingEventsTitle.visibility = View.VISIBLE
            }
        }

        binding.calendarWidgetRoot.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }
    }

    private fun getToday(): Long {
        return (System.currentTimeMillis() + zoneOffset) / (1000 * 60 * 60 * 24)
    }

    private fun updateEventList(includePastEvents: Boolean = false) {
        val today = getToday()
        val events: MutableList<Searchable> = calendarEvents.value?.filter { (it.startTime + zoneOffset) / (1000 * 60 * 60 * 24) == today + selectedDay || (it.endTime + zoneOffset) / (1000 * 60 * 60 * 24) == today + selectedDay }
                ?.toMutableList() ?: mutableListOf()

        if (events.isEmpty()) {
            events.add(
                    InformationText(context.getString(R.string.calendar_widget_no_events))
            )
        }
        val pastEvents = calendarEvents.value?.filter { (it.startTime + zoneOffset) / (1000 * 60 * 60 * 24) < today + selectedDay && (it.endTime + zoneOffset) / (1000 * 60 * 60 * 24) > today + selectedDay }

        if (pastEvents?.isNotEmpty() == true) {
            if (includePastEvents) {
                events.addAll(pastEvents)
            } else {
                events.add(InformationText(resources.getQuantityString(R.plurals.calendar_widget_running_events, pastEvents.size, pastEvents.size)) {
                    updateEventList(true)
                })
            }
        }

        binding.calendarWidgetList.submitItems(events)
    }


    override val name: String
        get() = resources.getString(R.string.widget_name_calendar)


    companion object {
        const val ID = "calendar"
    }
}