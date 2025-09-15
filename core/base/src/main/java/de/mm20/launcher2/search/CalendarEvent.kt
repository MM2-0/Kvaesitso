package de.mm20.launcher2.search

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.ktx.tryStartActivity
import java.net.URLEncoder
import java.text.SimpleDateFormat

interface CalendarEvent : SavableSearchable {
    val color: Int?
    val startTime: Long?
    val endTime: Long
    val allDay: Boolean
    val description: String?
    val calendarName: String?
    val location: String?
    val attendees: List<String>
    val isCompleted: Boolean?
        get() = null

    val isTask: Boolean
        get() = isCompleted != null


    override val preferDetailsOverLaunch: Boolean
        get() = true


    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val df = SimpleDateFormat("dd")
        return StaticLauncherIcon(
            foregroundLayer = TextLayer(
                text = df.format(startTime ?: endTime),
                color = color ?: 0,
            ),
            backgroundLayer = ColorLayer(color ?: 0)
        )
    }

    fun openLocation(context: Context) {
        if (location == null) return
        context.tryStartActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(
                    "geo:0,0?q=${
                        URLEncoder.encode(
                            location,
                            "utf8"
                        )
                    }".toUri()
                )
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}