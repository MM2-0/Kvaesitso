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
package com.vitorpamplona.amethyst.commons.model.nip52Calendar

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.utils.TimeUtils

// A strict YYYY-MM-DD read (an impossible day such as 02-31 is refused, not rolled over).
private fun parseIsoDate(date: String?): SearchDate? = if (date.isNullOrBlank()) null else SearchDate.parse(date)

private fun localEpochDay(unixSeconds: Long): Long = LocalClock.epochDayCounter().epochDay(unixSeconds)

/**
 * Calendar 31922 carries a calendar date (no instant). Anchor it at local midnight so that
 * "Jan 15" lands on Jan 15 in the user's grid and ordering reflects their local zone — UTC
 * anchoring made date-only events appear a day early west of UTC.
 */
fun parseIsoDateToUnixSeconds(date: String?): Long? = parseIsoDate(date)?.let { LocalClock.startOfDay(it) }

/**
 * Unified start time as unix-seconds. For 31923 (time-slot) this is the event's instant; for
 * 31922 (date-slot) it is local midnight of the calendar date. Both are suitable for ordering
 * against [TimeUtils.now] and for relative-time rendering.
 */
fun Note.calendarStartSeconds(): Long? =
    when (val e = event) {
        is CalendarTimeSlotEvent -> e.start()
        is CalendarDateSlotEvent -> parseIsoDateToUnixSeconds(e.start())
        else -> null
    }

fun Note.calendarEndSeconds(): Long? =
    when (val e = event) {
        is CalendarTimeSlotEvent -> e.end() ?: e.start()
        is CalendarDateSlotEvent -> parseIsoDateToUnixSeconds(e.end()) ?: parseIsoDateToUnixSeconds(e.start())
        else -> null
    }

/**
 * Calendar-day bucket key (days since 1970-01-01) for grouping events into day cells.
 *
 * For 31923, the event's instant is converted to the viewer's local date; for 31922, the ISO
 * date string is parsed directly with no zone conversion (a calendar date for "Jan 15" must
 * land on Jan 15 in every zone). Returns null when the start cannot be resolved.
 */
fun Note.calendarLocalDayKey(): Long? =
    when (val e = event) {
        is CalendarTimeSlotEvent -> e.start()?.let(::localEpochDay)
        is CalendarDateSlotEvent -> parseIsoDate(e.start())?.daysFromEpoch()
        else -> null
    }

/**
 * Buckets appointments by local calendar day (days since 1970-01-01). Notes that
 * are not calendar appointments or whose start can't be parsed are dropped.
 */
fun groupByDayKey(notes: List<Note>): Map<Long, List<Note>> {
    val map = mutableMapOf<Long, MutableList<Note>>()
    notes.forEach {
        val dayKey = it.calendarLocalDayKey() ?: return@forEach
        map.getOrPut(dayKey) { mutableListOf() }.add(it)
    }
    return map
}

/**
 * Inclusive `[start, end]` range of day-keys an appointment covers. A single-day event yields
 * one key; a multi-day event yields every day from start through end. Returns null when the
 * note isn't a calendar appointment or has no parseable start.
 *
 * Capped at 366 days so a malformed event with a far-future end can't blow up month-view memory.
 */
fun Note.calendarLocalDayKeyRange(): LongRange? {
    val startKey = calendarLocalDayKey() ?: return null
    val endKey =
        when (val e = event) {
            is CalendarTimeSlotEvent -> e.end()?.let(::localEpochDay) ?: startKey
            is CalendarDateSlotEvent -> parseIsoDate(e.end())?.daysFromEpoch() ?: startKey
            else -> startKey
        }
    val safeEnd = endKey.coerceAtLeast(startKey).coerceAtMost(startKey + 366)
    return startKey..safeEnd
}

/**
 * Like [groupByDayKey] but a multi-day appointment lands in every day it covers (not just the
 * start day). Used by month/week/day views so a 3-day conference shows on all three rows; the
 * upcoming/past list view still uses [groupByDayKey] semantics via its own ordering.
 */
fun groupByDayKeyExpanded(notes: List<Note>): Map<Long, List<Note>> {
    val map = mutableMapOf<Long, MutableList<Note>>()
    notes.forEach { note ->
        val range = note.calendarLocalDayKeyRange() ?: return@forEach
        for (key in range) {
            map.getOrPut(key) { mutableListOf() }.add(note)
        }
    }
    return map
}

/**
 * Sort by: upcoming events ascending (closest first), then past events descending (most-recent
 * first). [nowSeconds] is captured once per sort so the comparator stays transitive across the
 * full sort run — reading the clock inside `compare` would violate the [Comparator] contract on
 * boundary elements.
 */
fun upcomingFirstCalendarOrder(nowSeconds: Long): Comparator<Note> =
    Comparator { a, b ->
        val sa = a.calendarStartSeconds()
        val sb = b.calendarStartSeconds()

        val primary =
            when {
                sa == null && sb == null -> compareCreatedAt(a, b)
                sa == null -> 1
                sb == null -> -1
                else -> {
                    val aUpcoming = sa >= nowSeconds
                    val bUpcoming = sb >= nowSeconds
                    when {
                        aUpcoming && !bUpcoming -> -1
                        !aUpcoming && bUpcoming -> 1
                        aUpcoming -> sa.compareTo(sb) // both future: nearest first
                        else -> sb.compareTo(sa) // both past: most recent first
                    }
                }
            }

        if (primary != 0) primary else a.idHex.compareTo(b.idHex)
    }

private fun compareCreatedAt(
    a: Note,
    b: Note,
): Int {
    val ca = a.createdAt() ?: 0L
    val cb = b.createdAt() ?: 0L
    return cb.compareTo(ca)
}
