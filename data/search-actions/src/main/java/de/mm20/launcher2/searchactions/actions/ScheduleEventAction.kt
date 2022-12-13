package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import de.mm20.launcher2.ktx.tryStartActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ScheduleEventAction(
    override val label: String,
    val date: LocalDate,
    val time: LocalTime?,
) : SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Calendar
    override val iconColor: Int = 0
    override val customIcon: String? = null
    override fun start(context: Context) {

        val startTime = date.let {
            if (time != null) it.atTime(time)
            else it.atTime(0, 0)
        }.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000L

        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = "vnd.android.cursor.dir/event"
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            if (time == null) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        }
        context.tryStartActivity(intent)
    }
}