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
     * The main content type of this list. This has mainly cosmetic purposes (labels, icons).
     * It doesn't need to be 100% accurate since the actual type is determined on a per-item basis.
     * However, the launcher will hide some settings if it doesn't find any lists that would be
     * affected by them.
     */
    val contentTypes: List<CalendarListType>,
    /**
     * The owner account of this list.
     */
    val accountName: String? = null,
    /**
     * The color of this list, in 0xFFAARRGGBB format.
     * If null, the launcher will use a default theme color.
     * The color is corrected to match the launcher's theme  (i.e. for dark mode).
     */
    val color: Int? = null,
)