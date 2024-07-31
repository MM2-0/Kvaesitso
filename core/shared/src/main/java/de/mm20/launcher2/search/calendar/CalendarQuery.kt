package de.mm20.launcher2.search.calendar

data class CalendarQuery(
    val query: String?,
    val start: Long?,
    val end: Long?,
    /**
     * List of calendar list ids that should be excluded from the search results.
     */
    val excludedCalendars: List<String>,
)