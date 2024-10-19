package de.mm20.launcher2.calendar.providers

import de.mm20.launcher2.search.calendar.CalendarListType

data class CalendarList(
    val id: String,
    val name: String,
    val owner: String?,
    val color: Int,
    val types: List<CalendarListType>,
    val providerId: String,
)