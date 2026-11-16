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

/** The locale's predefined date or time lengths, as `java.text.DateFormat` and `NSDateFormatter` name them. */
enum class DateTimeStyle { NONE, SHORT, MEDIUM, LONG }

/**
 * Formats an instant in the default locale and time zone with the locale's predefined [date] and
 * [time] styles ("Sep 28, 2026, 2:32:10 PM" for MEDIUM/MEDIUM in en-US). A NONE side is left out;
 * both NONE formats nothing. Safe to call from any thread.
 */
expect fun formatDateTime(
    epochMillis: Long,
    date: DateTimeStyle,
    time: DateTimeStyle,
): String

/**
 * Like [formatDateTime], but on the wall clock of the IANA zone [zoneId] ("Europe/Oslo") instead
 * of the reader's, for NIP-52's `start_tzid`. Null when the platform does not know the zone.
 */
expect fun formatDateTimeInZone(
    epochMillis: Long,
    date: DateTimeStyle,
    time: DateTimeStyle,
    zoneId: String,
): String?
