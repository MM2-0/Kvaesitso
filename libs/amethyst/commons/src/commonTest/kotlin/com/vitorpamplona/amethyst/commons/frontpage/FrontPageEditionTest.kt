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

import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.corpus
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.event
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontPageEditionTest {
    private fun edition(
        day: String,
        headline: String? = "Headline of $day",
    ) = SavedEdition(day = day, code = "ABCDEF", printedAt = 0, model = "m", headline = headline)

    @Test
    fun theEditionNumberCountsDistinctDaysBeforeToday() {
        val days = listOf("2026-11-10", "2026-11-11", "2026-11-11", "2026-11-12", "2026-11-13")
        assertEquals(4, editionNumber(days, today = "2026-11-13"))
        assertEquals(1, editionNumber(emptyList(), today = "2026-11-13"))
    }

    @Test
    fun recentHeadlinesAreTheLastFiveDaysBeforeToday() {
        val editions = (3..12).filter { it != 9 }.map { edition("2026-11-${it.toString().padStart(2, '0')}") } + edition("2026-11-13") + edition("2026-11-09", headline = null)
        val recent = recentHeadlines(editions, today = "2026-11-13")
        assertEquals(listOf("2026-11-12", "2026-11-11", "2026-11-10", "2026-11-08", "2026-11-07"), recent.map { it.day })
        assertEquals("Headline of 2026-11-12", recent.first().headline)
    }

    @Test
    fun theMastheadMottoAndSectionsAreReadBackNewestFirst() {
        val newest = "<body><!-- motto: Read through one reader's trust | first edition --><!-- sections: The Diary · Long Reads · The Classifieds --></body>"
        val older = "<body><!-- masthead: The Purple Gazette | the network voted --><!-- motto: An older line | why --></body>"
        val paper = standingPaper(listOf(newest, older))
        assertEquals("The Purple Gazette", paper.masthead)
        assertEquals("Read through one reader's trust", paper.motto)
        assertEquals(listOf("The Diary", "Long Reads", "The Classifieds"), paper.sections)
        assertEquals(MASTHEAD, standingPaper(emptyList()).masthead)
        // The writer's words come back as one plain line: no markup.
        assertEquals("Bold name", announcedIn("<!-- masthead: <b>Bold</b>\n name | x -->").masthead)
        assertNull(announcedIn("<!-- restyle: black borders -->").masthead)
    }

    @Test
    fun theReadersClockTellsTheDateAndTimeWithItsZone() {
        // 2026-11-13 12:47 UTC, read five hours behind.
        val at = 1_794_574_020L
        val clock = EditionClock("America/New_York", CronZone.fixed(-5 * 3600))
        assertEquals("Friday, November 13, 2026", clock.date(at))
        assertEquals("07:47", clock.time(at))
        assertEquals("America/New_York, UTC-05:00", clock.zoneLabel(at))
        assertEquals("Friday, November 13, 2026", longDay("2026-11-13"))
        assertEquals("Thursday, January 1, 1970", longDay("1970-01-01"))
        assertEquals("Tuesday, February 29, 2028", longDay("2028-02-29"))
    }

    @Test
    fun theHeaderCarriesTheEditionTheClockAndTheDay() {
        val c = corpus(mapOf("notes" to listOf(event(1, content = "hi"))))
        val context =
            EditionContext(
                paper = StandingPaper("The Purple Gazette", "A motto", listOf("The Diary")),
                number = 48,
                recent = listOf(RecentHeadline("2026-11-12", "Bitcoin loses seventy thousand")),
                clock = EditionClock("America/New_York", CronZone.fixed(-5 * 3600)),
            )
        val header = editionHeader("Alice", "pt-BR", context, c, DaySize(read = 11_106, voices = 234, kept = 554))
        listOf(
            "Masthead: The Purple Gazette",
            "Motto: A motto",
            "Standing sections: The Diary",
            "Edition: Vol. I · No. 48",
            "Edition code: ${c.code}",
            "Date: Monday, November 9, 2026",
            "Window: the 24 hours to 19:00, on the reader's clock (America/New_York, UTC-05:00)",
            "11106 events read, 234 voices, 554 kept in the digest",
            "- Thursday, November 12, 2026: Bitcoin loses seventy thousand",
        ).forEach { assertTrue(header.contains(it), it) }
        val message = corpusPrefix("DIGEST", header)
        assertTrue(message.indexOf("Edition:") < message.indexOf("<corpus>"))
    }
}
