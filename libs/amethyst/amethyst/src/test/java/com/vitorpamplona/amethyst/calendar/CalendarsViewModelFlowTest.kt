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
package com.vitorpamplona.amethyst.calendar

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.calendars.dal.CalendarAppointmentsFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.CalendarsViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The model's cache-backed flows: the calendar picker's list, and the membership filter whose
 * "not loaded yet" answer used to be an empty set — which reads as "match nothing" and blanked
 * every lens until the kind-31924 arrived.
 *
 * Real dispatchers throughout, no virtual time. The flows seed on [Dispatchers.Default] (the scan
 * walks the whole cache and must stay off the UI thread), so their emissions land on real threads;
 * a test-scheduler Main would sit there un-driven while the test blocked waiting for them, and
 * every await would expire on the virtual clock first. Nothing here is driven by delays, so
 * virtual time buys nothing.
 *
 * Main is [Dispatchers.Unconfined] rather than [Dispatchers.Default] so that cancelling the
 * model's scope finishes synchronously on the cancelling thread. Cancellation is otherwise
 * asynchronous, and a continuation that resumed after `resetMain()` threw "Main dispatcher had
 * failed to initialize" onto a background thread — which `runTest` then reported against whatever
 * unrelated test started next.
 *
 * `LocalCache` is a process-wide object and JUnit's method order is hash-based, so every method
 * uses its own author key and asserts only over that key's events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarsViewModelFlowTest {
    // viewModelScope is Dispatchers.Main, and every flow is a WhileSubscribed stateIn, so nothing
    // runs until something subscribes.
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun calendar(
        id: String,
        pubKey: String,
        dTag: String?,
        title: String,
        members: List<Address> = emptyList(),
    ) = CalendarCollectionEvent(
        id = id,
        pubKey = pubKey,
        createdAt = 1_700_000_000L,
        tags =
            (
                listOfNotNull(dTag?.let { arrayOf("d", it) }, arrayOf("title", title)) +
                    members.map { arrayOf("a", it.toValue()) }
            ).toTypedArray(),
        content = "",
        sig = "sig",
    )

    private fun appointment(
        id: String,
        pubKey: String,
        dTag: String,
    ) = CalendarTimeSlotEvent(
        id = id,
        pubKey = pubKey,
        createdAt = 1_700_000_000L,
        tags = arrayOf(arrayOf("d", dTag), arrayOf("title", "Standup"), arrayOf("start", "1800000000")),
        content = "",
        sig = "sig",
    )

    /**
     * Consumes [calendars] and hands back the notes they landed in, so the caller can hold them
     * for the length of the test: `LocalCache.addressables` is a weak cache, and with nothing
     * holding a reference a calendar can be collected out from under the assertions.
     *
     * The notes are taken *before* consuming: fetching them afterwards leaves the window between
     * the consume and the fetch open, and a GC there drops the calendar before it is ever held.
     */
    private fun consume(vararg calendars: CalendarCollectionEvent): List<AddressableNote> {
        val held = calendars.map { LocalCache.getOrCreateAddressableNote(it.address()) }
        calendars.forEach { LocalCache.justConsumeMyOwnEvent(it) }
        return held
    }

    /**
     * Runs [block] against a model bound to [pubKey], on real threads, and tears everything down.
     *
     * The model comes out of a real [ViewModelStore] so that clearing the store cancels its
     * `viewModelScope` — the same thing the back stack does when the screen goes. Leaving it
     * running outlived `resetMain()`, and the collectors then threw "Main dispatcher had failed
     * to initialize" onto a background thread, which `runTest` reports against whichever test
     * happens to start next.
     */
    private fun withModel(
        pubKey: String,
        block: suspend (CalendarsViewModel) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val store = ViewModelStore()
        var modelScope: Job? = null
        try {
            val feed = FeedContentState(CalendarAppointmentsFeedFilter(seeEverythingAccount()), scope, LocalCache)
            val model = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[CalendarsViewModel::class.java]
            modelScope = model.viewModelScope.coroutineContext.job
            model.init(pubKey, feed)
            block(model)
        } finally {
            store.clear()
            scope.cancel()
            // Clearing only requests cancellation. The flowOn producers on Dispatchers.Default can
            // still be finishing and hand their completion back to the Main collectors; wait for
            // them here, while setMain is still in force, not after tearDown's resetMain.
            withTimeout(AWAIT_MS) { modelScope?.join() }
        }
    }

    /** Subscribes until [predicate] holds, or fails. Subscribing is what starts the flow. */
    private suspend fun <T> StateFlow<T>.await(
        what: String,
        predicate: (T) -> Boolean,
    ): T =
        withTimeoutOrNull(AWAIT_MS) { first(predicate) }
            ?: throw AssertionError("$what: gave up, last value was $value")

    /** Subscribes for a window and asserts nothing matching [predicate] ever shows up. */
    private suspend fun <T> StateFlow<T>.never(
        what: String,
        predicate: (T) -> Boolean,
    ) {
        val seen = withTimeoutOrNull(SETTLE_MS) { first(predicate) }
        if (seen != null) throw AssertionError("$what: saw $seen")
    }

    @Test
    fun theCalendarPickerListsThisAccountsCalendars() {
        val mine = "f1".repeat(32)
        val someoneElse = "f2".repeat(32)
        val held =
            consume(
                calendar("f3".repeat(32), mine, "work", "Work"),
                calendar("f4".repeat(32), mine, "gigs", "Also mine"),
                calendar("f5".repeat(32), someoneElse, "theirs", "Not mine"),
            )

        withModel(mine) { model ->
            val listed = model.ownCalendars.await("the picker never loaded") { it.size >= 2 }

            // Sorted by title, case-insensitive, and the other author's calendar is not here.
            assertEquals(listOf("Also mine", "Work"), listed.map { it.title() })
        }

        assertEquals("the calendars must stay reachable for the whole test", 3, held.size)
    }

    @Test
    fun nothingIsFilteredUntilTheUserPicksACalendar() =
        withModel("e1".repeat(32)) { model ->
            model.filterAddresses.never("\"All\" must not narrow anything") { it != null }
        }

    @Test
    fun aCalendarThatHasNotLoadedYetDoesNotFilterEverythingOut() =
        withModel("e2".repeat(32)) { model ->
            model.selectCalendar("a-calendar-that-is-not-in-the-cache")

            // The regression: this used to answer with an empty set, which `applyCalendarFilter`
            // reads as "match nothing" — every lens went blank.
            model.filterAddresses.never("an unresolved calendar must not blank the screen") { it != null }
        }

    @Test
    fun pickingACalendarResolvesItsMembers() {
        val mine = "e3".repeat(32)
        val member = Address(CalendarTimeSlotEvent.KIND, mine, "standup")
        LocalCache.justConsumeMyOwnEvent(appointment("e4".repeat(32), mine, "standup"))
        val held = consume(calendar("e5".repeat(32), mine, "work", "Work", listOf(member)))

        withModel(mine) { model ->
            model.selectCalendar("work")

            assertEquals(setOf(member), model.filterAddresses.await("never resolved") { it != null })
        }

        assertEquals(1, held.size)
    }

    @Test
    fun pickingAnEmptyCalendarDoesFilterEverythingOut() {
        val mine = "e6".repeat(32)
        val held = consume(calendar("e7".repeat(32), mine, "empty", "Empty"))

        withModel(mine) { model ->
            model.selectCalendar("empty")

            // A calendar that really lists nothing is the one case where an empty set is right.
            assertEquals(emptySet<Address>(), model.filterAddresses.await("never resolved") { it != null })
        }

        assertEquals(1, held.size)
    }

    @Test
    fun aCalendarWithNoDTagCanStillBeFilteredBy() {
        val mine = "e8".repeat(32)
        val member = Address(CalendarTimeSlotEvent.KIND, mine, "standup")
        // Addressed as `31924:<pubkey>:` — legal, and the picker offers it, so picking it has to
        // narrow the feed. Resolving through a `#d: [""]` filter could not: the event carries no
        // literal d tag for the matcher to find, which is why this resolves off the picker's list.
        val held = consume(calendar("e9".repeat(32), mine, null, "Untagged", listOf(member)))

        withModel(mine) { model ->
            model.selectCalendar("")

            assertEquals(setOf(member), model.filterAddresses.await("never resolved") { it != null })
        }

        assertEquals(1, held.size)
    }

    companion object {
        /** Real milliseconds — every await crosses [Dispatchers.Default]. */
        private const val AWAIT_MS = 5_000L

        /** How long "it never happens" waits before believing it. */
        private const val SETTLE_MS = 500L
    }
}
