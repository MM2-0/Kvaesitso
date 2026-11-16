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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_next_month
import com.vitorpamplona.amethyst.commons.resources.calendar_nav_previous_month
import com.vitorpamplona.amethyst.commons.search.ActivePicker
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.MonthGridDay
import com.vitorpamplona.amethyst.commons.search.calendar.SearchCalendar
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * The calendar that opens under a half-written `since:`/`until:` token.
 *
 * A pick writes an absolute day into the field rather than a relative one, so a search that is
 * saved, shared or bookmarked still means the same window tomorrow — which is why the quick picks
 * below the grid are labelled relatively ("Last 7 days") but resolve to a date before they are
 * written.
 */
@Composable
fun SearchCalendarPicker(
    picker: ActivePicker.Calendar,
    month: SearchDate,
    cursor: SearchDate?,
    onPick: (String) -> Unit,
    onStepMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalClock.today() }
    val grid = remember(month, today) { SearchCalendar.monthGrid(month, today) }
    val headings = remember { SearchCalendar.weekdayHeadings() }
    val quickPicks = remember(picker.dateField, today) { SearchCalendar.quickPicks(picker.dateField, today) }

    Column(modifier.padding(bottom = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                picker.dateField.heading,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${picker.dateField.token}:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            MonthStep(MaterialSymbols.AutoMirrored.ArrowBack, stringRes(Res.string.calendar_nav_previous_month)) { onStepMonth(-1) }
            Text(grid.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            MonthStep(MaterialSymbols.AutoMirrored.ArrowForward, stringRes(Res.string.calendar_nav_next_month)) { onStepMonth(1) }
        }

        // Seven columns of headings, then the blanks before the 1st, then the days. Keeping the
        // blanks as cells is what puts the 1st under the right weekday.
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            userScrollEnabled = false,
        ) {
            items(headings) { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            items(grid.lead) { Box(Modifier.size(36.dp)) }
            items(grid.days) { day -> DayCell(day, day.date == cursor, onPick) }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            quickPicks.forEach { pick ->
                TextButton(onClick = { onPick(pick.value) }, contentPadding = PaddingValues(6.dp, 2.dp)) {
                    Text(pick.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MonthStep(
    icon: MaterialSymbol,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCell(
    day: MonthGridDay,
    isCursor: Boolean,
    onPick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background =
        when {
            isCursor -> scheme.primary
            day.isToday -> scheme.primaryContainer
            else -> Color.Transparent
        }
    val content =
        when {
            isCursor -> scheme.onPrimary
            day.isToday -> scheme.onPrimaryContainer
            // A day in the future is still pickable — an `until:` next week is a valid window —
            // but it is dimmed, because no event has been written on it yet.
            day.isAhead -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
            else -> scheme.onSurface
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .padding(1.dp)
                .size(34.dp)
                .background(background, CircleShape)
                .clickable { onPick(day.value) },
    ) {
        Text(day.date.day.toString(), style = MaterialTheme.typography.bodySmall, color = content)
    }
}

/** The shell both pickers are drawn in: one raised surface under the field. */
@Composable
fun SearchPickerSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        content()
    }
}
