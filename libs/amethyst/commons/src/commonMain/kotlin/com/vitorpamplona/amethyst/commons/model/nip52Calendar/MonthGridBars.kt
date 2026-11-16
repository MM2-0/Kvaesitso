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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note

/**
 * One bar drawn into one day cell, with `lane` controlling its vertical position so two
 * overlapping multi-day events stack rather than collide. `isLeftEnd` / `isRightEnd` control
 * which corners of the bar are rounded — a continuation day in the middle of a 3-day event gets
 * neither end rounded, so adjacent cells visually merge into one bar.
 *
 * Note: the underlying [Note] is exposed so the UI can colour or label bars per event. Equality
 * is on idHex so a row of cells holding the same bar share segment identity for keys.
 */
@Immutable
data class MonthGridBarSegment(
    val note: Note,
    val lane: Int,
    val isLeftEnd: Boolean,
    val isRightEnd: Boolean,
)

/**
 * Maximum lanes we render before collapsing the remainder into a "+N" overflow label. Three
 * matches the previous dot-row capacity and keeps each 56dp cell readable on mid-range phones.
 */
const val MONTH_GRID_MAX_LANES = 3

/**
 * Greedy lane-assignment for the month grid: sort events earliest-start-first (longer events
 * wins ties so they take the top lane), then for each event pick the lowest lane index whose
 * full day-range is unoccupied. Returns a per-day-key map so each cell can render its own
 * segments without re-running the layout.
 *
 * Single-day events participate in the same layout — they get bars too, just short ones,
 * which keeps the visual language consistent.
 */
fun computeMonthGridBars(notes: List<Note>): Map<Long, List<MonthGridBarSegment>> {
    val ranges =
        notes
            .distinctBy { it.idHex }
            .mapNotNull { n -> n.calendarLocalDayKeyRange()?.let { n to it } }
            .sortedWith(
                compareBy(
                    { it.second.first },
                    { -(it.second.last - it.second.first) },
                    { it.first.idHex },
                ),
            )

    // day-key → set of lanes already claimed for that day
    val occupied = mutableMapOf<Long, MutableSet<Int>>()
    val perDay = mutableMapOf<Long, MutableList<MonthGridBarSegment>>()

    for ((note, range) in ranges) {
        // Find the lowest lane index whose full range is free. Bounded at 32 so a pathological
        // input can't loop forever; overflow events still render as "+N" via the cap downstream.
        var lane = 0
        while (lane < 32) {
            val clash = (range).any { occupied[it]?.contains(lane) == true }
            if (!clash) break
            lane++
        }
        for (day in range) {
            occupied.getOrPut(day) { mutableSetOf() }.add(lane)
            perDay.getOrPut(day) { mutableListOf() }.add(
                MonthGridBarSegment(
                    note = note,
                    lane = lane,
                    isLeftEnd = day == range.first,
                    isRightEnd = day == range.last,
                ),
            )
        }
    }
    // Within each cell, sort by lane so the rendering doesn't have to.
    return perDay.mapValues { (_, list) -> list.sortedBy { it.lane } }
}
