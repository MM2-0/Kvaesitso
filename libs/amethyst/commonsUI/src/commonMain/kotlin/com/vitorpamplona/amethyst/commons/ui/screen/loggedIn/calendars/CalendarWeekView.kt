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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarEmptyState
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarNavigationHeader
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.calendarDayA11yLabel
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.calendarSwipeNavigation
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_day_a11y_selected_suffix
import com.vitorpamplona.amethyst.commons.resources.calendar_day_a11y_today_suffix
import com.vitorpamplona.amethyst.commons.resources.calendar_empty_week_subtitle
import com.vitorpamplona.amethyst.commons.resources.calendar_empty_week_title
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_next_week
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_previous_week
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
fun CalendarWeekView(
    model: CalendarsViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val eventsByDay by model.eventsByDay.collectAsStateWithLifecycle()

    val today = model.today
    val weekStart = model.weekStart

    val selectedDate = model.selectedWeekDate
    val dayNotes = eventsByDay[selectedDate.daysFromEpoch()].orEmpty()

    // Single LazyColumn containing nav + strip + day-summary + events so the whole stack
    // scrolls together with the [DisappearingScaffold]'s top bar. The previous Column-with-
    // disappearingScaffoldPadding kept the strip pinned at a fixed offset, so when the top bar
    // collapsed on scroll the strip stayed put and left a visual gap.
    LazyColumn(
        state = model.weekListState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
        modifier =
            Modifier
                .fillMaxSize()
                .calendarSwipeNavigation(
                    key = model.weekStartEpochDay,
                    onSwipeLeft = { model.shiftWeeks(1) },
                    onSwipeRight = { model.shiftWeeks(-1) },
                ),
    ) {
        item(key = "week-nav") {
            CalendarNavigationHeader(
                title = formatMonthYear(weekStart.year, weekStart.month - 1),
                prevContentDescription = stringRes(Res.string.calendar_nav_previous_week),
                nextContentDescription = stringRes(Res.string.calendar_nav_next_week),
                onPrev = { model.shiftWeeks(-1) },
                onNext = { model.shiftWeeks(1) },
                onToday = { model.showWeekOf(LocalClock.today()) },
            )
        }

        item(key = "week-strip") {
            WeekStrip(
                weekStart = weekStart,
                today = today,
                selectedIndex = model.selectedDayIndex,
                eventsByDay = eventsByDay,
                onSelect = { model.selectedDayIndex = it },
            )
        }

        item(key = "week-spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item(key = "week-day-summary") {
            DaySummaryHeader(selectedDate)
        }

        if (dayNotes.isEmpty()) {
            item(key = "week-empty") {
                CalendarEmptyState(
                    title = stringRes(Res.string.calendar_empty_week_title),
                    subtitle = stringRes(Res.string.calendar_empty_week_subtitle),
                )
            }
        } else {
            items(dayNotes, key = { it.idHex }) { note ->
                CalendarEventListCard(note, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun WeekStrip(
    weekStart: SearchDate,
    today: SearchDate,
    selectedIndex: Int,
    eventsByDay: Map<Long, List<Note>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        for (i in 0..6) {
            val date = weekStart.plusDays(i)
            val count = eventsByDay[date.daysFromEpoch()]?.size ?: 0
            val isToday = date == today
            val isSelected = i == selectedIndex

            val bg =
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            val fg =
                when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }

            val dateLabel = formatLongDate(LocalClock.startOfDay(date))
            val baseA11y = calendarDayA11yLabel(dateLabel, count)
            val todaySuffix = stringRes(Res.string.calendar_day_a11y_today_suffix)
            val selectedSuffix = stringRes(Res.string.calendar_day_a11y_selected_suffix)
            val a11y =
                buildString {
                    append(baseA11y)
                    if (isToday) append(", ").append(todaySuffix)
                    if (isSelected) append(", ").append(selectedSuffix)
                }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(3.dp)
                        .background(bg, RoundedCornerShape(10.dp))
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp),
                        ).clickable(role = Role.Tab) { onSelect(i) }
                        .padding(vertical = 6.dp)
                        .semantics(mergeDescendants = true) { contentDescription = a11y },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatShortWeekday(i),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                )
                if (count > 0) {
                    Text(
                        text = if (count > 9) "9+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun DaySummaryHeader(date: SearchDate) {
    Text(
        text = formatLongDate(LocalClock.startOfDay(date)),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}
