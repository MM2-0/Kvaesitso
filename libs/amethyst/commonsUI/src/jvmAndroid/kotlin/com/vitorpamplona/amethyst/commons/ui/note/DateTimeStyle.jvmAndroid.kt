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

import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

private fun DateTimeStyle.javaStyle(): Int =
    when (this) {
        DateTimeStyle.SHORT -> DateFormat.SHORT
        DateTimeStyle.MEDIUM -> DateFormat.MEDIUM
        DateTimeStyle.LONG, DateTimeStyle.NONE -> DateFormat.LONG
    }

/** A fresh `DateFormat` per call: they are not thread-safe, and the locale may have changed. */
private fun dateFormatFor(
    date: DateTimeStyle,
    time: DateTimeStyle,
): DateFormat? =
    when {
        date == DateTimeStyle.NONE && time == DateTimeStyle.NONE -> null
        date == DateTimeStyle.NONE -> DateFormat.getTimeInstance(time.javaStyle())
        time == DateTimeStyle.NONE -> DateFormat.getDateInstance(date.javaStyle())
        else -> DateFormat.getDateTimeInstance(date.javaStyle(), time.javaStyle())
    }

actual fun formatDateTime(
    epochMillis: Long,
    date: DateTimeStyle,
    time: DateTimeStyle,
): String {
    val formatter = dateFormatFor(date, time) ?: return ""
    return formatter.format(Date(epochMillis))
}

actual fun formatDateTimeInZone(
    epochMillis: Long,
    date: DateTimeStyle,
    time: DateTimeStyle,
    zoneId: String,
): String? {
    // getTimeZone falls back to GMT for an id it does not know; that is a wrong time, not a zone.
    val zone = TimeZone.getTimeZone(zoneId).takeIf { it.id == zoneId } ?: return null
    val formatter = dateFormatFor(date, time) ?: return ""
    formatter.timeZone = zone
    return formatter.format(Date(epochMillis))
}
