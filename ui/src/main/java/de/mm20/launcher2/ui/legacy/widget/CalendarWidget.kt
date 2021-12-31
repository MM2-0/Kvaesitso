package de.mm20.launcher2.ui.legacy.widget

import android.animation.LayoutTransition
import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.ktx.lifecycleOwner
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewCalendarWidgetBinding
import de.mm20.launcher2.ui.launcher.widgets.calendar.CalendarWidgetVM
import de.mm20.launcher2.ui.legacy.data.InformationText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class CalendarWidget : LauncherWidget {

    override val canResize: Boolean
        get() = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    private fun formatDay(day: LocalDate): String {
        val today = LocalDate.now()
        return when {
            today == day -> context.getString(R.string.date_today)
            today.plusDays(1) == day -> context.getString(R.string.date_tomorrow)
            else -> DateUtils.formatDateTime(
                context,
                day.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
            )
        }
    }

    private val binding =
        ViewCalendarWidgetBinding.inflate(LayoutInflater.from(context), this, true)

    private val viewModel: CalendarWidgetVM by (context as AppCompatActivity).viewModels()

    init {
        clipToPadding = false
        clipChildren = false

        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.onActive()
            }
        }

        binding.calendarNewEvent.setOnClickListener {
            viewModel.createEvent(context)
        }

        binding.calendarDate.setOnClickListener {
            val menu = PopupMenu(context, binding.calendarDate)
            val availableDates = viewModel.availableDates
            for ((i, d) in availableDates.withIndex()) {
                menu.menu.add(
                    Menu.NONE,
                    i,
                    Menu.NONE,
                    formatDay(d)
                )
            }
            menu.setOnMenuItemClickListener {
                viewModel.selectDate(availableDates[it.itemId])
                true
            }
            menu.show()
        }

        binding.calendarOpenApp.setOnClickListener {
            viewModel.openCalendarApp(context)
        }

        binding.calendarDateNext.setOnClickListener {
            viewModel.nextDay()
        }
        binding.calendarDatePrev.setOnClickListener {
            viewModel.previousDay()
        }

        val calendarEvents = viewModel.calendarEvents
        val pinnedCalendarEvents = viewModel.pinnedCalendarEvents
        val hiddenPastEvents = viewModel.hiddenPastEvents
        val selectedDate = viewModel.selectedDate

        calendarEvents.observe(context as AppCompatActivity, {
            updateEventList(it, hiddenPastEvents.value ?: 0)
        })
        pinnedCalendarEvents.observe(context as AppCompatActivity) {
            binding.calendarWidgetPinnedList.submitItems(it)
            if (it.isEmpty()) {
                binding.calendarWidgetPinnedList.visibility = View.GONE
                binding.calendarUpcomingEventsTitle.visibility = View.GONE
            } else {
                binding.calendarWidgetPinnedList.visibility = View.VISIBLE
                binding.calendarUpcomingEventsTitle.visibility = View.VISIBLE
            }
        }

        selectedDate.observe(context as AppCompatActivity) {
            binding.calendarDate.text = formatDay(it)
        }

        binding.calendarWidgetRoot.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }
    }

    private fun updateEventList(
        events: List<CalendarEvent>,
        hiddenPastDayEvents: Int
    ) {
        val items = events.toMutableList<Searchable>()

        if (events.isEmpty()) {
            items.add(
                InformationText(context.getString(R.string.calendar_widget_no_events))
            )
        }

        if (hiddenPastDayEvents > 0) {
            items.add(
                InformationText(
                    resources.getQuantityString(
                        R.plurals.calendar_widget_running_events,
                        hiddenPastDayEvents,
                        hiddenPastDayEvents
                    )
                ) {
                    viewModel.showAllEvents()
                })
        }

        binding.calendarWidgetList.submitItems(items)
    }


    override val name: String
        get() = resources.getString(R.string.widget_name_calendar)


    companion object {
        const val ID = "calendar"
    }
}