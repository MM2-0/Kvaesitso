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
package com.vitorpamplona.amethyst.calendar

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.computeMonthGridBars
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonthGridBarsTest {
    @Test
    fun singleDayEvent_oneSegment_bothEndsRounded() {
        val note = dateSlot("a", "2025-01-15")
        val key = LocalDate.of(2025, 1, 15).toEpochDay()
        val byDay = computeMonthGridBars(listOf(note))
        val seg = byDay[key]?.single()
        assertNotNull(seg)
        assertTrue("single-day event should round both ends", seg!!.isLeftEnd && seg.isRightEnd)
        assertEquals(0, seg.lane)
    }

    @Test
    fun threeDayEvent_segmentPerDay_endsOnlyOnBoundaries() {
        val note = dateSlot("a", "2025-01-15", end = "2025-01-17")
        val byDay = computeMonthGridBars(listOf(note))
        val k15 = LocalDate.of(2025, 1, 15).toEpochDay()
        val k16 = LocalDate.of(2025, 1, 16).toEpochDay()
        val k17 = LocalDate.of(2025, 1, 17).toEpochDay()
        assertEquals(true to false, byDay[k15]!!.single().run { isLeftEnd to isRightEnd })
        assertEquals(false to false, byDay[k16]!!.single().run { isLeftEnd to isRightEnd })
        assertEquals(false to true, byDay[k17]!!.single().run { isLeftEnd to isRightEnd })
    }

    @Test
    fun overlappingEvents_assignedToDistinctLanes() {
        // A: Jan 15–17. B: Jan 16–18. They overlap on 16 and 17 so must land in different lanes.
        val a = dateSlot("a", "2025-01-15", end = "2025-01-17")
        val b = dateSlot("b", "2025-01-16", end = "2025-01-18")
        val byDay = computeMonthGridBars(listOf(a, b))
        val k16 = LocalDate.of(2025, 1, 16).toEpochDay()
        val lanes = byDay[k16]!!.map { it.lane }.toSet()
        assertEquals("expected two distinct lanes on the overlap day", 2, lanes.size)
    }

    @Test
    fun longerEventTakesLowerLane_amongTies() {
        // Earliest-start ties broken by length-descending: the longer event sits on lane 0 so it
        // visually anchors the top, with the shorter one tucked under it.
        val long3 = dateSlot("L", "2025-01-15", end = "2025-01-17")
        val short1 = dateSlot("S", "2025-01-15")
        val byDay = computeMonthGridBars(listOf(short1, long3))
        val k15 = byDay[LocalDate.of(2025, 1, 15).toEpochDay()]!!
        val longLane = k15.first { it.note === long3 }.lane
        val shortLane = k15.first { it.note === short1 }.lane
        assertTrue("longer event should be in a lower lane", longLane < shortLane)
    }

    @Test
    fun nonOverlappingEvents_reuseLowestLane() {
        // A: Jan 15. B: Jan 16. C: Jan 17. No overlaps → all on lane 0.
        val a = dateSlot("a", "2025-01-15")
        val b = dateSlot("b", "2025-01-16")
        val c = dateSlot("c", "2025-01-17")
        val byDay = computeMonthGridBars(listOf(a, b, c))
        for (note in listOf(a, b, c)) {
            val key =
                note.event!!
                    .tags
                    .first { it[0] == "start" }[1]
                    .let(LocalDate::parse)
                    .toEpochDay()
            assertEquals(0, byDay[key]!!.single().lane)
        }
    }

    @Test
    fun noEvents_emptyMap() {
        val byDay = computeMonthGridBars(emptyList())
        assertTrue(byDay.isEmpty())
    }

    @Test
    fun noteWithoutStart_dropped() {
        val ghost = Note("ghost") // no event
        val real = dateSlot("a", "2025-01-15")
        val byDay = computeMonthGridBars(listOf(ghost, real))
        assertEquals(1, byDay.size)
        assertNull(byDay[0L])
    }

    private fun dateSlot(
        id: String,
        start: String,
        end: String? = null,
    ): Note {
        val tags =
            buildList {
                add(arrayOf("d", "$id-d"))
                add(arrayOf("title", "T"))
                add(arrayOf("start", start))
                end?.let { add(arrayOf("end", it)) }
            }.toTypedArray()
        val e = CalendarDateSlotEvent(id, "pub", 0L, tags, "", "sig")
        return Note(id).apply { event = e }
    }
}
