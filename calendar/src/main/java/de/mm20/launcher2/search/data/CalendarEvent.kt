package de.mm20.launcher2.search.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import java.text.SimpleDateFormat

class CalendarEvent(
    override val label: String,
    val id: Long,
    val color: Int,
    val startTime: Long,
    val endTime: Long,
    val allDay: Boolean,
    val location: String,
    val attendees: List<String>,
    val description: String,
    val calendar: Long
) : Searchable() {

    override val key: String
        get() = "calendar://$id"


    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val df = SimpleDateFormat("dd")
        return StaticLauncherIcon(
            foregroundLayer = TextLayer(
                text = df.format(startTime),
                color = color
            ),
            backgroundLayer = ColorLayer(color)
        )
    }

    override fun getLaunchIntent(context: Context): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return Intent(Intent.ACTION_VIEW).setData(uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

data class UserCalendar(
    val id: Long,
    val name: String,
    val owner: String,
    val color: Int
)