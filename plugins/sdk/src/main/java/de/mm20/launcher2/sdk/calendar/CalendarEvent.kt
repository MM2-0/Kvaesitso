package de.mm20.launcher2.sdk.calendar

import android.net.Uri

data class CalendarEvent(
    val id: String,
    val title: String,
    /**
     * The name of the calendar the event belongs to.
     */
    val calendarName: String?,
    val description: String? = null,
    /**
     * The location of the event.
     */
    val location: String? = null,
    /**
     * The color of the event, as 0xAARRGGBB int.
     */
    val color: Int? = null,
    /**
     * Start time of the event in milliseconds since epoch.
     * For tasks, this can be null.
     */
    val startTime: Long?,
    /**
     * End time of the event in milliseconds since epoch.
     * For tasks: Due date of the task.
     */
    val endTime: Long,
    /**
     * If false, only the date will be shown for the event.
     */
    val includeTime: Boolean = true,
    val attendees: List<String> = emptyList(),
    val uri: Uri,
    /**
     * If this is not null, the event is treated as a task, indicated by a checkmark in the UI.
     */
    val isCompleted: Boolean? = null,
)