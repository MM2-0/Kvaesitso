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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.IcsExport
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.commons.model.toNostrUriOrFallback
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.calendar_add_to_calendar_action
import com.vitorpamplona.amethyst.commons.resources.calendar_add_to_phone_calendar
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_count
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_empty_members
import com.vitorpamplona.amethyst.commons.resources.calendar_event_in_calendars
import com.vitorpamplona.amethyst.commons.resources.calendar_event_in_no_calendars
import com.vitorpamplona.amethyst.commons.resources.calendar_event_loading
import com.vitorpamplona.amethyst.commons.resources.calendar_export_event
import com.vitorpamplona.amethyst.commons.resources.calendar_export_share_title
import com.vitorpamplona.amethyst.commons.resources.calendar_open_in_maps
import com.vitorpamplona.amethyst.commons.resources.calendar_open_link
import com.vitorpamplona.amethyst.commons.resources.calendar_participants_section
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_going_prefixed
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_maybe_prefixed
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_none
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_not_going_prefixed
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_section
import com.vitorpamplona.amethyst.commons.resources.calendar_share_nostr
import com.vitorpamplona.amethyst.commons.resources.calendar_share_nostr_title
import com.vitorpamplona.amethyst.commons.resources.calendar_untitled
import com.vitorpamplona.amethyst.commons.resources.edit_calendar_event
import com.vitorpamplona.amethyst.commons.resources.route_calendar_event_detail
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.rememberFileSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.note.types.CalendarRsvpRow
import com.vitorpamplona.amethyst.commons.ui.note.types.calendarTimeZoneLine
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.datasource.CalendarsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatCalendarRange
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.rememberPhoneCalendar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.rememberRelativeTimeLabel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Dedicated detail screen for a NIP-52 calendar appointment (kind 31922 or 31923). Renders the
 * full event metadata along with three related sections that the inline note render can't
 * easily fit:
 *   - participants (from the event's `p` tags)
 *   - RSVPs (kind 31925 events that a-tag this event)
 *   - calendars this event belongs to (kind 31924 events whose member list includes this event)
 *
 * The "related" sections are snapshotted at composition for simplicity; opening the screen
 * triggers the appointments subscription so freshly-arrived related events are visible on the
 * next entry. A future revision could subscribe to LocalCache.live for true reactivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventDetailScreen(
    kind: Int,
    pubKeyHex: String,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    CalendarsFilterAssemblerSubscription(accountViewModel)

    val targetAddress = remember(kind, pubKeyHex, dTag) { Address(kind, pubKeyHex, dTag) }
    val targetNote = remember(targetAddress) { LocalCache.getOrCreateAddressableNote(targetAddress) }
    // [observeNote] issues a per-event relay subscription on top of the LocalCache flow. This
    // is the prefetch path for deep links (notification tap, `nostr:naddr…` from another app):
    // landing on the screen without the event cached now triggers a targeted relay fetch instead
    // of waiting for the broader calendars feed to happen to include it.
    val noteState by observeNote(targetNote, accountViewModel)
    val event = noteState.note.event

    val isOwnEvent = event?.pubKey == accountViewModel.userProfile().pubkeyHex

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringRes(Res.string.route_calendar_event_detail),
                    )
                },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = {
                    val phoneCalendar = rememberPhoneCalendar()
                    val fileSharer = rememberFileSharer()
                    val textSharer = rememberTextSharer()
                    val icsChooserTitle = stringRes(Res.string.calendar_export_share_title)
                    // Two share modes:
                    //  - .ics for non-nostr calendar apps (Google Calendar / iOS / Outlook)
                    //  - nostr:naddr… link for sharing inside the nostr ecosystem (in DMs,
                    //    in posts, in other clients). A single button with a chooser would be
                    //    cleaner, but two icons keep both actions one tap away.
                    if (event != null) {
                        // Direct "Add to phone calendar" — opens the system event composer with
                        // every field pre-filled. Falls back to the .ics share path if the device
                        // has no calendar app registered for ACTION_INSERT (rare; Wear OS, some
                        // GrapheneOS profiles).
                        IconButton(onClick = {
                            if (!phoneCalendar.add(event)) {
                                val ics = IcsExport.appointmentToIcs(event, targetAddress, TimeUtils.now())
                                val filename = IcsExport.appointmentFilename(event, targetAddress)
                                fileSharer.shareTextFile(filename, "text/calendar", ics, icsChooserTitle)
                            }
                        }) {
                            Icon(
                                symbol = MaterialSymbols.EventAvailable,
                                contentDescription = stringRes(Res.string.calendar_add_to_phone_calendar),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            val ics = IcsExport.appointmentToIcs(event, targetAddress, TimeUtils.now())
                            val filename = IcsExport.appointmentFilename(event, targetAddress)
                            fileSharer.shareTextFile(filename, "text/calendar", ics, icsChooserTitle)
                        }) {
                            Icon(
                                symbol = MaterialSymbols.Share,
                                contentDescription = stringRes(Res.string.calendar_export_event),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        val shareTitle = stringRes(Res.string.calendar_share_nostr_title)
                        IconButton(onClick = {
                            textSharer.share(targetAddress.toNostrUriOrFallback(null, null), null, shareTitle)
                        }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.Send,
                                contentDescription = stringRes(Res.string.calendar_share_nostr),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // The Edit affordance is only meaningful when the current account is the
                    // author — relays will reject a signed-by-stranger replacement.
                    if (isOwnEvent) {
                        IconButton(onClick = {
                            nav.nav(
                                Route.EditCalendarEvent(
                                    kind = event.kind,
                                    pubKeyHex = event.pubKey,
                                    dTag = targetAddress.dTag,
                                ),
                            )
                        }) {
                            Icon(
                                symbol = MaterialSymbols.Edit,
                                contentDescription = stringRes(Res.string.edit_calendar_event),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            when (event) {
                is CalendarTimeSlotEvent,
                is CalendarDateSlotEvent,
                ->
                    EventBody(
                        note = targetNote,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        targetAddress = targetAddress,
                    )

                is CalendarCollectionEvent ->
                    CollectionBody(
                        note = targetNote,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        targetAddress = targetAddress,
                    )

                else -> LoadingPlaceholder()
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(Res.string.calendar_event_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventBody(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    targetAddress: Address,
) {
    val view = note.appointmentView() ?: return
    val event = note.event ?: return

    val participants =
        remember(note.idHex) {
            when (val e = event) {
                is CalendarTimeSlotEvent -> e.participants()
                is CalendarDateSlotEvent -> e.participants()
                else -> emptyList()
            }
        }

    HeroImage(view.image, accountViewModel)

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        view.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        val timeFormatter = rememberTimeOfDayFormatter()
        formatCalendarRange(note, timeFormatter)?.let { range ->
            Text(
                text = range,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        (event as? CalendarTimeSlotEvent)?.let { timed ->
            calendarTimeZoneLine(timed.start(), timed.startTzId())?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val relative = rememberRelativeTimeLabel(view, note.idHex)
        relative?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        view.location?.let { LocationRow(it) }
        view.summary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Standard social actions (zap, reactions/likes, repost, reply count → thread/comments).
    // Uses the shared [ReactionsRow] so the affordances look and behave the same as every other
    // note-detail surface in the app — no calendar-specific reinvention. The row carries its
    // own internal vertical padding, so we don't add any here; an outer spacedBy compounded with
    // that padding made the row look like it had huge top/bottom margins.
    ReactionsRow(
        baseNote = note,
        showReactionDetail = true,
        addPadding = true,
        editState = null,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(8.dp))

    CalendarRsvpRow(
        eventKind = event.kind,
        eventPubKey = event.pubKey,
        eventDTag = targetAddress.dTag,
        eventId = event.id,
        accountViewModel = accountViewModel,
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (participants.isNotEmpty()) {
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        ParticipantsSection(participants, accountViewModel, nav)
        Spacer(modifier = Modifier.height(12.dp))
    }

    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))
    RsvpsSection(targetAddress, accountViewModel, nav)
    Spacer(modifier = Modifier.height(12.dp))

    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))
    InCalendarsSection(targetAddress, accountViewModel, nav)

    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * Detail body for a NIP-52 kind-31924 calendar collection. Renders the same author header /
 * social actions surface as an appointment, but the body is a list of the collection's member
 * appointments instead of the appointment metadata block. Tapping a member routes to its own
 * appointment detail.
 */
@Composable
private fun CollectionBody(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    targetAddress: Address,
) {
    val event = note.event as? CalendarCollectionEvent ?: return
    val title = remember(note.idHex) { event.title() }
    val memberAddresses = remember(note.idHex) { event.calendarEventAddresses() }

    Spacer(modifier = Modifier.height(12.dp))

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (event.content.isNotBlank()) {
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    ReactionsRow(
        baseNote = note,
        showReactionDetail = true,
        addPadding = true,
        editState = null,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))

    CollectionMembersSection(memberAddresses, targetAddress, accountViewModel, nav)

    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * Lists the appointments addressed by [memberAddresses]. Each row tries to resolve the cached
 * appointment event; if it hasn't arrived yet we fall back to a thin "(loading…)" placeholder
 * tied to the address so the user can still see what's expected. Tapping a row routes to that
 * appointment's detail screen.
 */
@Composable
private fun CollectionMembersSection(
    memberAddresses: List<Address>,
    targetAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(pluralStringRes(Res.plurals.calendar_collection_count, memberAddresses.size, memberAddresses.size))
        if (memberAddresses.isEmpty()) {
            Text(
                text = stringRes(Res.string.calendar_collection_empty_members),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        memberAddresses.forEach { address ->
            CollectionMemberRow(address, accountViewModel, nav)
        }
        if (targetAddress.dTag.isNotEmpty()) Unit // suppress unused-parameter lint
    }
}

/**
 * One row in [CollectionMembersSection]. Reads the live note for [address] so a member event
 * that arrives later in the session fills in without leaving the screen.
 */
@Composable
private fun CollectionMemberRow(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val memberNote = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
    // Issues a per-event relay subscription so missing members fill in while the user is on
    // the screen.
    val state by observeNote(memberNote, accountViewModel)
    val memberEvent = state.note.event

    val title =
        when (memberEvent) {
            is CalendarTimeSlotEvent -> memberEvent.title()
            is CalendarDateSlotEvent -> memberEvent.title()
            else -> null
        }
    val subtitle =
        when (memberEvent) {
            is CalendarTimeSlotEvent -> memberEvent.start()?.let(::formatLongDate)
            is CalendarDateSlotEvent -> memberEvent.start()
            else -> null
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.CalendarEventDetail(address)) }
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title ?: stringRes(Res.string.calendar_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroImage(
    image: String?,
    accountViewModel: AccountViewModel,
) {
    if (image.isNullOrBlank()) return
    MyAsyncImage(
        imageUrl = image,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        mainImageModifier = Modifier.fillMaxWidth().height(200.dp),
        loadedImageModifier = Modifier,
        accountViewModel = accountViewModel,
        onLoadingBackground = { Box(modifier = Modifier.fillMaxWidth().height(200.dp)) },
        onError = { Box(modifier = Modifier.fillMaxWidth().height(200.dp)) },
    )
}

@Composable
private fun LocationRow(location: String) {
    val uriHandler = LocalUriHandler.current
    // NIP-52 `location` is free-text — a place name, an address, OR a meeting URL (Zoom, Jitsi,
    // a livestream link). Treat http(s) URLs as web links so they open in the browser; otherwise
    // hand to the maps app via the geo: intent.
    val isUrl = remember(location) { isLocationUrl(location) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        val trimmed = location.trim()
                        // `geo:0,0?q=<location>` is the Android geo URI; the user's installed maps
                        // app handles it. runCatching swallows the failure when no handler is
                        // installed — we don't have anywhere useful to fall back to.
                        uriHandler.openUri(if (isUrl) trimmed else "geo:0,0?q=${encodeUriComponent(trimmed)}")
                    }
                }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = if (isUrl) MaterialSymbols.Link else MaterialSymbols.LocationOn,
            contentDescription =
                stringRes(if (isUrl) Res.string.calendar_open_link else Res.string.calendar_open_in_maps),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = location,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ParticipantsSection(
    participants: List<PTag>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringRes(Res.string.calendar_participants_section, participants.size))
        participants.forEach { p ->
            UserRow(p.pubKey, accountViewModel, nav, trailing = null)
        }
    }
}

@Composable
private fun RsvpsSection(
    targetAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Reactively re-scan when LocalCache emits new bundles. Without this, RSVPs that arrive
    // from relays while the screen is open don't show up until the user leaves and returns.
    val rsvps by rememberRsvpsFor(targetAddress)

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringRes(Res.string.calendar_rsvp_section, rsvps.size))
        if (rsvps.isEmpty()) {
            Text(
                text = stringRes(Res.string.calendar_rsvp_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        rsvps.forEach { rsvp ->
            UserRow(
                pubKey = rsvp.pubKey,
                accountViewModel = accountViewModel,
                nav = nav,
                trailing = { RsvpStatusBadge(rsvp.status()) },
            )
        }
    }
}

@Composable
private fun InCalendarsSection(
    targetAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val calendars by rememberCalendarsContaining(targetAddress)
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringRes(Res.string.calendar_event_in_calendars, calendars.size))
            Spacer(modifier = Modifier.weight(1f))
            // Affordance for adding/removing this event from the user's own calendars. Hidden
            // when the user has no own calendars — the sheet would show an empty-state in that
            // case anyway, but the IconButton would be a misleading entry point.
            IconButton(onClick = { showAddSheet = true }) {
                Icon(
                    symbol = MaterialSymbols.Add,
                    contentDescription = stringRes(Res.string.calendar_add_to_calendar_action),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (showAddSheet) {
            AddToCalendarSheet(
                targetAddress = targetAddress,
                accountViewModel = accountViewModel,
                onDismiss = { showAddSheet = false },
            )
        }
        if (calendars.isEmpty()) {
            Text(
                text = stringRes(Res.string.calendar_event_in_no_calendars),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        calendars.forEach { calendar ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            nav.nav(
                                Route.CalendarEventDetail(
                                    kind = CalendarCollectionEvent.KIND,
                                    pubKeyHex = calendar.pubKey,
                                    dTag = calendar.dTag(),
                                ),
                            )
                        }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClickableUserPicture(
                    baseUserHex = calendar.pubKey,
                    size = Size30dp,
                    accountViewModel = accountViewModel,
                )
                Text(
                    text = calendar.title() ?: stringRes(Res.string.calendar_untitled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Shared social-row layout: avatar (clickable → profile), display name, optional trailing slot
 * for things like RSVP badges. Matches the visual language of every other user-list across the
 * app.
 */
@Composable
private fun UserRow(
    pubKey: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    trailing: (@Composable () -> Unit)?,
) {
    LoadUser(baseUserHex = pubKey) { user ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClickableUserPicture(
                baseUserHex = pubKey,
                size = Size35dp,
                accountViewModel = accountViewModel,
                onClick = { nav.nav(Route.Profile(pubKey)) },
            )
            if (user != null) {
                UsernameDisplay(
                    baseUser = user,
                    weight = Modifier.weight(1f),
                    accountViewModel = accountViewModel,
                )
            } else {
                // LoadUser is still resolving — show the npub-style fallback so the row
                // doesn't visibly collapse.
                Text(
                    text = formatPubKeyShort(pubKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun RsvpStatusBadge(status: RSVPStatusTag.STATUS?) {
    val (label, color) =
        when (status) {
            RSVPStatusTag.STATUS.ACCEPTED ->
                stringRes(Res.string.calendar_rsvp_going_prefixed) to MaterialTheme.colorScheme.primary
            RSVPStatusTag.STATUS.TENTATIVE ->
                stringRes(Res.string.calendar_rsvp_maybe_prefixed) to MaterialTheme.colorScheme.tertiary
            RSVPStatusTag.STATUS.DECLINED ->
                stringRes(Res.string.calendar_rsvp_not_going_prefixed) to MaterialTheme.colorScheme.error
            null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

private fun formatPubKeyShort(pubKey: String): String = if (pubKey.length <= 16) pubKey else pubKey.take(8) + "…" + pubKey.takeLast(8)

/**
 * Whether a NIP-52 `location` value should be treated as a clickable web link (Zoom, Jitsi,
 * livestream URL) instead of a place name to look up in the maps app. Trims so `" https://… "`
 * still classifies as a URL.
 */
private fun isLocationUrl(location: String): Boolean {
    val trimmed = location.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
}

/**
 * The kind-31925 RSVPs that a-tag [targetAddress], and the kind-31924 calendars that list it.
 *
 * [LocalCache.observeEvents] registers the filter with the cache's own index, so this screen is
 * woken for those two kinds rather than for every event the app ingests — which is what the
 * previous `LocalCache.live.newEventBundles` collector did, answering each batch with a full
 * scan of the addressable cache.
 *
 * The filter narrows by KIND and the a-tag check happens below, rather than putting the a-tag in
 * the filter itself, because `FilterIndex` buckets an observer under its most selective dimension
 * — the tag — and an edited event that DROPS the tag then lands in a different bucket and never
 * wakes this observer. A calendar that removed this appointment would have gone on listing it
 * until the screen was reopened. Both kinds are low-volume, so waking on the kind and re-checking
 * here costs little and cannot go stale: the list re-emits on every matching event and the
 * snapshot reads the live event off each note.
 *
 * The seed scan inside `observeEvents` walks the whole notes cache (`LocalCache.filter` does that
 * for every query, whatever the kinds), so neither of these may run on the UI thread.
 */
@Composable
private fun rememberRsvpsFor(targetAddress: Address): State<List<CalendarRSVPEvent>> {
    val rsvps =
        remember(targetAddress) {
            LocalCache
                .observeEvents<CalendarRSVPEvent>(Filter(kinds = listOf(CalendarRSVPEvent.KIND)))
                .map { all ->
                    all
                        .filter { it.calendarEventAddress() == targetAddress }
                        .sortedByDescending { it.createdAt }
                }.flowOn(Dispatchers.IO)
        }

    return rsvps.collectAsStateWithLifecycle(emptyList())
}

@Composable
private fun rememberCalendarsContaining(targetAddress: Address): State<List<CalendarCollectionEvent>> {
    val calendars =
        remember(targetAddress) {
            LocalCache
                .observeEvents<CalendarCollectionEvent>(Filter(kinds = listOf(CalendarCollectionEvent.KIND)))
                .map { all ->
                    all
                        .filter { it.calendarEventAddresses().contains(targetAddress) }
                        .sortedByDescending { it.createdAt }
                }.flowOn(Dispatchers.IO)
        }

    return calendars.collectAsStateWithLifecycle(emptyList())
}

/** Percent-encodes everything but the unreserved characters, as Android's `Uri.encode` does. */
private fun encodeUriComponent(text: String): String {
    val sb = StringBuilder(text.length)
    for (b in text.encodeToByteArray()) {
        val c = (b.toInt() and 0xFF).toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "_-!.~'()*") {
            sb.append(c)
        } else {
            sb.append('%')
            sb.append((b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase())
        }
    }
    return sb.toString()
}
