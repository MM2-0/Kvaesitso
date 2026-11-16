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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.FollowsGoing
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.MonthGridBarSegment
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.computeFollowsGoing
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.computeMonthGridBars
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.groupByDayKeyExpanded
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavFilter
import com.vitorpamplona.amethyst.commons.nip52Calendar.ui.CalendarsViewMode
import com.vitorpamplona.amethyst.commons.relayClient.calendars.CalendarAppointmentKinds
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the calendar screen is *looking at*: which lens is open, which calendar the feed is
 * scoped to, where each lens is parked, and the note lists the lenses draw.
 *
 * Scoped to the screen's `NavBackStackEntry`, so it is built the first time the user opens
 * Calendars and cleared when that entry leaves the back stack — not before. That is the whole
 * point of it being here: this state has to outlive the composition (opening an appointment tears
 * it down and rebuilds it on the way back, and switching lenses disposes the branch the previous
 * lens lived in) without outliving the screen. `rememberSaveable` covers the first case at best
 * and never the second; a process-scoped store covers both but then hangs onto a month the user
 * looked at an hour ago, in another account.
 *
 * Two lifetimes ride along for free, and both are what you'd want: the bottom bar's
 * `popUpTo(Home) { saveState = true }` saves this entry rather than clearing it, so leaving the
 * tab and coming back keeps the lens; and the ViewModel store hangs off the account-scoped owner
 * ([com.vitorpamplona.amethyst.commons.ui.app.AccountScopedViewModelStore]), so switching accounts
 * drops it with everything else that belongs to the old account.
 *
 * Not restored after process death — the defaults below are computed from *today*, which is where
 * a cold start should open anyway.
 */
@Stable
class CalendarsViewModel : ViewModel() {
    /** The day this screen was first opened. Only the defaults below are anchored to it. */
    private val openedOn: SearchDate = LocalClock.today()

    /**
     * Read live, not captured: this entry can outlive midnight, and a captured value left the
     * month grid and the week strip highlighting yesterday while the "Today" buttons (which call
     * [LocalClock.today] themselves) jumped somewhere else.
     */
    val today: SearchDate get() = LocalClock.today()

    var viewMode by mutableStateOf(CalendarsViewMode.FEED)

    // Month lens. The month is rebuilt on read from two ints so the pieces stay primitive.
    var visibleYear by mutableIntStateOf(openedOn.year)
    var visibleMonthValue by mutableIntStateOf(openedOn.month)
    var selectedDayKey by mutableStateOf<Long?>(null)

    // Week lens. Epoch-day, so the arithmetic stays in calendar days and stays DST-safe.
    var weekStartEpochDay by mutableLongStateOf(startOfWeek(openedOn).daysFromEpoch())
    var selectedDayIndex by mutableIntStateOf(0)

    // Day lens.
    var visibleEpochDay by mutableLongStateOf(openedOn.daysFromEpoch())

    /**
     * One scroll position per lens, held here for the same reason as everything above. Every other
     * feed in the app parks its offset in the process-scoped store behind `rememberForeverLazyListState`;
     * these live with the rest of this screen's state instead, so they are cleared on the same
     * event and there is one answer to "where did the calendar screen leave off".
     */
    val feedListState = LazyListState()
    val monthListState = LazyListState()
    val weekListState = LazyListState()
    val dayListState = LazyListState()

    /**
     * What [init] binds. A flow rather than `lateinit` so the derived flows below can be declared
     * as plain properties and simply wait for it: nothing downstream has to know whether the
     * screen has composed yet.
     */
    private data class Inputs(
        val myPubKey: HexKey,
        val feed: FeedContentState,
    )

    private val inputs = MutableStateFlow<Inputs?>(null)

    /** Idempotent — the screen calls it on every composition. */
    fun init(
        myPubKey: HexKey,
        feedState: FeedContentState,
    ) {
        val bound = Inputs(myPubKey, feedState)
        if (inputs.value != bound) inputs.value = bound
    }

    // ------------------------------------------------------------------------------------------
    // The membership filter
    // ------------------------------------------------------------------------------------------

    private val _filterDTag = MutableStateFlow<String?>(null)

    /**
     * d-tag of the kind-31924 calendar the feed is scoped to, or null for "All". Deliberately not
     * persisted anywhere: a filter that survived a relaunch would surprise a user who set it once
     * and forgot.
     */
    val filterDTag = _filterDTag.asStateFlow()

    fun selectCalendar(dTag: String?) {
        _filterDTag.value = dTag
    }

    /**
     * The calendars this account owns, for the top-bar picker.
     *
     * [LocalCache.observeEvents] takes the Nostr filter to the cache's own index, so this wakes up
     * for kind-31924s by this author and nothing else. The previous version collected
     * `LocalCache.live.newEventBundles` — *every* event the app ingests — and answered each batch
     * by rescanning the whole addressable cache and re-sorting, to render a chip label that
     * changes about once a week.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val ownCalendars: StateFlow<List<CalendarCollectionEvent>> =
        inputs
            .filterNotNull()
            .flatMapLatest { (myPubKey, _) ->
                LocalCache
                    .observeEvents<CalendarCollectionEvent>(
                        Filter(kinds = listOf(CalendarCollectionEvent.KIND), authors = listOf(myPubKey)),
                    ).map { calendars -> calendars.sortedBy { it.title()?.lowercase() ?: "" } }
            }
            // The seed scan inside observeEvents walks the whole notes cache (LocalCache.filter
            // does that for every query, whatever the kinds), so it must not run on the UI thread.
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * The selected calendar's member addresses, or null when nothing is narrowing the feed.
     *
     * Resolved against [ownCalendars] — the picker's own list — rather than a second cache
     * observer filtered on `#d`. Two reasons: it is one observer instead of two for the same
     * data, and a `#d` filter cannot match a calendar published WITHOUT a d tag. Such a calendar
     * is addressed as `31924:<pubkey>:` and the picker happily offers it (`dTag()` is ""), so
     * filtering by it silently resolved to nothing and the chip sat there claiming a filter that
     * wasn't applied. A string compare has no such blind spot.
     *
     * Null covers both "All is selected" and "the calendar hasn't loaded yet", and the difference
     * matters: the old code answered the second case with an EMPTY SET, which reads as "match
     * nothing" to [applyCalendarFilter] and blanked every lens until the kind-31924 arrived. An
     * empty set now only ever means a calendar that genuinely lists no appointments.
     */
    val filterAddresses: StateFlow<Set<Address>?> =
        combine(_filterDTag, ownCalendars) { dTag, calendars ->
            if (dTag == null) {
                null
            } else {
                calendars
                    .firstOrNull { it.dTag() == dTag }
                    ?.calendarEventAddresses()
                    ?.toSet()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // ------------------------------------------------------------------------------------------
    // What the lenses draw
    // ------------------------------------------------------------------------------------------

    /**
     * The appointments on screen: the feed, narrowed by the membership filter. Every lens used to
     * assemble this for itself, which is four copies of the same collect-and-filter and four
     * chances for them to disagree.
     *
     * An empty list here means the feed is empty — [FeedState.Empty] is what
     * [FeedContentState.updateFeed] emits when the rebuilt list has nothing in it, so it is the
     * truth and not something to paper over.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<Note>> =
        combine(
            inputs.filterNotNull().flatMapLatest { (_, feed) ->
                feed.feedContent.flatMapLatest { state ->
                    if (state is FeedState.Loaded) state.feed.map { it.list } else flowOf(emptyList())
                }
            },
            filterAddresses,
        ) { feedNotes, addresses ->
            feedNotes.applyCalendarFilter(addresses)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Appointments bucketed by local day, multi-day ones repeated on every day they cover. Shared
     * by the month grid, the week strip and the day agenda — each of them used to derive it from
     * the same list and throw it away on every lens switch.
     */
    val eventsByDay: StateFlow<Map<Long, List<Note>>> =
        notes
            .map { groupByDayKeyExpanded(it) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** Lane assignments for the month grid's bars. */
    val monthBars: StateFlow<Map<Long, List<MonthGridBarSegment>>> =
        notes
            .map { computeMonthGridBars(it) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** The feed lens's upcoming/past split. */
    val upcomingPast: StateFlow<UpcomingPastSplit> =
        notes
            .map { partitionUpcomingPast(it) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                UpcomingPastSplit(emptyList(), emptyList()),
            )

    // ------------------------------------------------------------------------------------------
    // Where the people in the selected list are going
    // ------------------------------------------------------------------------------------------

    /**
     * What decides whose RSVPs count: the screen's top-nav list and the account's mute state,
     * handed over as the account's own flows so a list switch or a new mute re-runs the fold.
     */
    private data class AttendeeInputs(
        val lists: StateFlow<IFeedTopNavFilter>,
        val hidden: StateFlow<LiveHiddenUsers>,
    )

    private val attendeeInputs = MutableStateFlow<AttendeeInputs?>(null)

    /**
     * Every kind-31925 note seen while this screen lives, held strongly.
     *
     * `LocalCache.addressables` keeps its values by weak reference, and nothing else in the app
     * references an RSVP by someone else: the calendar feed lists appointments, not answers. So
     * the RSVPs the screen's subscription pulls in while the user is on another lens could be
     * collected before they ever open "Friends going" — and the subscription's EOSE cursor has
     * already moved past them, so they would not be asked for again. Pinning them here, from the
     * moment the screen binds, is what lets the lens be opened later and still see them. They
     * are released with the screen's back-stack entry. Kind-31925 events are a few hundred bytes.
     */
    private val heldRsvps = ConcurrentSet<AddressableNote>()

    private var rsvpPinner: Job? = null

    /** Idempotent — the screen calls it on every composition. */
    fun bindAttendeeFilter(
        lists: StateFlow<IFeedTopNavFilter>,
        hidden: StateFlow<LiveHiddenUsers>,
    ) {
        val bound = AttendeeInputs(lists, hidden)
        if (attendeeInputs.value != bound) attendeeInputs.value = bound

        if (rsvpPinner == null) {
            rsvpPinner =
                viewModelScope.launch(Dispatchers.IO) {
                    LocalCache.addressables.filterIntoSet(CalendarRSVPEvent.KIND).forEach { heldRsvps.add(it) }
                    LocalCache
                        .observeNewEvents<CalendarRSVPEvent>(Filter(kinds = listOf(CalendarRSVPEvent.KIND)))
                        .collect { rsvp -> LocalCache.getAddressableNoteIfExists(rsvp.address())?.let(heldRsvps::add) }
                }
        }
    }

    /**
     * The upcoming appointments the people in the selected list said they're going to, soonest
     * first, plus the appointments they RSVP'd to that are not in the cache yet (for the lens to
     * fetch). Null until the first fold lands, so the lens can tell "loading" from "nobody".
     *
     * Wakes on one cache observer covering both kinds it reads: RSVPs, and appointments, because
     * an RSVP usually lands before the appointment it answers — the host is often someone the
     * viewer does not follow — and the row can only be placed once the appointment's date is
     * known. The observer is only a wake-up signal; each fold re-reads the RSVPs from the
     * addressable cache's kind index, which holds exactly the latest version of each one.
     *
     * That replaced `observeEvents`, whose seed walks every regular note in the cache (its
     * `filter()` scans `notes` whatever the kinds) and which then copies its whole list and id
     * set on every insert — quadratic on the burst of RSVPs a list switch pulls in, each copy
     * followed by a full re-fold. Here the cheap part (combining the signal with the list and
     * mute state) runs per event, and [conflate] lets the fold skip every signal that arrived
     * while the previous fold was running.
     *
     * `now` is read on each fold rather than ticking on a timer: an event that ends while the lens
     * is open lingers until the next RSVP or appointment arrives, which is harmless.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val followsGoing: StateFlow<FollowsGoing?> =
        attendeeInputs
            .filterNotNull()
            .flatMapLatest { (lists, hidden) ->
                combine(
                    LocalCache
                        .observeNewEvents<Event>(Filter(kinds = FOLLOWS_GOING_KINDS))
                        .conflate()
                        .map { }
                        .onStart { emit(Unit) },
                    lists,
                    hidden,
                ) { _, list, hiddenUsers ->
                    FilterByListParams.create(list, hiddenUsers)
                }.conflate()
                    .map(::foldFollowsGoing)
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun foldFollowsGoing(params: FilterByListParams): FollowsGoing {
        // The index scan sees an RSVP the pinner has not reached yet; the pin set holds the rest.
        val rsvpNotes = LocalCache.addressables.filterIntoSet(CalendarRSVPEvent.KIND)
        rsvpNotes.forEach { heldRsvps.add(it) }
        val rsvps = rsvpNotes.mapNotNull { it.event as? CalendarRSVPEvent }

        // Relays only matter to a relay-scoped list, so the others skip the lookup.
        val relaysOf: (CalendarRSVPEvent) -> List<NormalizedRelayUrl> =
            if (params.followLists is RelayTopNavFilter) {
                val byId = rsvpNotes.associateBy({ it.event?.id }, { it.relays })
                ({ byId[it.id] ?: emptyList() })
            } else {
                ({ emptyList() })
            }

        return computeFollowsGoing(
            rsvps = rsvps,
            isAttendee = { params.match(it, relaysOf(it)) },
            appointmentFor = LocalCache::getOrCreateAddressableNote,
            nowSeconds = TimeUtils.now(),
            isAppointmentVisible = { params.isHiddenList || params.isNotHidden(it.pubKeyHex) },
        )
    }

    val followsGoingListState = LazyListState()

    // ------------------------------------------------------------------------------------------
    // Paging
    // ------------------------------------------------------------------------------------------

    /** The visible month, as its first day. */
    var visibleMonth: SearchDate
        get() = SearchDate(visibleYear, visibleMonthValue, 1)
        set(value) {
            visibleYear = value.year
            visibleMonthValue = value.month
        }

    val weekStart: SearchDate get() = SearchDate.civilFromDays(weekStartEpochDay)

    val visibleDate: SearchDate get() = SearchDate.civilFromDays(visibleEpochDay)

    /** The day the week strip has selected — [weekStart] plus the strip's index. */
    val selectedWeekDate: SearchDate get() = weekStart.plusDays(selectedDayIndex)

    fun showMonth(month: SearchDate) {
        visibleMonth = month
        selectedDayKey = null
    }

    fun showWeekOf(date: SearchDate) {
        weekStartEpochDay = startOfWeek(date).daysFromEpoch()
        selectedDayIndex = 0
    }

    fun shiftWeeks(delta: Long) {
        weekStartEpochDay += delta * 7
        selectedDayIndex = 0
    }

    fun shiftDays(delta: Long) {
        visibleEpochDay += delta
    }

    companion object {
        /**
         * How long the cache observers and the feed collection stay up after the screen stops
         * being watched. Long enough to ride out a configuration change without re-seeding every
         * list, short enough that a backgrounded screen stops holding observers open.
         */
        private const val STOP_TIMEOUT_MS = 5_000L

        private val FOLLOWS_GOING_KINDS = CalendarAppointmentKinds + CalendarRSVPEvent.KIND
    }
}

/**
 * Returns the Sunday on or before [date]. DST-safe because [SearchDate] arithmetic ignores zones;
 * [SearchDate.dayOfWeek] already counts Sunday as 0.
 */
fun startOfWeek(date: SearchDate): SearchDate = date.plusDays(-date.dayOfWeek())
