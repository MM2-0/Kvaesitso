package de.mm20.launcher2.search.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TextLayer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.SavableSearchable
import java.text.SimpleDateFormat

data class CalendarEvent(
    override val label: String,
    val id: Long,
    val color: Int,
    val startTime: Long,
    val endTime: Long,
    val allDay: Boolean,
    val location: String,
    val attendees: List<String>,
    val description: String,
    val calendar: Long,
    override val labelOverride: String? = null,
) : SavableSearchable {

    override val domain: String = Domain

    override val key: String
        get() = "$domain://$id"

    override val preferDetailsOverLaunch: Boolean = true

    override fun overrideLabel(label: String): CalendarEvent {
        return this.copy(labelOverride = label)
    }

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

    private fun getLaunchIntent(): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return Intent(Intent.ACTION_VIEW).setData(uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(), options)
    }

    companion object {
        const val Domain = "calendar"
    }
}

data class UserCalendar(
    val id: Long,
    val name: String,
    val owner: String,
    val color: Int
)