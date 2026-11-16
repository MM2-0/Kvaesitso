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

import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import kotlin.math.abs

/*
 * What an edition knows beyond the day's posts: the reader's clock, its number, the last few
 * front pages, and the masthead, motto and sections the last edition left. All of it goes in the
 * writer's message above the corpus, outside the data block, because it is ours.
 */

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** A civil date: [month] 1..12, [day] 1..31, [weekday] 1 = Monday … 7 = Sunday. */
internal class CivilDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val weekday: Int,
) {
    /** "Friday, November 13, 2026". */
    fun long() = "${WEEKDAYS[weekday - 1]}, ${MONTHS[month - 1]} $day, $year"

    /** "2026-11-13". */
    fun key() = "$year-${two(month)}-${two(day)}"

    companion object {
        /** The date [days] after 1970-01-01 (Hinnant's civil-from-days, any era). */
        fun ofEpochDay(days: Long): CivilDate {
            val z = days + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
            // 1970-01-01 was a Thursday.
            val weekday = ((days + 3).mod(7L) + 1).toInt()
            return CivilDate(year, month, day, weekday)
        }

        /** A `yyyy-MM-dd` day key, or null. */
        fun ofKey(key: String): CivilDate? {
            val parts = key.split('-')
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            val d = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
            return ofEpochDay(epochDay(y, m, d))
        }

        /** Days from 1970-01-01 to [year]-[month]-[day] (Hinnant's days-from-civil). */
        fun epochDay(
            year: Int,
            month: Int,
            day: Int,
        ): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097 + doe - 719_468
        }
    }
}

private fun two(n: Int) = n.toString().padStart(2, '0')

/**
 * The reader's clock for one edition: the schedule's IANA zone, else this device's, else UTC.
 * The date, the window's close and every time in the digest are told on it, with the zone named,
 * so the writer never converts between zones.
 */
class EditionClock(
    /** The IANA name ("America/New_York"), or "UTC". */
    val zoneId: String,
    private val zone: CronZone,
) {
    private fun wall(epochSec: Long) = zone.wallSeconds(epochSec)

    internal fun civil(epochSec: Long) = CivilDate.ofEpochDay(wall(epochSec).floorDiv(86_400L))

    /** "Friday, November 13, 2026". */
    fun date(epochSec: Long): String = civil(epochSec).long()

    /** "07:47", on the 24-hour clock. */
    fun time(epochSec: Long): String {
        val seconds = wall(epochSec).mod(86_400L)
        return two((seconds / 3_600).toInt()) + ":" + two(((seconds % 3_600) / 60).toInt())
    }

    /** "2026-11-13 07:47": a post's time in the digest. */
    fun stamp(epochSec: Long): String = civil(epochSec).key() + " " + time(epochSec)

    /** "UTC-05:00" at [epochSec]: the zone's offset then. */
    fun offset(epochSec: Long): String {
        val total = zone.offsetSecondsAt(epochSec)
        val sign = if (total < 0) "-" else "+"
        val minutes = abs(total) / 60
        return "UTC$sign${two(minutes / 60)}:${two(minutes % 60)}"
    }

    /** "America/New_York, UTC-05:00", or "UTC". */
    fun zoneLabel(epochSec: Long): String = if (zoneId == "UTC" && zone.offsetSecondsAt(epochSec) == 0) "UTC" else "$zoneId, ${offset(epochSec)}"

    companion object {
        val UTC = EditionClock("UTC", CronZone.UTC)

        /** The zone named [zoneId], when this platform knows it. */
        fun of(zoneId: String?): EditionClock? {
            val id = zoneId?.trim()?.ifEmpty { null } ?: return null
            return FrontPageClock.cronZone(id)?.let { EditionClock(id, it) }
        }

        /** The schedule's zone ([scheduleZone]), else this device's, else UTC. */
        fun forReader(scheduleZone: String?): EditionClock = of(scheduleZone) ?: of(FrontPageClock.localZone()) ?: UTC
    }
}

/** "Friday, November 13, 2026" for a kept edition's `yyyy-MM-dd` [day]; the key itself when it isn't one. */
fun longDay(day: String): String = CivilDate.ofKey(day)?.long() ?: day

/** One front page the writer is reminded of: its day and its lead headline. */
class RecentHeadline(
    val day: String,
    val headline: String,
)

/** The paper as the last editions left it: its name, motto and standing sections. */
class StandingPaper(
    val masthead: String = MASTHEAD,
    val motto: String? = null,
    val sections: List<String> = emptyList(),
)

/** Everything about the edition beyond its posts, for the writer's message. */
class EditionContext(
    val paper: StandingPaper = StandingPaper(),
    /** `Vol. I · No. N`'s N; null when it can't be counted (then none is printed). */
    val number: Int? = null,
    val recent: List<RecentHeadline> = emptyList(),
    val clock: EditionClock = EditionClock.UTC,
    /** The Kitchen's recipes recent editions ran ([recipeAddress]): not offered again ([pickRecipe]). */
    val recentRecipes: Set<String> = emptySet(),
)

/** The edition number of a print on [today]: one more than the distinct days printed before (or after) it. */
fun editionNumber(
    days: Collection<String>,
    today: String,
): Int = days.filter { it.isNotBlank() && it != today }.toSet().size + 1

/** How many recent editions' recipes the Kitchen won't offer again. */
const val RECENT_RECIPES = 14

/** The recipes the last [max] editions before [today] ran, as their addresses. */
fun recentRecipes(
    editions: List<SavedEdition>,
    today: String,
    max: Int = RECENT_RECIPES,
): Set<String> =
    editions
        .filter { it.day != today }
        .sortedByDescending { it.day }
        .take(max)
        .mapNotNull { it.recipe }
        .toSet()

/** How many recent front pages the writer is shown. */
const val RECENT_HEADLINES = 5

/** The last [max] front pages before [today] that have a headline, newest first, one per day. */
fun recentHeadlines(
    editions: List<SavedEdition>,
    today: String,
    max: Int = RECENT_HEADLINES,
): List<RecentHeadline> =
    editions
        .filter { it.day != today && !it.headline.isNullOrBlank() }
        .sortedByDescending { it.day }
        .distinctBy { it.day }
        .take(max)
        .map { RecentHeadline(it.day, clean(it.headline!!, 160)) }

private val ANNOUNCEMENT = Regex("<!--\\s*(masthead|motto|sections)\\s*:([\\s\\S]*?)-->", RegexOption.IGNORE_CASE)
private val SECTION_SPLIT = Regex("\\s*[·•|,;]\\s*")
private val TAGS = Regex("<[^>]*>")
private val ANGLES = Regex("[<>]")
private val SPACES = Regex("\\s+")

/** One line of the writer's own words read back: no markup, no line breaks, at most [max] characters. */
private fun clean(
    text: String,
    max: Int,
): String =
    text
        .replace(TAGS, "")
        .replace(ANGLES, "")
        .replace(SPACES, " ")
        .trim()
        .take(max)
        .trim()

/** The masthead, motto and sections one page's comments announce; nulls where it says nothing. */
class Announced(
    val masthead: String?,
    val motto: String?,
    val sections: List<String>?,
)

/**
 * The `<!-- masthead: … | why -->`, `<!-- motto: … | why -->` and `<!-- sections: A · B -->`
 * comments of [html]: the first of each. These are the writer's own words read back into the
 * next brief, so each is one short line without markup ([clean]).
 */
fun announcedIn(html: String): Announced {
    var masthead: String? = null
    var motto: String? = null
    var sections: List<String>? = null
    for (match in ANNOUNCEMENT.findAll(html)) {
        val value = match.groupValues[2].substringBefore('|')
        when (match.groupValues[1].lowercase()) {
            "masthead" -> if (masthead == null) masthead = clean(value, 60).ifEmpty { null }
            "motto" -> if (motto == null) motto = clean(value, 120).ifEmpty { null }
            "sections" ->
                if (sections == null) {
                    sections =
                        value
                            .split(SECTION_SPLIT)
                            .map { clean(it, 40) }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .take(12)
                            .ifEmpty { null }
                }
        }
    }
    return Announced(masthead, motto, sections)
}

/**
 * The paper as [pages] (newest first) leave it: each of the masthead, the motto and the sections
 * from the newest page that announces it; the house name, and none, otherwise.
 */
fun standingPaper(pages: List<String>): StandingPaper {
    val read = pages.map(::announcedIn)
    return StandingPaper(
        masthead = read.firstNotNullOfOrNull { it.masthead } ?: MASTHEAD,
        motto = read.firstNotNullOfOrNull { it.motto },
        sections = read.firstNotNullOfOrNull { it.sections }.orEmpty(),
    )
}
