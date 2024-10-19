package de.mm20.launcher2.calendar.providers

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import de.mm20.launcher2.calendar.AndroidCalendarEventSerializer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableSerializer
import java.net.URLEncoder

internal data class AndroidCalendarEvent(
    override val label: String,
    val id: Long,
    override val color: Int,
    override val startTime: Long,
    override val endTime: Long,
    override val allDay: Boolean,
    override val location: String?,
    override val attendees: List<String>,
    override val description: String?,
    internal val calendarId: Long,
    override val calendarName: String?,
    override val labelOverride: String? = null,
) : CalendarEvent {

    override val domain: String = Domain

    override val key: String
        get() = "$domain://$id"
    override fun overrideLabel(label: String): AndroidCalendarEvent {
        return this.copy(labelOverride = label)
    }

    private fun getLaunchIntent(): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return Intent(Intent.ACTION_VIEW).setData(uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return context.tryStartActivity(getLaunchIntent(), options)
    }

    override fun openLocation(context: Context) {
        if (location == null) return
        context.tryStartActivity(
            Intent(Intent.ACTION_VIEW)
                .setData(
                    Uri.parse(
                        "geo:0,0?q=${
                            URLEncoder.encode(
                                location,
                                "utf8"
                            )
                        }"
                    )
                )
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun getSerializer(): SearchableSerializer {
        return AndroidCalendarEventSerializer()
    }

    companion object {
        const val Domain = "calendar"
    }
}

