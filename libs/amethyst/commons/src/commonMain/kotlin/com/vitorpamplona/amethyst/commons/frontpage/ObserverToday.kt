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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone

/**
 * The calendar The Observer reads "today" on: the same one the "already printed today" check
 * reads ([ObserverSite.printedFor], [FrontPagePrinter.alreadyPrinted]), so the screen never shows
 * a paper the morning print would print again, or offers to print one it would skip.
 */
fun interface ObserverCalendar {
    /** The calendar day [epochSec] falls on, `yyyy-MM-dd`. */
    fun dayOf(epochSec: Long): String

    companion object {
        /** [zone]'s calendar: the day its wall clock shows. */
        fun of(zone: CronZone) = ObserverCalendar { ObserverSite.utcDay(zone.wallSeconds(it)) }

        /** This device's own calendar, as [FrontPageClock.dayKey] reads it (and kept editions are named). */
        val DEVICE = ObserverCalendar { FrontPageClock.dayKey(it) }

        /**
         * The schedule's zone when a readable schedule names one this platform knows (the zone the
         * morning print checks in), else this device's.
         */
        fun forSchedule(
            schedule: MorningScheduleView,
            device: ObserverCalendar = DEVICE,
            zoneOf: (String?) -> CronZone? = FrontPageClock::cronZone,
        ): ObserverCalendar = forZone(readableZone(schedule), device, zoneOf)

        /** The calendar of [zone] (a readable schedule's), else [device]'s: a zone this platform doesn't know is the device's too. */
        fun forZone(
            zone: String?,
            device: ObserverCalendar = DEVICE,
            zoneOf: (String?) -> CronZone? = FrontPageClock::cronZone,
        ): ObserverCalendar = zone?.let(zoneOf)?.let(::of) ?: device

        /** The zone [schedule] is read in, when it is readable; null reads the device's calendar. */
        fun readableZone(schedule: MorningScheduleView): String? = schedule.zone?.takeIf { schedule.state == ScheduleState.READY }
    }
}

/**
 * The day [edition] belongs to on this calendar: the day it was printed, as the "already printed"
 * check reads it; an edition with no print time (one read from an Observer site that didn't say)
 * keeps the day it is named for.
 */
fun ObserverCalendar.dayOf(edition: SavedEdition): String = if (edition.printedAt > 0) dayOf(edition.printedAt) else edition.day

/**
 * What The Observer shows: today's paper when one was printed today, else the view that prints it.
 * An older paper is never shown as the current one: it is in [past], for the Past editions list.
 */
@Immutable
data class ObserverToday(
    /** Today, `yyyy-MM-dd`, on the [ObserverCalendar] it was decided on. */
    val day: String,
    /** Today's paper, the newest printed today; null shows the print view. */
    val paper: SavedEdition?,
    /** Every other kept edition, newest first, with the day it belongs to on the same calendar. */
    val past: List<PastEdition>,
) {
    /** Yesterday, on the same calendar: what the Past editions list calls "Yesterday". */
    val yesterday: String? get() = dayBefore(day)

    /** Any edition at all: the setup checklist says "To print your Observer" rather than "your first". */
    val hasEditions: Boolean get() = paper != null || past.isNotEmpty()

    /**
     * The past edition [pick] names (Past editions, a notification, `Route.FrontPage(day)`); null
     * shows today. Picked by identity, not by comparing days: an edition's day is named on the
     * device's calendar and today on the schedule's, so a device in Los Angeles reading a Tokyo
     * schedule lists, as past, an edition named with the device's own today. A pick with its
     * [EditionPick.code] is that edition when it is a past one; one by day alone is today's paper
     * when that is the day's, else the past edition kept for it.
     */
    fun pastEdition(pick: EditionPick?): PastEdition? {
        if (pick == null) return null
        if (pick.code != null) return past.firstOrNull { it.edition.day == pick.day && it.edition.code == pick.code }
        if (paper?.day == pick.day) return null
        return past.firstOrNull { it.edition.day == pick.day }
    }
}

/** An edition picked to read in place of today's: its [day] (`yyyy-MM-dd`, as kept) and, from the list, its [code]. */
@Immutable
data class EditionPick(
    val day: String,
    val code: String? = null,
) {
    companion object {
        fun of(edition: SavedEdition) = EditionPick(edition.day, edition.code)
    }
}

/** The decision for [editions] (newest first) at [now] on [calendar]. Pure: the screen recomputes it each minute. */
fun observerToday(
    editions: List<SavedEdition>,
    now: Long,
    calendar: ObserverCalendar,
): ObserverToday {
    val today = calendar.dayOf(now)
    val dated = editions.map { PastEdition(it, calendar.dayOf(it)) }
    val paper = dated.firstOrNull { it.day == today }
    return ObserverToday(today, paper?.edition, if (paper == null) dated else dated.filter { it !== paper })
}

/** A kept edition that isn't today's, and the [day] it belongs to on the calendar today was decided on. */
@Immutable
data class PastEdition(
    val edition: SavedEdition,
    val day: String,
)

/**
 * The Observer's clock at one minute: [now] read on [calendar]. The screen, the reader and the
 * titles that say "Today" all read the same one ([FrontPageMorning.observerNow]), so they turn the
 * page together at midnight.
 */
@Immutable
class ObserverNow(
    /** The readable schedule's zone it is read in; null for this device's. */
    val zone: String?,
    val now: Long,
    val calendar: ObserverCalendar = ObserverCalendar.forZone(zone),
) {
    /** Today, `yyyy-MM-dd`. */
    val today: String = calendar.dayOf(now)

    /** Equal while it is the same day on the same calendar: a new minute alone changes nothing shown. */
    override fun equals(other: Any?) = other is ObserverNow && other.today == today && other.zone == zone

    override fun hashCode() = today.hashCode() * 31 + zone.hashCode()
}

/** How a day is named relative to today: "Today", "Yesterday", a weekday within the past week, else its date. */
sealed interface RelativeDay {
    data object Today : RelativeDay

    data object Yesterday : RelativeDay

    /** Two to six days ago: its ISO weekday, 1 = Monday … 7 = Sunday. */
    data class Weekday(
        val isoDay: Int,
    ) : RelativeDay

    /** Further back, ahead of today, or not a day at all: the date itself. */
    data object Date : RelativeDay
}

/** [day] named relative to [today] (both `yyyy-MM-dd`, on the same calendar). */
fun relativeDay(
    day: String,
    today: String,
): RelativeDay {
    if (day == today) return RelativeDay.Today
    val ago = (daysOf(today) ?: return RelativeDay.Date) - (daysOf(day) ?: return RelativeDay.Date)
    return when (ago) {
        1L -> RelativeDay.Yesterday
        in 2L..6L -> dayOfWeek(day)?.let { RelativeDay.Weekday(it) } ?: RelativeDay.Date
        else -> RelativeDay.Date
    }
}

/** The day before [day] (`yyyy-MM-dd`), for "Yesterday"; null for anything else. */
fun dayBefore(day: String): String? = parseDay(day)?.plusDays(-1)?.ymd()

private fun daysOf(day: String): Long? = parseDay(day)?.daysFromEpoch()

private fun parseDay(day: String): SearchDate? {
    val parts = day.split('-')
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    return SearchDate(y, m, d)
}
