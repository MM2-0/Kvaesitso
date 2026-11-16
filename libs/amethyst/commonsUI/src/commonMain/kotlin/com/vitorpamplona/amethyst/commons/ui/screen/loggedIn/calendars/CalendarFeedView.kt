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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.calendarEndSeconds
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.calendarStartSeconds
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarEmptyState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_empty_feed_subtitle
import com.vitorpamplona.amethyst.commons.resources.calendar_empty_feed_title
import com.vitorpamplona.amethyst.commons.resources.calendar_section_past
import com.vitorpamplona.amethyst.commons.resources.calendar_section_upcoming
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchScrollToTop
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils

@Composable
fun CalendarFeedView(
    feedState: FeedContentState,
    model: CalendarsViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(feedState, true) {
        val state by feedState.feedContent.collectAsStateWithLifecycle()

        when (val s = state) {
            is FeedState.Loaded -> CalendarFeedLoadedBody(feedState, model, accountViewModel, nav)
            is FeedState.Empty -> CalendarFeedEmpty()
            is FeedState.Loading -> Box(modifier = Modifier.fillMaxSize())
            is FeedState.FeedError -> CalendarFeedError(s)
        }
    }
}

@Composable
private fun CalendarFeedLoadedBody(
    feedState: FeedContentState,
    model: CalendarsViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Split on the model, off the main thread, from the same filtered list the other lenses read.
    val split by model.upcomingPast.collectAsStateWithLifecycle()

    // The list state lives on the screen's ViewModel: a remembered one is rebuilt from scratch
    // when the screen comes back from an appointment, dropping the reader at the top of the feed
    // on every tap.
    //
    // WatchScrollToTop: without it the top-bar filter switch fires `sendToTop()`, but the
    // LazyColumn never hears it — so the scroll position from the previous filter (e.g. mid-way
    // through a tiny People List) is preserved when the user flips back to Global, leaving the
    // user staring at the past-events section of a 100-item feed instead of the top.
    val listState = model.feedListState
    WatchScrollToTop(feedState, listState)

    LazyColumn(
        state = listState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (split.upcoming.isNotEmpty()) {
            item(key = "section-upcoming") {
                SectionHeader(stringRes(Res.string.calendar_section_upcoming))
            }
            items(split.upcoming, key = { it.idHex }) { note ->
                CalendarEventListCard(note, accountViewModel, nav)
            }
        }

        if (split.past.isNotEmpty()) {
            item(key = "section-past") {
                if (split.upcoming.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                SectionHeader(stringRes(Res.string.calendar_section_past))
            }
            items(split.past, key = { it.idHex }) { note ->
                CalendarEventListCard(note, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun CalendarFeedEmpty() {
    CalendarEmptyState(
        title = stringRes(Res.string.calendar_empty_feed_title),
        subtitle = stringRes(Res.string.calendar_empty_feed_subtitle),
    )
}

@Composable
private fun CalendarFeedError(state: FeedState.FeedError) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

data class UpcomingPastSplit(
    val upcoming: List<Note>,
    val past: List<Note>,
)

/**
 * Returns only notes whose appointment address is in [filterAddresses]. Pass null to skip
 * filtering (the common path when "All" is selected). Lives here as a shared helper so each
 * view body can keep its own collection logic and just opt-into the filter via one call.
 */
fun List<Note>.applyCalendarFilter(filterAddresses: Set<com.vitorpamplona.quartz.nip01Core.core.Address>?): List<Note> {
    if (filterAddresses == null) return this
    return filter { note ->
        val addr =
            (note.event as? com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent)?.address()
        addr != null && addr in filterAddresses
    }
}

/**
 * [nowSeconds] is taken as a parameter (rather than reading `TimeUtils.now()` internally) so the
 * split can be unit-tested deterministically and so callers that already snapshot `now` for a
 * sort don't read the clock twice.
 */
fun partitionUpcomingPast(
    items: List<Note>,
    nowSeconds: Long = TimeUtils.now(),
): UpcomingPastSplit {
    val upcoming = mutableListOf<Note>()
    val past = mutableListOf<Note>()
    items.forEach {
        val s = it.calendarStartSeconds() ?: return@forEach
        // An event that started yesterday but ends tomorrow is "happening now", not over.
        // Fall back to start when end is missing so the legacy single-instant behaviour is kept.
        val effectiveEnd = it.calendarEndSeconds() ?: s
        if (effectiveEnd >= nowSeconds) {
            upcoming.add(it)
        } else {
            past.add(it)
        }
    }
    return UpcomingPastSplit(upcoming, past)
}
