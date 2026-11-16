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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.AppointmentAttendance
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarEmptyState
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_follows_going_count
import com.vitorpamplona.amethyst.commons.resources.calendar_follows_going_empty_subtitle
import com.vitorpamplona.amethyst.commons.resources.calendar_follows_going_empty_title
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.types.UserGallery
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** Faces drawn on a row before the rest collapse into "+N". */
private const val ATTENDEE_FACES = 6

/**
 * Keeps a relay request from growing without bound when the list follows people with a long RSVP
 * history of appointments this device has never seen.
 */
private const val MAX_APPOINTMENTS_TO_FETCH = 100

/**
 * The upcoming events the people in the selected list said they're going to, soonest first, each
 * with the faces of who's going.
 *
 * The RSVPs come in on the screen's calendar subscription (it asks the list's authors for their
 * kind-31925s). The appointments they point at are usually published by someone else — a
 * conference organizer the viewer does not follow — so the ones the cache is missing are fetched
 * here, by address, through the per-note event finder.
 */
@Composable
fun CalendarFollowsGoingView(
    model: CalendarsViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val state by model.followsGoing.collectAsStateWithLifecycle()
    val going = state
    if (going == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    FetchMissingAppointments(going.unresolved, accountViewModel)

    if (going.upcoming.isEmpty()) {
        CalendarEmptyState(
            title = stringRes(Res.string.calendar_follows_going_empty_title),
            subtitle = stringRes(Res.string.calendar_follows_going_empty_subtitle),
        )
        return
    }

    LazyColumn(
        state = model.followsGoingListState,
        contentPadding = rememberFeedContentPadding(FeedPadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(going.upcoming, key = { it.appointment.idHex }) { entry ->
            CalendarEventListCard(
                note = entry.appointment,
                accountViewModel = accountViewModel,
                nav = nav,
                footer = { AttendeesRow(entry, accountViewModel, nav) },
            )
        }
    }
}

@Composable
private fun FetchMissingAppointments(
    unresolved: List<AddressableNote>,
    accountViewModel: AccountViewModel,
) {
    unresolved.take(MAX_APPOINTMENTS_TO_FETCH).forEach { appointment ->
        key(appointment.idHex) {
            EventFinderFilterAssemblerSubscription(appointment, accountViewModel)
        }
    }
}

@Composable
private fun AttendeesRow(
    entry: AppointmentAttendance,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val shown = remember(entry) { entry.attendees.take(ATTENDEE_FACES).map(LocalCache::getOrCreateUser) }
    val total = entry.attendees.size

    Row(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserGallery(shown, total) { user ->
            UserPicture(user, Size25dp, accountViewModel = accountViewModel, nav = nav)
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = pluralStringRes(Res.plurals.calendar_follows_going_count, total, total),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
