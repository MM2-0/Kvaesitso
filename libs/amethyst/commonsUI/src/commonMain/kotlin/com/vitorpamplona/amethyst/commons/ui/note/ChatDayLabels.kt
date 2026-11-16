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
package com.vitorpamplona.amethyst.commons.ui.note

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

private val chatWeekdayFormatter = DateSkeletonFormatter("EEE")
private val chatMonthDayFormatter = DateSkeletonFormatter("MMMd")
private val chatYearMonthDayFormatter = DateSkeletonFormatter("yMMMd")

/**
 * How many calendar days ago [thenMillis] was, in the default time zone: 0 for today, 1 for
 * yesterday, up to [max]. Null when it was longer ago than that, or lies in the future.
 *
 * Calendar days, not 24-hour spans: a message from 11 PM last night is "yesterday" at 10 AM, though
 * only 11 hours have passed.
 */
fun calendarDaysAgo(
    thenMillis: Long,
    nowMillis: Long,
    max: Int,
): Int? {
    val then = calendarYearAndDay(thenMillis)
    for (days in 0..max) {
        if (calendarYearAndDay(nowMillis - days * DAY_MILLIS) == then) return days
    }
    return null
}

private fun isSameCalendarYear(
    thenMillis: Long,
    nowMillis: Long,
): Boolean = calendarYearAndDay(thenMillis) / 1000 == calendarYearAndDay(nowMillis) / 1000

/**
 * The name of a chat day, for its divider and the pinned day indicator: "Today", "Yesterday",
 * "Aug 20" earlier this year, "Jan 5, 2024" before that. [timeSeconds] is a Unix timestamp.
 */
fun chatDayLabelWith(
    timeSeconds: Long,
    nowMillis: Long,
    today: String,
    yesterday: String,
): String {
    val millis = timeSeconds * 1000
    return when (calendarDaysAgo(millis, nowMillis, max = 1)) {
        0 -> today
        1 -> yesterday
        else -> if (isSameCalendarYear(millis, nowMillis)) chatMonthDayFormatter.format(millis) else chatYearMonthDayFormatter.format(millis)
    }
}

/**
 * A chat message's timestamp when nothing else on screen says which day it is from: the time of day
 * for today, "Yesterday, 5:20 PM", the weekday within the week ("Mon, 5:20 PM"), then the date
 * alone ("Aug 20", "Jan 5, 2024"), where the time of day stops mattering and the date makes plain
 * how old the message is. [timeOfDay] comes from [rememberTimeOfDayFormatter].
 */
fun chatTimestampWith(
    timeSeconds: Long,
    nowMillis: Long,
    timeOfDay: (epochMillis: Long) -> String,
    yesterday: String,
): String {
    val millis = timeSeconds * 1000
    return when (calendarDaysAgo(millis, nowMillis, max = 6)) {
        0 -> timeOfDay(millis)
        1 -> yesterday + ", " + timeOfDay(millis)
        null -> if (isSameCalendarYear(millis, nowMillis)) chatMonthDayFormatter.format(millis) else chatYearMonthDayFormatter.format(millis)
        // 2 to 6 days ago
        else -> chatWeekdayFormatter.format(millis) + ", " + timeOfDay(millis)
    }
}
