package de.mm20.launcher2.ui.launcher.search.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import java.net.URLEncoder

class CalendarItemVM(
    private val calendarEvent: CalendarEvent
) : SearchableItemVM(calendarEvent) {

    fun getSummary(context: Context): String {
        val isToday =
            DateUtils.isToday(calendarEvent.startTime) && DateUtils.isToday(calendarEvent.endTime)
        return if (isToday) {
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

    fun formatTime(context: Context): String {
        if (calendarEvent.allDay) return DateUtils.formatDateRange(
            context,
            calendarEvent.startTime,
            calendarEvent.endTime,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY
        )
        return DateUtils.formatDateRange(
            context,
            calendarEvent.startTime,
            calendarEvent.endTime,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY
        )

    }

    fun openLocation(context: Context) {
        context.tryStartActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(
                    Uri.parse(
                        "geo:0,0?q=${
                            URLEncoder.encode(
                                calendarEvent.location,
                                "utf8"
                            )
                        }"
                    )
                )
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}