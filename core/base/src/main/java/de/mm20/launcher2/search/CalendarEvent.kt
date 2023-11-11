package de.mm20.launcher2.search

import android.content.Context
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import java.text.SimpleDateFormat

interface CalendarEvent: SavableSearchable {
    val color: Int?
    val startTime: Long
    val endTime: Long
    val allDay: Boolean
    val description: String?
    val location: String?
    val attendees: List<String>


    override val preferDetailsOverLaunch: Boolean
        get() = true


    override suspend fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        val df = SimpleDateFormat("dd")
        return StaticLauncherIcon(
            foregroundLayer = TextLayer(
                text = df.format(startTime),
                color = color ?: 0,
            ),
            backgroundLayer = ColorLayer(color ?: 0)
        )
    }

    fun openLocation(context: Context) {}
}