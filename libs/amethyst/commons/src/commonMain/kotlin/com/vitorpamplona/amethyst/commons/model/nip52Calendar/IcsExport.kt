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

import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent

/**
 * Serialises NIP-52 calendar events to RFC 5545 iCalendar (`.ics`) text. The output is the
 * universal calendar interchange format: tapping a generated file in any email/calendar app
 * lets users import the event into Google Calendar, Apple Calendar, Outlook, Thunderbird, etc.
 *
 * Implements only the subset needed for NIP-52 appointments:
 *  - `VEVENT` per appointment with `UID`, `DTSTAMP`, `DTSTART`, `DTEND`, `SUMMARY`,
 *    `DESCRIPTION`, `LOCATION`, and `CATEGORIES` for hashtags.
 *  - 31922 date-slot events emit `DTSTART;VALUE=DATE:YYYYMMDD` (no time component); 31923
 *    time-slot events emit UTC instants formatted as `YYYYMMDDTHHMMSSZ`.
 *  - 31924 calendar collections wrap their member appointments in one `VCALENDAR`.
 *
 * Line folding (75-octet limit per RFC 5545) is *not* implemented — modern parsers tolerate
 * long lines and the resulting files import cleanly across the apps tested.
 */
object IcsExport {
    private const val PRODID = "-//Amethyst//NIP-52//EN"
    private const val CRLF = "\r\n"

    fun appointmentToIcs(
        event: Any,
        address: Address,
        nowSeconds: Long,
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR").append(CRLF)
        sb.append("VERSION:2.0").append(CRLF)
        sb.append("PRODID:").append(PRODID).append(CRLF)
        sb.append("CALSCALE:GREGORIAN").append(CRLF)
        appendVEvent(sb, event, address, nowSeconds)
        sb.append("END:VCALENDAR").append(CRLF)
        return sb.toString()
    }

    /**
     * Wrap multiple appointments (the membership of a kind-31924 calendar) in one VCALENDAR.
     * Members that aren't in [memberEvents] are silently skipped — typically because they
     * haven't been fetched from relays yet.
     */
    fun calendarToIcs(
        calendar: CalendarCollectionEvent,
        memberEvents: List<Pair<Address, Any>>,
        nowSeconds: Long,
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR").append(CRLF)
        sb.append("VERSION:2.0").append(CRLF)
        sb.append("PRODID:").append(PRODID).append(CRLF)
        sb.append("CALSCALE:GREGORIAN").append(CRLF)
        calendar.title()?.let {
            sb.append("X-WR-CALNAME:").append(escapeText(it)).append(CRLF)
        }
        calendar.content.takeIf { it.isNotBlank() }?.let {
            sb.append("X-WR-CALDESC:").append(escapeText(it)).append(CRLF)
        }
        memberEvents.forEach { (address, event) ->
            appendVEvent(sb, event, address, nowSeconds)
        }
        sb.append("END:VCALENDAR").append(CRLF)
        return sb.toString()
    }

    /**
     * Suggested filename for a single appointment. Sanitises the title for filesystems but
     * keeps it short enough for share-sheet thumbnails. Falls back to the d-tag.
     */
    fun appointmentFilename(
        event: Any,
        address: Address,
    ): String {
        val title =
            when (event) {
                is CalendarTimeSlotEvent -> event.title()
                is CalendarDateSlotEvent -> event.title()
                else -> null
            }
        return safeFilename(title ?: address.dTag) + ".ics"
    }

    fun calendarFilename(calendar: CalendarCollectionEvent): String = safeFilename(calendar.title() ?: "calendar") + ".ics"

    private fun safeFilename(raw: String): String =
        raw
            .replace(Regex("[^a-zA-Z0-9._-]+"), "-")
            .trim('-', '_')
            .ifBlank { "calendar" }
            .take(60)

    private fun appendVEvent(
        sb: StringBuilder,
        event: Any,
        address: Address,
        nowSeconds: Long,
    ) {
        sb.append("BEGIN:VEVENT").append(CRLF)
        // UID must be globally unique; "<dtag>@<pubkey>.nostr" gives stable uniqueness without
        // exposing relay metadata.
        sb
            .append("UID:")
            .append(address.dTag)
            .append('@')
            .append(address.pubKeyHex)
            .append(".nostr")
            .append(CRLF)
        sb.append("DTSTAMP:").append(formatUtcInstant(nowSeconds)).append(CRLF)

        when (event) {
            is CalendarTimeSlotEvent -> appendTimeSlot(sb, event)
            is CalendarDateSlotEvent -> appendDateSlot(sb, event)
        }

        sb.append("END:VEVENT").append(CRLF)
    }

    private fun appendTimeSlot(
        sb: StringBuilder,
        event: CalendarTimeSlotEvent,
    ) {
        event.start()?.let { sb.append("DTSTART:").append(formatUtcInstant(it)).append(CRLF) }
        event.end()?.let { sb.append("DTEND:").append(formatUtcInstant(it)).append(CRLF) }
        event.title()?.let { sb.append("SUMMARY:").append(escapeText(it)).append(CRLF) }
        appendDescription(sb, event.summary().orEmpty().ifBlank { event.content })
        event.location()?.let { sb.append("LOCATION:").append(escapeText(it)).append(CRLF) }
        appendCategories(sb, event.hashtags())
    }

    private fun appendDateSlot(
        sb: StringBuilder,
        event: CalendarDateSlotEvent,
    ) {
        event.start()?.let { iso ->
            tryFormatBasicDate(iso)?.let { sb.append("DTSTART;VALUE=DATE:").append(it).append(CRLF) }
        }
        event.end()?.let { iso ->
            tryFormatBasicDate(iso)?.let { sb.append("DTEND;VALUE=DATE:").append(it).append(CRLF) }
        }
        event.title()?.let { sb.append("SUMMARY:").append(escapeText(it)).append(CRLF) }
        appendDescription(sb, event.summary().orEmpty().ifBlank { event.content })
        event.location()?.let { sb.append("LOCATION:").append(escapeText(it)).append(CRLF) }
        appendCategories(sb, event.hashtags())
    }

    private fun appendDescription(
        sb: StringBuilder,
        text: String,
    ) {
        if (text.isBlank()) return
        sb.append("DESCRIPTION:").append(escapeText(text)).append(CRLF)
    }

    private fun appendCategories(
        sb: StringBuilder,
        hashtags: List<String>,
    ) {
        if (hashtags.isEmpty()) return
        sb.append("CATEGORIES:").append(hashtags.joinToString(",") { escapeText(it) }).append(CRLF)
    }

    /** `yyyyMMdd'T'HHmmss'Z'` in UTC. */
    private fun formatUtcInstant(unixSeconds: Long): String {
        val date = SearchDate.civilFromDays(unixSeconds.floorDiv(SECONDS_PER_DAY))
        val secondOfDay = unixSeconds.mod(SECONDS_PER_DAY)
        return basicDate(date) + "T" + pad2(secondOfDay / 3600) + pad2(secondOfDay / 60 % 60) + pad2(secondOfDay % 60) + "Z"
    }

    /** `yyyy-MM-dd` as `yyyyMMdd`, or null when [iso] is not a valid date. */
    private fun tryFormatBasicDate(iso: String): String? = SearchDate.parse(iso)?.let(::basicDate)

    private fun basicDate(date: SearchDate): String = date.year.toString().padStart(4, '0') + pad2(date.month.toLong()) + pad2(date.day.toLong())

    private fun pad2(value: Long): String = value.toString().padStart(2, '0')

    private const val SECONDS_PER_DAY = 86_400L

    /**
     * Escapes text per RFC 5545 §3.3.11: backslash, semicolon, comma, newline. Carriage
     * returns are dropped (line folding handles physical newlines for us).
     */
    internal fun escapeText(raw: String): String =
        raw
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace(",", "\\,")
            .replace(";", "\\;")
}
