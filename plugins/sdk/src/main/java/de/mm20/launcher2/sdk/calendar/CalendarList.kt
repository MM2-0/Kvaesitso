package de.mm20.launcher2.sdk.calendar

import de.mm20.launcher2.search.calendar.CalendarListType

data class CalendarList(
    /**
     * A unique identifier for this list.
     */
    val id: String,
    /**
     * The display name of this list.
     */
    val name: String,
    /**
     * The content type of this list.
     */
    val contentTypes: List<CalendarListType>,
    /**
     * The owner account of this list.
     */
    val accountName: String? = null,
    /**
     * The color of this list, in 0xAARRGGBB format.
     * If null, the launcher will use a default theme color.
     * The color is corrected to match the launcher's theme  (i.e. for dark mode).
     */
    val color: Int? = null,
)