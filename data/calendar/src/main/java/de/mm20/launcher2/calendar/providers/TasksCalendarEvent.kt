package de.mm20.launcher2.calendar.providers

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.core.net.toUri
import de.mm20.launcher2.calendar.TasksCalendarEventSerializer
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.SearchableSerializer

data class TasksCalendarEvent(
    override val label: String,
    val id: Long,
    override val color: Int?,
    override val startTime: Long?,
    override val endTime: Long,
    override val allDay: Boolean,
    override val isCompleted: Boolean?,
    override val description: String?,
    override val calendarName: String?,
    override val labelOverride: String? = null,
): CalendarEvent {

    override val domain: String = Domain

    override val key: String = "$domain://$id"

    override val location: String? = null
    override val attendees: List<String> = emptyList()


    override fun overrideLabel(label: String): TasksCalendarEvent {
        return this.copy(labelOverride = label)
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        val uri = ContentUris.withAppendedId("content://org.tasks/tasks".toUri(), id)
        val intent = Intent(Intent.ACTION_VIEW).setData(uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage("org.tasks")

        return context.tryStartActivity(intent, options)
    }

    override fun getSerializer(): SearchableSerializer {
        return TasksCalendarEventSerializer()
    }


    companion object {
        const val Domain = "tasks.org"
    }
}