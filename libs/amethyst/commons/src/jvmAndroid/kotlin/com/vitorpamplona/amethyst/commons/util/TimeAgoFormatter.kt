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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.quartz.utils.TimeUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Month + day without year — month name is textual so order is unambiguous across locales.
private const val MONTH_DATE_FORMAT = "MMM d"

/**
 * Per-thread cached [DateFormat] keyed off the current default [Locale].
 *
 * `DateFormat`/`SimpleDateFormat` are mutable and not thread-safe. These
 * formatters can be read from UI threads (composition) and from background
 * coroutines. `ThreadLocal` gives each thread its own instance — no locks,
 * no allocation per call, and we rebuild lazily on locale change.
 */
private class LocaleAwareFormatter(
    private val build: (Locale) -> DateFormat,
) {
    private val cache = ThreadLocal<Pair<Locale, DateFormat>>()

    fun get(): DateFormat {
        val current = Locale.getDefault()
        val cached = cache.get()
        if (cached != null && cached.first == current) return cached.second
        val fresh = build(current)
        cache.set(current to fresh)
        return fresh
    }
}

// Locale-aware: en-US "May 28, 2026" · en-GB "28 May 2026" · de-DE "28.05.2026" · ja-JP "2026/05/28"
private val yearFormatter = LocaleAwareFormatter { DateFormat.getDateInstance(DateFormat.MEDIUM, it) }
private val monthFormatter = LocaleAwareFormatter { SimpleDateFormat(MONTH_DATE_FORMAT, it) }

// Locale-aware: en-US "2:32 PM" · en-GB "14:32" · de-DE "14:32"
private val timeOnlyFormatter = LocaleAwareFormatter { DateFormat.getTimeInstance(DateFormat.SHORT, it) }

/**
 * Formats a Unix timestamp (seconds) as a human-readable time ago string.
 * Returns strings like " • 5m", " • 2h", " • Dec 12"
 *
 * @param time Unix timestamp in seconds, or null
 * @param withDot Whether to prefix with " • " (default true)
 * @return Formatted time ago string
 */
fun timeAgo(
    time: Long?,
    withDot: Boolean = true,
): String {
    if (time == null) return " "
    if (time == 0L) return if (withDot) " • never" else "never"

    val timeDifference = TimeUtils.now() - time
    val prefix = if (withDot) " • " else ""

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            prefix + yearFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_MONTH -> {
            prefix + monthFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            prefix + (timeDifference / TimeUtils.ONE_DAY).toString() + "d"
        }

        timeDifference > TimeUtils.ONE_HOUR -> {
            prefix + (timeDifference / TimeUtils.ONE_HOUR).toString() + "h"
        }

        timeDifference > TimeUtils.ONE_MINUTE -> {
            prefix + (timeDifference / TimeUtils.ONE_MINUTE).toString() + "m"
        }

        else -> {
            prefix + "now"
        }
    }
}

/**
 * Formats a Unix timestamp as a date string.
 * For recent dates (< 1 day), returns the provided today string.
 *
 * @param time Unix timestamp in seconds
 * @param never String to show for time == 0
 * @param today String to show for today's date
 */
fun dateFormatter(
    time: Long?,
    never: String = "never",
    today: String = "today",
): String {
    if (time == null) return " "
    if (time == 0L) return " $never"

    val timeDifference = TimeUtils.now() - time

    return when {
        timeDifference > TimeUtils.ONE_YEAR -> {
            yearFormatter.get().format(time * 1000)
        }

        timeDifference > TimeUtils.ONE_DAY -> {
            monthFormatter.get().format(time * 1000)
        }

        else -> {
            today
        }
    }
}

/**
 * Extension function for Long timestamps.
 */
fun Long.toTimeAgo(withDot: Boolean = true): String = timeAgo(this, withDot)

/**
 * Formats a Unix timestamp (seconds) as an absolute date/time string. Granularity
 * depends on how far in the past the timestamp is:
 *   - same day → locale-aware short time (e.g. "14:32" / "2:32 PM")
 *   - same year → month + day + short time (e.g. "May 28, 2:32 PM")
 *   - older    → locale-aware medium date (e.g. "May 28, 2026" / "28 May 2026" / "28.05.2026")
 */
fun timeAbsolute(
    time: Long?,
    withDot: Boolean = true,
    never: String = "never",
): String {
    if (time == null) return " "
    val prefix = if (withDot) " • " else ""
    if (time == 0L) return prefix + never

    val timeMs = time * 1000
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timeMs }

    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

    val timeOfDay = timeOnlyFormatter.get().format(Date(timeMs))

    return when {
        sameDay -> prefix + timeOfDay
        sameYear -> prefix + monthFormatter.get().format(timeMs) + ", " + timeOfDay
        else -> prefix + yearFormatter.get().format(timeMs)
    }
}

fun Long.toTimeAbsolute(withDot: Boolean = true): String = timeAbsolute(this, withDot)
