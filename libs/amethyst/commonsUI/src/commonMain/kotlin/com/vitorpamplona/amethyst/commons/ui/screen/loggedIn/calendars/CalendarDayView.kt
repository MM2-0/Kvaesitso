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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.calendarLocalDayKeyRange
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarEmptyState
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarNavigationHeader
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.calendarSwipeNavigation
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_all_day
import com.vitorpamplona.amethyst.commons.resources.calendar_continues
import com.vitorpamplona.amethyst.commons.resources.calendar_day_of_total
import com.vitorpamplona.amethyst.commons.resources.calendar_empty_day_subtitle
import com.vitorpamplona.amethyst.commons.resources.calendar_empty_day_title
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_next_day
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_previous_day
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatTimeOfDay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun CalendarDayView(
    model: CalendarsViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val byDay by model.eventsByDay.collectAsStateWithLifecycle()

    // Stepping in calendar days rather than milliseconds keeps this DST-safe: millisecond stepping
    // was off by an hour after spring/fall transitions.
    val visibleEpochDay = model.visibleEpochDay
    val visibleDate = model.visibleDate

    val dayEvents = byDay[visibleDate.daysFromEpoch()].orEmpty()

    val sorted =
        remember(dayEvents) {
            // All-day events bubble to the top (Long.MIN_VALUE), then time-slot events in order.
            dayEvents.sortedBy { it.appointmentView()?.startSeconds ?: Long.MAX_VALUE }
        }

    // Single LazyColumn — nav header is the first item so it scrolls with the disappearing
    // top bar instead of staying pinned mid-screen when the bar collapses.
    LazyColumn(
        state = model.dayListState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
        modifier =
            Modifier
                .fillMaxSize()
                .calendarSwipeNavigation(
                    key = visibleEpochDay,
                    onSwipeLeft = { model.shiftDays(1) },
                    onSwipeRight = { model.shiftDays(-1) },
                ),
    ) {
        item(key = "day-nav") {
            CalendarNavigationHeader(
                title = formatLongDate(LocalClock.startOfDay(visibleDate)),
                prevContentDescription = stringRes(Res.string.calendar_nav_previous_day),
                nextContentDescription = stringRes(Res.string.calendar_nav_next_day),
                onPrev = { model.shiftDays(-1) },
                onNext = { model.shiftDays(1) },
                onToday = { model.visibleEpochDay = LocalClock.today().daysFromEpoch() },
            )
        }

        if (dayEvents.isEmpty()) {
            item(key = "day-empty") {
                CalendarEmptyState(
                    title = stringRes(Res.string.calendar_empty_day_title),
                    subtitle = stringRes(Res.string.calendar_empty_day_subtitle),
                )
            }
        } else {
            items(sorted, key = { it.idHex }) { note ->
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    DayRow(
                        note = note,
                        visibleEpochDay = visibleEpochDay,
                        // The appointment's own screen, like every other calendar row opens —
                        // not the generic thread view. `idHex` is the address for these
                        // (addressable) notes, so the old Route.Note(idHex) would have handed
                        // the thread screen an `naddr`-shaped id to resolve for no reason.
                        onClick = { nav.nav(detailRouteFor(note)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DayRow(
    note: Note,
    visibleEpochDay: Long,
    onClick: () -> Unit,
) {
    val view = note.appointmentView() ?: return
    val range = note.calendarLocalDayKeyRange()
    // Position within a multi-day event: today is "Day 2 of 4". Renders below the time label so
    // a continuation day on a 3-day conference reads as "9:00 AM / Day 2 of 3" rather than
    // looking like a fresh event.
    val dayOfTotal =
        if (range != null && range.last > range.first) {
            (visibleEpochDay - range.first + 1).toInt() to (range.last - range.first + 1).toInt()
        } else {
            null
        }

    val startSeconds = view.startSeconds
    val timeFormatter = rememberTimeOfDayFormatter()
    val timeLabel =
        when {
            view.isAllDay -> stringRes(Res.string.calendar_all_day)
            startSeconds != null && visibleEpochDay > (range?.first ?: visibleEpochDay) ->
                // Continuation day of a multi-day timed event — the "9:00 AM" of day 1 is
                // misleading on day 2 since the event has been ongoing overnight. Show a
                // continuation marker so the user reads it as "still happening".
                stringRes(Res.string.calendar_continues)
            startSeconds != null -> formatTimeOfDay(startSeconds, timeFormatter)
            else -> "—"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            view.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            dayOfTotal?.let { (day, total) ->
                Text(
                    text = stringRes(Res.string.calendar_day_of_total, day, total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            view.location?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
