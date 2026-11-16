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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.CalendarAppointmentView
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatCalendarRange
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.rememberRelativeTimeLabel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video.UserCardHeader
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent

// Hoisted: previously each CalendarDateBadge recompose allocated a new date formatter, 500
// allocations while scrolling. It rebuilds itself when the default locale changes, so the month
// names follow a language the user changes while the app is running.
private val monthShortFormatter = DateSkeletonFormatter("MMM")

@Composable
fun CalendarEventListCard(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    // Drawn inside the card under the appointment, for lenses that annotate it (who's going).
    footer: (@Composable () -> Unit)? = null,
) {
    val view = note.appointmentView() ?: return
    val timeFormatter = rememberTimeOfDayFormatter()
    val range = remember(note.idHex) { formatCalendarRange(note, timeFormatter) }
    val relative = rememberRelativeTimeLabel(view, note.idHex)
    val event = note.event ?: return
    val detailRoute = remember(event.id) { detailRouteFor(note) }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { nav.nav(detailRoute) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        // Author header matches the picture-feed / shorts card shape: avatar + display name +
        // time-ago at the top of every social card in the app. Without this, calendar cards
        // looked alien next to the rest of the feed.
        UserCardHeader(
            baseNote = note,
            accountViewModel = accountViewModel,
            nav = nav,
            // Hide the "posted N ago" timestamp — would be visually identical to the event's
            // own start time below and confuse users into reading the publication date as the
            // event date.
            showTimeAgo = false,
        )

        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CalendarDateBadge(view.startSeconds)

            Spacer(modifier = Modifier.size(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CalendarAppointmentLines(view, range, relative)
                val image = view.image
                val summary = view.summary
                if (!image.isNullOrBlank()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    MyAsyncImage(
                        imageUrl = image,
                        contentDescription = view.title,
                        contentScale = ContentScale.Crop,
                        mainImageModifier = Modifier.fillMaxWidth().height(120.dp),
                        loadedImageModifier = Modifier,
                        accountViewModel = accountViewModel,
                        onLoadingBackground = { Box(modifier = Modifier.fillMaxWidth().height(120.dp)) },
                        onError = { Box(modifier = Modifier.fillMaxWidth().height(120.dp)) },
                    )
                }
                if (!summary.isNullOrBlank() && image.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        footer?.invoke()
    }
}

/**
 * Title, time range, relative time and location of an appointment — the text half of every
 * calendar row. Shared by the calendar list card and the feed's RSVP card so the two cannot
 * disagree about how an event reads. Pass a null [relative] where the caller shows it elsewhere.
 */
@Composable
fun CalendarAppointmentLines(
    view: CalendarAppointmentView,
    range: String?,
    relative: String?,
) {
    view.title?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    range?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    relative?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    view.location?.let {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = MaterialSymbols.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(4.dp))
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

@Composable
fun CalendarDateBadge(startSeconds: Long?) {
    if (startSeconds == null) {
        Box(
            modifier = Modifier.size(width = 52.dp, height = 60.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    val localDate =
        remember(startSeconds) {
            SearchDate.civilFromDays(LocalClock.epochDayCounter().epochDay(startSeconds))
        }
    val day = localDate.day.toString()
    // Keyed on Locale.current so a locale change while the app runs re-formats
    // the month name instead of leaving it in the old language.
    val locale = Locale.current
    val month = remember(startSeconds, locale) { monthShortFormatter.format(startSeconds * 1000).uppercase() }

    Column(
        modifier = Modifier.size(width = 52.dp, height = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = month,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = day,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Where a calendar row goes when it is tapped: the appointment's own screen for an addressable
 * event — which every 31922/31923 is — and the generic thread view for anything else that somehow
 * reaches a calendar list. Shared so the feed card and the day-view row can't drift apart.
 */
fun detailRouteFor(note: Note): Route {
    val address = (note.event as? BaseAddressableEvent)?.address()
    return address?.let { Route.CalendarEventDetail(it) } ?: Route.Note(note.idHex)
}
