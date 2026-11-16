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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.MONTH_GRID_MAX_LANES
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.MonthGridBarSegment
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarNavigationHeader
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.calendarDayA11yLabel
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.calendarSwipeNavigation
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_day_a11y_selected_suffix
import com.vitorpamplona.amethyst.commons.resources.calendar_day_a11y_today_suffix
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_next_month
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_previous_month
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatMonthYear
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatShortWeekday
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun CalendarMonthView(
    model: CalendarsViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Both derived on the model, off the main thread, and shared with the other lenses.
    val eventsByDay by model.eventsByDay.collectAsStateWithLifecycle()
    val barsByDay by model.monthBars.collectAsStateWithLifecycle()

    val today = model.today
    val visibleMonth = model.visibleMonth

    val selectedEvents = model.selectedDayKey?.let { eventsByDay[it] }.orEmpty()

    // Single LazyColumn — nav header + weekday header + grid scroll together with the
    // disappearing top bar so the grid doesn't stay pinned mid-screen when the bar collapses.
    LazyColumn(
        state = model.monthListState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
        modifier =
            Modifier
                .fillMaxSize()
                .calendarSwipeNavigation(
                    key = visibleMonth,
                    onSwipeLeft = { model.showMonth(visibleMonth.firstOfMonth(1)) },
                    onSwipeRight = { model.showMonth(visibleMonth.firstOfMonth(-1)) },
                ),
    ) {
        item(key = "month-nav") {
            CalendarNavigationHeader(
                title = formatMonthYear(visibleMonth.year, visibleMonth.month - 1),
                prevContentDescription = stringRes(Res.string.calendar_nav_previous_month),
                nextContentDescription = stringRes(Res.string.calendar_nav_next_month),
                onPrev = { model.showMonth(visibleMonth.firstOfMonth(-1)) },
                onNext = { model.showMonth(visibleMonth.firstOfMonth(1)) },
                onToday = { model.showMonth(LocalClock.today().firstOfMonth()) },
            )
        }

        item(key = "month-weekday-header") {
            WeekdayHeader()
        }

        item(key = "month-grid") {
            MonthGrid(
                visibleMonth = visibleMonth,
                today = today,
                barsByDay = barsByDay,
                selectedDayKey = model.selectedDayKey,
                onDayClick = { dayKey ->
                    model.selectedDayKey = if (model.selectedDayKey == dayKey) null else dayKey
                },
            )
        }

        item(key = "month-grid-spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selectedEvents.isNotEmpty()) {
            items(selectedEvents, key = { it.idHex }) { note ->
                CalendarEventListCard(note, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        for (i in 0..6) {
            Text(
                text = formatShortWeekday(i),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    visibleMonth: SearchDate,
    today: SearchDate,
    barsByDay: Map<Long, List<MonthGridBarSegment>>,
    selectedDayKey: Long?,
    onDayClick: (Long) -> Unit,
) {
    val firstOfMonth = visibleMonth.firstOfMonth()
    // Sunday = 0, matching `formatShortWeekday`.
    val firstWeekdayIndex = firstOfMonth.dayOfWeek()
    val daysInMonth = SearchDate.lastDayOfMonth(visibleMonth.year, visibleMonth.month)
    val rows = ((firstWeekdayIndex + daysInMonth + 6) / 7)
    val isCurrentMonth = visibleMonth.sameMonth(today)

    Column(modifier = Modifier.fillMaxWidth()) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val cellIndex = r * 7 + c
                    val dayNumber = cellIndex - firstWeekdayIndex + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = SearchDate(visibleMonth.year, visibleMonth.month, dayNumber)
                        val dayKey = date.daysFromEpoch()
                        val cellBars = barsByDay[dayKey].orEmpty()
                        val isToday = isCurrentMonth && date == today
                        val isSelected = selectedDayKey == dayKey
                        DayCell(
                            modifier = Modifier.weight(1f),
                            dayNumber = dayNumber,
                            isToday = isToday,
                            isSelected = isSelected,
                            bars = cellBars,
                            // Anything past the visible lane cap collapses into a "+N" tail —
                            // keeps each cell readable when a day has more than three events.
                            extraEventCount = cellBars.count { it.lane >= MONTH_GRID_MAX_LANES },
                            isWeekStart = c == 0,
                            isWeekEnd = c == 6,
                            // Full date label fed to the screen-reader content description so
                            // TalkBack reads "Wednesday January 15 2025, 2 events" instead of
                            // just "15".
                            dateLabel = formatLongDate(LocalClock.startOfDay(date)),
                            totalEventCount = cellBars.size,
                            onClick = { onDayClick(dayKey) },
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).height(MONTH_CELL_HEIGHT))
                    }
                }
            }
        }
    }
}

private val MONTH_CELL_HEIGHT = 72.dp

@Composable
private fun DayCell(
    modifier: Modifier,
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    bars: List<MonthGridBarSegment>,
    extraEventCount: Int,
    isWeekStart: Boolean,
    isWeekEnd: Boolean,
    dateLabel: String,
    totalEventCount: Int,
    onClick: () -> Unit,
) {
    val bg =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    val baseDescription = calendarDayA11yLabel(dateLabel, totalEventCount)
    val todaySuffix = stringRes(Res.string.calendar_day_a11y_today_suffix)
    val selectedSuffix = stringRes(Res.string.calendar_day_a11y_selected_suffix)
    val a11y =
        buildString {
            append(baseDescription)
            if (isToday) append(", ").append(todaySuffix)
            if (isSelected) append(", ").append(selectedSuffix)
        }

    Box(
        modifier =
            modifier
                .height(MONTH_CELL_HEIGHT)
                // Vertical-only padding so adjacent cells in a row touch horizontally — a
                // multi-day bar that extends from the right edge of one cell to the left edge of
                // the next visually merges into a single uninterrupted line.
                .padding(vertical = 2.dp)
                .background(bg, RoundedCornerShape(8.dp))
                .border(
                    width = if (isToday) 1.5.dp else 0.5.dp,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                ).clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = a11y },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color =
                    if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            Spacer(modifier = Modifier.height(2.dp))
            EventBarLanes(
                bars = bars,
                extraEventCount = extraEventCount,
                isWeekStart = isWeekStart,
                isWeekEnd = isWeekEnd,
            )
        }
    }
}

/**
 * Renders up to [MONTH_GRID_MAX_LANES] horizontal bars stacked vertically. Each lane occupies a
 * fixed height across every cell so a multi-day event sits on the same y-row in every column it
 * covers — the visual continuity that makes "spans 3 days" readable at a glance.
 *
 * The bar is rounded only at the event's start (`isLeftEnd`) and end (`isRightEnd`). On week
 * boundaries we also round so each row of the grid looks self-contained instead of bleeding into
 * an unaligned next row.
 */
@Composable
private fun EventBarLanes(
    bars: List<MonthGridBarSegment>,
    extraEventCount: Int,
    isWeekStart: Boolean,
    isWeekEnd: Boolean,
) {
    val barColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (i in 0 until MONTH_GRID_MAX_LANES) {
            val seg = bars.firstOrNull { it.lane == i }
            if (seg != null) {
                val roundLeft = seg.isLeftEnd || isWeekStart
                val roundRight = seg.isRightEnd || isWeekEnd
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(
                                color = barColor,
                                shape =
                                    RoundedCornerShape(
                                        topStart = if (roundLeft) 2.dp else 0.dp,
                                        bottomStart = if (roundLeft) 2.dp else 0.dp,
                                        topEnd = if (roundRight) 2.dp else 0.dp,
                                        bottomEnd = if (roundRight) 2.dp else 0.dp,
                                    ),
                            ),
                )
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(5.dp))
            }
        }
        if (extraEventCount > 0) {
            Text(
                text = "+$extraEventCount",
                style = MaterialTheme.typography.labelSmall,
                color = barColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
