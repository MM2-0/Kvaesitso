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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CalendarRsvpCard
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.CalendarAppointmentLines
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.CalendarDateBadge
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.detailRouteFor
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatCalendarRange
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.rememberRelativeTimeLabel
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Entry for a NIP-52 calendar RSVP: decodes the [Note], makes sure the appointment it answers is
 * in the cache, and renders the shared commons [CalendarRsvpCard] with that appointment merged
 * into its frame — "going" is only interesting next to *what*. Until the appointment loads the
 * card shows its status strip over a "loading" line.
 *
 * [LoadAddressableNote] creates the [com.vitorpamplona.amethyst.commons.model.AddressableNote] in
 * `LocalCache` — a shell with a null event if we have never seen the appointment — and
 * [WatchNoteEvent]'s event-finder subscription then asks relays for it: `filterMissingAddressables`
 * picks up exactly those addressables whose `event == null` and queries the address author's
 * outbox relays plus any stored hints.
 *
 * Both halves matter beyond drawing this card. `EventBroadcaster` routes an RSVP by following its
 * `a` tag into the appointment and reading the participants off it, and every step of that walk
 * is a `LocalCache` lookup. An RSVP seen in a feed for an appointment that was never cached would
 * otherwise leave the cache with no entry to walk, so answering it from here would reach the host
 * (whose pubkey the coordinate carries) but none of the other invitees.
 *
 * Composition-scoped like every other per-note subscription: the row unsubscribes ~30s after it
 * scrolls away or the app backgrounds.
 */
@Composable
fun RenderCalendarRSVPEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CalendarRSVPEvent ?: return
    val address = remember(event) { event.calendarEventAddress() }

    if (address == null) {
        CalendarRsvpCard(event)
        return
    }

    LoadAddressableNote(address) { appointment ->
        if (appointment == null) {
            CalendarRsvpCard(event)
        } else {
            // WatchNoteEvent subscribes the event finder only while the appointment is missing.
            // Once it is here the card has everything it draws; a standing subscription would
            // keep asking relays for the appointment's reactions and zaps on every RSVP in the feed.
            WatchNoteEvent(
                baseNote = appointment,
                onNoteEventFound = { RsvpWithAppointment(event, appointment, accountViewModel, nav) },
                onBlank = { CalendarRsvpCard(event) },
                accountViewModel = accountViewModel,
            )
        }
    }
}

/**
 * The appointment drawn *inside* the RSVP's frame, under its status strip: cover image full
 * width, then the same date badge and text lines the calendar list uses. No author header — the
 * post already says who answered — and no card of its own, so there is one frame, not two.
 * "In 3 days" moves up into the strip, next to the answer it qualifies.
 */
@Composable
private fun RsvpWithAppointment(
    event: CalendarRSVPEvent,
    appointment: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val view = appointment.appointmentView()
    if (view == null) {
        CalendarRsvpCard(event)
        return
    }
    val timeFormatter = rememberTimeOfDayFormatter()
    val appointmentEvent = appointment.event
    val range = remember(appointmentEvent) { formatCalendarRange(appointment, timeFormatter) }
    val relative = rememberRelativeTimeLabel(view, appointmentEvent?.id)
    val route = remember(appointment) { detailRouteFor(appointment) }
    // Past a week the relative formatter falls back to a plain date ("October 31"), which the
    // badge and the time line right below already show, so the strip says nothing instead.
    val startsWithinAWeek = remember(view.startSeconds) { startsWithinAWeek(view.startSeconds, TimeUtils.now()) }

    CalendarRsvpCard(
        event = event,
        statusDetail = relative?.takeIf { startsWithinAWeek },
        onClick = { nav.nav(route) },
    ) {
        val image = view.image
        if (!image.isNullOrBlank()) {
            MyAsyncImage(
                imageUrl = image,
                contentDescription = view.title,
                contentScale = ContentScale.Crop,
                mainImageModifier = CoverImageModifier,
                loadedImageModifier = Modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { Box(CoverImageModifier) },
                onError = null,
            )
        }

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CalendarDateBadge(view.startSeconds)
            Spacer(modifier = Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CalendarAppointmentLines(view, range, relative = null)
            }
        }
    }
}

/**
 * Whether [startSeconds] is close enough for "in 3 days" to say something the date does not:
 * anything that has started, or starts within a week. A missing start has no phrase at all.
 */
fun startsWithinAWeek(
    startSeconds: Long?,
    nowSeconds: Long,
): Boolean = startSeconds != null && startSeconds - nowSeconds <= TimeUtils.ONE_WEEK

private val CoverImageModifier = Modifier.fillMaxWidth().aspectRatio(2f)

// --------------------------------------------------------------------------------------------
// Previews
// --------------------------------------------------------------------------------------------

private const val PREVIEW_HOST = "c07b08396b7bffdb659f862dd7ead57ae169caea65ed573d161e13c1cd6d490c"
private const val PREVIEW_FRIEND = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

private fun previewConference() =
    CalendarTimeSlotEvent(
        id = "5c1a0e3b8f2d4a6e9b7c1d3f5a7e9c2b4d6f8a1c3e5b7d9f2a4c6e8b1d3f5a7e",
        pubKey = PREVIEW_HOST,
        createdAt = 1788000000,
        tags =
            arrayOf(
                arrayOf("d", "nostr-conference-2026"),
                arrayOf("title", "Nostr Conference 2026"),
                arrayOf("image", "https://image.nostr.build/conference-cover.jpg"),
                arrayOf("start", "1791622800"),
                arrayOf("end", "1791655200"),
                arrayOf("start_tzid", "Europe/Lisbon"),
                arrayOf("location", "Lisbon, Portugal"),
            ),
        content = "Two days of talks, workshops and hacking on the protocol.",
        sig = "0".repeat(128),
    )

private fun previewMeetup() =
    CalendarDateSlotEvent(
        id = "8e2c4a6f1b3d5e7a9c2e4b6d8f1a3c5e7b9d2f4a6c8e1b3d5f7a9c2e4b6d8f1a",
        pubKey = PREVIEW_HOST,
        createdAt = 1788000000,
        tags =
            arrayOf(
                arrayOf("d", "bitcoin-meetup"),
                arrayOf("title", "Bitcoin Meetup: open discussion"),
                arrayOf("start", "2026-10-24"),
                arrayOf("location", "Restaurant Bjørk, Bodø"),
            ),
        content = "",
        sig = "0".repeat(128),
    )

private fun previewRsvp(
    id: String,
    target: Address,
    status: RSVPStatusTag.STATUS,
    comment: String,
) = CalendarRSVPEvent(
    id = id,
    pubKey = PREVIEW_FRIEND,
    createdAt = 1789000000,
    tags =
        arrayOf(
            arrayOf("d", "rsvp-$id"),
            arrayOf("a", target.toValue()),
            status.toTagArray(),
            arrayOf("p", target.pubKeyHex),
        ),
    content = comment,
    sig = "0".repeat(128),
)

@Composable
private fun PreviewRsvpOf(
    appointment: Event,
    rsvp: CalendarRSVPEvent,
) {
    LocalCache.justConsume(appointment, null, true)
    LocalCache.justConsume(rsvp, null, true)
    val note = LocalCache.getOrCreateNote(rsvp.id)

    ThemeComparisonColumn {
        RenderCalendarRSVPEvent(note, mockAccountViewModel(), EmptyNav())
    }
}

@Composable
@Preview
fun RenderCalendarRsvpGoingPreview() {
    val conference = previewConference()
    PreviewRsvpOf(
        conference,
        previewRsvp(
            id = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
            target = conference.address(),
            status = RSVPStatusTag.STATUS.ACCEPTED,
            comment = "See you all there!",
        ),
    )
}

@Composable
@Preview
fun RenderCalendarRsvpMaybeNoImagePreview() {
    val meetup = previewMeetup()
    PreviewRsvpOf(
        meetup,
        previewRsvp(
            id = "b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1",
            target = meetup.address(),
            status = RSVPStatusTag.STATUS.TENTATIVE,
            comment = "",
        ),
    )
}

/** The appointment has not arrived yet: the status strip over the loading line. */
@Composable
@Preview
fun CalendarRsvpCardLoadingPreview() {
    val rsvp =
        previewRsvp(
            id = "c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2",
            target = Address(CalendarTimeSlotEvent.KIND, PREVIEW_HOST, "not-fetched-yet"),
            status = RSVPStatusTag.STATUS.ACCEPTED,
            comment = "Who else is coming?",
        )

    ThemeComparisonColumn {
        CalendarRsvpCard(rsvp)
    }
}
