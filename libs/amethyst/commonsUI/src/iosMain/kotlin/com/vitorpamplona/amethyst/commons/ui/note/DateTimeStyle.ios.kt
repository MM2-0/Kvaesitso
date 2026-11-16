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

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

private fun DateTimeStyle.nsStyle(): NSDateFormatterStyle =
    when (this) {
        DateTimeStyle.NONE -> NSDateFormatterNoStyle
        DateTimeStyle.SHORT -> NSDateFormatterShortStyle
        DateTimeStyle.MEDIUM -> NSDateFormatterMediumStyle
        DateTimeStyle.LONG -> NSDateFormatterLongStyle
    }

actual fun formatDateTime(
    epochMillis: Long,
    date: DateTimeStyle,
    time: DateTimeStyle,
): String {
    if (date == DateTimeStyle.NONE && time == DateTimeStyle.NONE) return ""
    val formatter =
        NSDateFormatter().apply {
            dateStyle = date.nsStyle()
            timeStyle = time.nsStyle()
        }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}

actual fun formatDateTimeInZone(
    epochMillis: Long,
    date: DateTimeStyle,
    time: DateTimeStyle,
    zoneId: String,
): String? {
    val zone = NSTimeZone.timeZoneWithName(zoneId) ?: return null
    if (date == DateTimeStyle.NONE && time == DateTimeStyle.NONE) return ""
    val formatter =
        NSDateFormatter().apply {
            dateStyle = date.nsStyle()
            timeStyle = time.nsStyle()
            timeZone = zone
        }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}
