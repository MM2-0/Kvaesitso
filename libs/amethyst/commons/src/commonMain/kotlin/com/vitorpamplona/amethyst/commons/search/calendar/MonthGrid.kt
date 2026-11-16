/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.search.calendar

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One cell of a month grid: the day it stands for, and the two states a cell draws. */
@Immutable
data class MonthGridDay(
    val date: SearchDate,
    val isToday: Boolean,
    val isAhead: Boolean,
) {
    /** The `YYYY-MM-DD` a pick on this cell writes into the field. */
    val value: String get() = date.ymd()
}

/**
 * A month as the cells a seven-column grid draws. [lead] is how many blanks come before the 1st,
 * given the weekday this locale starts its week on.
 */
@Immutable
data class MonthGrid(
    val month: SearchDate,
    val label: String,
    val lead: Int,
    val days: ImmutableList<MonthGridDay>,
)

/** A shortcut under the grid. Its [value] is an absolute day, never an offset — see [quickPicks]. */
@Immutable
data class QuickPick(
    val label: String,
    val value: String,
)

object SearchCalendar {
    /**
     * One month's cells. [today] is passed in rather than read, so a test can pick its day.
     */
    fun monthGrid(
        month: SearchDate,
        today: SearchDate,
        weekStart: Int = LocalClock.firstDayOfWeek(),
        label: String = LocalClock.monthLabel(month),
    ): MonthGrid {
        val first = month.firstOfMonth()
        val count = SearchDate.lastDayOfMonth(first.year, first.month)
        val days =
            (1..count).map { d ->
                val at = SearchDate(first.year, first.month, d)
                MonthGridDay(at, isToday = at == today, isAhead = at > today)
            }
        return MonthGrid(
            month = first,
            label = label,
            lead = (first.dayOfWeek() - weekStart + 7) % 7,
            days = days.toImmutableList(),
        )
    }

    /** The seven column headings in this locale's week order. */
    fun weekdayHeadings(
        weekStart: Int = LocalClock.firstDayOfWeek(),
        names: List<String> = LocalClock.narrowWeekdayNames(),
    ): ImmutableList<String> = (0..6).map { names[(weekStart + it) % 7] }.toImmutableList()

    /**
     * The month a half-typed date names, or null while it names none. A partial year or a partial
     * month is not read: `2026-0` names no month, and `202` is not a year.
     */
    fun typedMonth(partial: String?): SearchDate? {
        val s = partial ?: return null
        if (s.length < 7 || s[4] != '-') return null
        val year = s.substring(0, 4).toIntOrNull() ?: return null
        val month = s.substring(5, 7).toIntOrNull() ?: return null
        if (month < 1 || month > 12) return null
        return SearchDate(year, month, 1)
    }

    /**
     * The shortcuts under the grid, written into the field as absolute days: a saved search
     * reading `since:7d` would mean a different search every morning.
     */
    fun quickPicks(
        field: DateField,
        today: SearchDate,
    ): ImmutableList<QuickPick> =
        when (field) {
            DateField.SINCE ->
                listOf(
                    "Today" to 0,
                    "Last 7 days" to -6,
                    "Last 30 days" to -29,
                    "Last 90 days" to -89,
                )

            DateField.UNTIL ->
                listOf(
                    "Today" to 0,
                    "Yesterday" to -1,
                    "A week ago" to -7,
                    "A month ago" to -30,
                )
        }.map { (label, offset) -> QuickPick(label, today.plusDays(offset).ymd()) }
            .toImmutableList()
}

/** Which end of the window a `since:`/`until:` token names. */
enum class DateField(
    val token: String,
) {
    SINCE("since"),
    UNTIL("until"),
    ;

    /** The heading the calendar shows while this end is being picked. */
    val heading: String
        get() = if (this == SINCE) "Written on or after" else "Written on or before"

    companion object {
        fun of(name: String): DateField? =
            when (name.lowercase()) {
                "since" -> SINCE
                "until" -> UNTIL
                else -> null
            }
    }
}
