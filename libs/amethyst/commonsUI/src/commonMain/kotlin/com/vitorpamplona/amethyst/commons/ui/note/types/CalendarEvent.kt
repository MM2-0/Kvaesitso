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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_time_in_zone
import com.vitorpamplona.amethyst.commons.resources.calendar_time_zone
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTimeInZone
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent

@Composable
fun RenderCalendarTimeSlotEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? CalendarTimeSlotEvent ?: return

    val title = noteEvent.title()
    val image = noteEvent.image()
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }
    val location = noteEvent.location()
    val dateRange =
        remember(noteEvent) {
            val start = noteEvent.start()
            val end = noteEvent.end()
            formatTimestampRange(start, end)
        }

    val timeZoneLine = calendarTimeZoneLine(noteEvent.start(), noteEvent.startTzId())

    CalendarHeader(
        title = title,
        image = image,
        summary = summary,
        location = location,
        dateRange = dateRange,
        timeZoneLine = timeZoneLine,
        note = note,
        accountViewModel = accountViewModel,
        rsvpRow = {
            CalendarRsvpRow(
                eventKind = CalendarTimeSlotEvent.KIND,
                eventPubKey = noteEvent.pubKey,
                eventDTag = noteEvent.dTag(),
                eventId = noteEvent.id,
                accountViewModel = accountViewModel,
            )
        },
    )
}

@Composable
fun RenderCalendarDateSlotEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? CalendarDateSlotEvent ?: return

    val title = noteEvent.title()
    val image = noteEvent.image()
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }
    val location = noteEvent.location()
    val dateRange =
        remember(noteEvent) {
            val start = noteEvent.start()
            val end = noteEvent.end()
            formatDateRange(start, end)
        }

    CalendarHeader(
        title = title,
        image = image,
        summary = summary,
        location = location,
        dateRange = dateRange,
        note = note,
        accountViewModel = accountViewModel,
        rsvpRow = {
            CalendarRsvpRow(
                eventKind = CalendarDateSlotEvent.KIND,
                eventPubKey = noteEvent.pubKey,
                eventDTag = noteEvent.dTag(),
                eventId = noteEvent.id,
                accountViewModel = accountViewModel,
            )
        },
    )
}

@Composable
private fun CalendarHeader(
    title: String?,
    image: String?,
    summary: String?,
    location: String?,
    dateRange: String?,
    note: Note,
    accountViewModel: AccountViewModel,
    timeZoneLine: String? = null,
    rsvpRow: @Composable () -> Unit = {},
) {
    Column(MaterialTheme.colorScheme.replyModifier) {
        image?.let {
            MyAsyncImage(
                imageUrl = it,
                contentDescription = stringRes(Res.string.preview_card_image_for, it),
                contentScale = ContentScale.FillWidth,
                mainImageModifier = Modifier.fillMaxWidth(),
                loadedImageModifier = Modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                onError = { DefaultImageHeader(note, accountViewModel) },
            )
        } ?: run {
            DefaultImageHeader(note, accountViewModel, Modifier.fillMaxWidth())
        }

        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        dateRange?.let {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        timeZoneLine?.let {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        location?.let {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        summary?.let {
            Spacer(modifier = StdVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (summary == null) {
            Spacer(modifier = StdVertSpacer)
        }

        rsvpRow()
    }
}

/**
 * The event's own time zone (NIP-52 `start_tzid`), for the line under its times, which are shown on
 * the reader's clock. When the reader is elsewhere it carries the start on the event's clock too,
 * "Mar 3, 2026, 7:00 PM (Europe/Oslo)", so a meetup in another city reads in both. Null when the
 * event names no zone.
 */
@Composable
fun calendarTimeZoneLine(
    start: Long?,
    tzId: String?,
): String? {
    val zone = tzId?.trim()?.ifBlank { null } ?: return null
    val atEventClock =
        remember(start, zone) {
            // Compare clocks, not names: "Etc/UTC" for a reader on "UTC", or a Paris event read in
            // Berlin, would otherwise repeat the very time already shown above.
            if (start == null || zone == LocalClock.zoneId() || LocalClock.utcOffsetSeconds(zone, start) == LocalClock.utcOffsetSeconds(start)) {
                null
            } else {
                formatDateTimeInZone(start * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT, zone)
            }
        }
    return if (atEventClock != null) {
        stringRes(Res.string.calendar_time_in_zone, atEventClock, zone)
    } else {
        stringRes(Res.string.calendar_time_zone, zone)
    }
}

private fun formatTimestampRange(
    start: Long?,
    end: Long?,
): String? {
    if (start == null) return null
    val startStr = formatDateTime(start * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)
    return if (end != null && end != start) {
        "$startStr – ${formatDateTime(end * 1000, DateTimeStyle.NONE, DateTimeStyle.SHORT)}"
    } else {
        startStr
    }
}

private fun formatDateRange(
    start: String?,
    end: String?,
): String? {
    if (start == null) return null
    return if (end != null && end != start) {
        "$start – $end"
    } else {
        start
    }
}

@Composable
@Preview
fun RenderCalendarTimeSlotEventPreview() {
    val event =
        CalendarTimeSlotEvent(
            id = "0c22faa881c52f32754e6f2f7188a1333388acf4bfb32ecc04edb48f3cf4fd5d",
            pubKey = "c07b08396b7bffdb659f862dd7ead57ae169caea65ed573d161e13c1cd6d490c",
            createdAt = 1773145949,
            tags =
                arrayOf(
                    arrayOf("d", "2cc67b34-f8c9-4bb2-aa80-df38d2c16903"),
                    arrayOf("t", "Meetup"),
                    arrayOf("r", ""),
                    arrayOf("title", "Bodø Bitcoin Meet -up!"),
                    arrayOf("image", "https://cdn.satlantis.io/1cpasswtt00lakevlscka06k40tsknjh2vhk4w0gkrcfurntdfyxqu6qges-1769516859659-ChatGPT%20Image%2027.%20jan.%202026%2C%2013_27_28.png"),
                    arrayOf("start", "1775671200"),
                    arrayOf("end", "1775674800"),
                    arrayOf("start_tzid", "Europe/Oslo"),
                    arrayOf("g", ""),
                    arrayOf("summary", "Bitcoin is for everyone — whether you’re just curious, stacking sats for the first time, or you’ve been around since the early days.\n\nThis meetup is an open and informal gathering for anyone interested in Bitcoin. There’s no strict agenda, no pressure, and no expectations. Just good conversations, shared curiosity, and a chance to meet like-minded people in Bodø.\n\nWho is this for?\n\nNew to Bitcoin and want to learn the basics\n\nLong-time Bitcoiners (OGs very welcome)\n\nBuilders, investors, students, professionals, skeptics, and the simply curious\n\nAnyone who wants to understand why Bitcoin matters\n\nWe’ll talk Bitcoin at whatever level feels natural — from fundamentals and real-world use cases to philosophy, technology, and current developments.\n\nDuring the meetup, I’ll also announce and briefly share details about the upcoming Bitcoin conference in Bodø on 14 August, bringing international and Nordic voices together around Bitcoin.\n\n📍 Location: Bodø, Bjørk\n🕒 Time: 20.00\n🍻 Format: Casual, social, open discussion\n\nCome as you are. Bring questions, ideas, or just yourself.\nEveryone is welcome."),
                    arrayOf("url", ""),
                    arrayOf("cohosts", "172100"),
                    arrayOf("calendar_event_tags", "education, meet-up, bitcoin, networking, fun"),
                    arrayOf("website", ""),
                    arrayOf("autoFollowHosts", "false"),
                    arrayOf("rsvp_gated_enabled", "false"),
                    arrayOf("allowCohostsToDownloadAttendeeList", "false"),
                    arrayOf("venue", "167911"),
                    arrayOf("location", "Storgata 8, 8006 Bodø, Norway"),
                    arrayOf("googleMapsUri", "https://maps.google.com/?cid=601501172909304252&g_mp=CiVnb29nbGUubWFwcy5wbGFjZXMudjEuUGxhY2VzLkdldFBsYWNlEAIYBCAA"),
                    arrayOf("venue_name", "Restaurant Bjørk"),
                    arrayOf("google_place_id", "ChIJYd5l1F0Q30URvGk9PyH2WAg"),
                    arrayOf("rsvp_waitlist_enabled", "false"),
                ),
            content = "Bitcoin is for everyone — whether you’re just curious, stacking sats for the first time, or you’ve been around since the early days.\n\nThis meetup is an open and informal gathering for anyone interested in Bitcoin. There’s no strict agenda, no pressure, and no expectations. Just good conversations, shared curiosity, and a chance to meet like-minded people in Bodø.\n\nWho is this for?\n\nNew to Bitcoin and want to learn the basics\n\nLong-time Bitcoiners (OGs very welcome)\n\nBuilders, investors, students, professionals, skeptics, and the simply curious\n\nAnyone who wants to understand why Bitcoin matters\n\nWe’ll talk Bitcoin at whatever level feels natural — from fundamentals and real-world use cases to philosophy, technology, and current developments.\n\nDuring the meetup, I’ll also announce and briefly share details about the upcoming Bitcoin conference in Bodø on 14 August, bringing international and Nordic voices together around Bitcoin.\n\n📍 Location: Bodø, Bjørk\n🕒 Time: 20.00\n🍻 Format: Casual, social, open discussion\n\nCome as you are. Bring questions, ideas, or just yourself.\nEveryone is welcome.",
            sig = "a63bdfc5d33432ef6a1454c7b395586430f5602b822ccd07b9d1ccd9608ecda8508b58374bff28ab87d9b9c92c9cd5a7e95bfe67587ccc852e3fea78c0c682c3",
        )

    LocalCache.justConsume(event, null, true)
    val note = LocalCache.getOrCreateNote(event.id)

    ThemeComparisonColumn {
        RenderCalendarTimeSlotEvent(
            note,
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
@Preview
fun RenderCalendarDateSlotEventPreview() {
    val event =
        CalendarDateSlotEvent(
            id = "d8d76e5a7786210684317fe0264cc44fdc8147254456c20a9fb99db1c090a37f",
            pubKey = "068598899f82af67fc67cfcc44457ed9a8cc686b7d63ab9db969c96c3fe9bffd",
            createdAt = 1771952727,
            content = "Join the OER Community #qualität #communities #oer #oep",
            sig = "6122acb989dbe24780c463d3026c255d0a223221e96ea29331c68b7442a3710460d6886705fe8f9b4fb0c5565f7e6ed9cb5d9b254e5b656926beb56a0d83da47",
            tags =
                arrayOf(
                    arrayOf("d", "event-1771952727237-oue85wf9r"),
                    arrayOf("h", "d6d214fe27fcc0a691dd0f04d152b7cdda7f61f96f26dc421df46af0bb51792e"),
                    arrayOf("title", "FOERBICO Tagung"),
                    arrayOf("start", "1771891200"),
                    arrayOf("end", "1771977600"),
                ),
        )

    LocalCache.justConsume(event, null, true)
    val note = LocalCache.getOrCreateNote(event.id)

    ThemeComparisonColumn {
        RenderCalendarDateSlotEvent(
            note,
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}
