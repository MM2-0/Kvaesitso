package de.mm20.launcher2.ui.legacy.search

import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import androidx.transition.Scene
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.FavoriteSwipeAction
import de.mm20.launcher2.ui.legacy.view.HideSwipeAction
import de.mm20.launcher2.ui.legacy.view.SwipeCardView
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class CalendarListRepresentation : Representation {
    override fun getScene(
        rootView: SearchableView,
        searchable: Searchable,
        previousRepresentation: Int?
    ): Scene {
        val calendarEvent = searchable as CalendarEvent
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_calendar_list, rootView.context)
        scene.setEnterAction {
            with(rootView) {
                findViewById<TextView>(R.id.calendarLabel).text = calendarEvent.label

                findViewById<View>(R.id.calendarColor).setBackgroundColor(
                    CalendarEvent.getDisplayColor(
                        context,
                        calendarEvent.color
                    )
                )
                findViewById<SwipeCardView>(R.id.calendarEventCard).also {
                    it.leftAction = FavoriteSwipeAction(context, calendarEvent)
                    it.rightAction = HideSwipeAction(context, calendarEvent)
                    it.setOnClickListener {
                        rootView.representation = SearchableView.REPRESENTATION_FULL
                    }
                }
                val isToday =
                    DateUtils.isToday(calendarEvent.startTime) && DateUtils.isToday(calendarEvent.endTime)
                findViewById<TextView>(R.id.eventDateTime).text = if (isToday) {
                    if (calendarEvent.allDay) {
                        context.getString(R.string.calendar_event_allday)
                    } else {
                        DateUtils.formatDateRange(
                            context,
                            calendarEvent.startTime,
                            calendarEvent.endTime,
                            DateUtils.FORMAT_SHOW_TIME
                        )
                    }
                } else {
                    if (calendarEvent.allDay) {
                        DateUtils.formatDateRange(
                            context,
                            calendarEvent.startTime,
                            calendarEvent.endTime,
                            DateUtils.FORMAT_SHOW_DATE
                        )
                    } else {
                        DateUtils.formatDateRange(
                            context,
                            calendarEvent.startTime,
                            calendarEvent.endTime,
                            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
                        )
                    }
                }
            }
        }
        return scene
    }

    private fun formatTime(context: Context, event: CalendarEvent): String {
        val df = DateFormat.getTimeInstance(DateFormat.SHORT)
        return when {
            event.startTime == event.endTime -> {
                df.format(Date(event.startTime))
            }
            event.allDay -> {
                context.getString(R.string.calendar_event_allday)
            }
            else -> {
                DateUtils.formatDateRange(
                    context, event.startTime, event.endTime,
                    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                )
            }
        }
    }

    private fun formatDate(event: CalendarEvent): Pair<String, String> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = event.startTime
        val today = Calendar.getInstance()
        today.timeInMillis = System.currentTimeMillis()
        val line1 =
            if (calendar[Calendar.YEAR] == today[Calendar.YEAR] && calendar[Calendar.MONTH] == calendar[Calendar.MONTH]) {
                SimpleDateFormat("EEE").format(event.startTime)
            } else SimpleDateFormat("MMM").format(event.startTime)
        val line2 = calendar[Calendar.DAY_OF_MONTH].toString()
        return line1 to line2
    }

}