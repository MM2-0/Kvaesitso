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

import com.vitorpamplona.amethyst.commons.calendars.dal.CalendarAppointmentsFeedFilter
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A 31923 appointment lands in `LocalCache` as TWO objects: the canonical `AddressableNote` under
 * its address, and a version note under the event id. Only the first is handed to feeds when the
 * event arrives, and only the first survives the cache sweep that runs on every app switch — so a
 * feed must query the addressables, which is what [com.vitorpamplona.amethyst.commons.calendars.dal.CalendarAppointmentsFeedFilter]
 * now does. Scanning `LocalCache.notes` instead gave the calendar views a second identity for the
 * same appointment and made them flicker events in and out around every trip to the background.
 *
 * `LocalCache` is a process-wide object and JUnit's method order is hash-based, so every method
 * here uses its own author key and asserts only over that key's address range.
 */
class CalendarAppointmentCacheIdentityTest {
    private fun appointment(
        id: String,
        pubKey: String,
        dTag: String,
    ) = CalendarTimeSlotEvent(
        id = id,
        pubKey = pubKey,
        createdAt = 1_700_000_000L,
        tags =
            arrayOf(
                arrayOf("d", dTag),
                arrayOf("title", "Standup"),
                arrayOf("start", "1800000000"),
            ),
        content = "",
        sig = "sig",
    )

    @Test
    fun theQueryTheFeedUsesReturnsTheSameObjectTheLiveUpdateDelivers() {
        val pubKey = "a1".repeat(32)
        val event = appointment("a3".repeat(32), pubKey, "standup")
        LocalCache.justConsumeMyOwnEvent(event)

        // What LocalCache hands a feed on arrival (refreshNewNoteObservers(replaceableNote)).
        val delivered = LocalCache.getAddressableNoteIfExists(event.address())
        assertNotNull("the appointment did not reach the addressable cache", delivered)

        // What the feed's full rebuild now scans.
        val scanned =
            LocalCache.addressables.filterIntoSet(CalendarTimeSlotEvent.KIND, pubKey) { _, it ->
                it.event is CalendarTimeSlotEvent
            }
        assertEquals(1, scanned.size)
        assertSame("the rebuild must return the object the live path delivers", delivered, scanned.first())
    }

    @Test
    fun theCanonicalNoteIsKeyedByItsAddress_notByTheEventId() {
        val pubKey = "b1".repeat(32)
        val event = appointment("b3".repeat(32), pubKey, "retro")
        LocalCache.justConsumeMyOwnEvent(event)

        val canonical = LocalCache.getAddressableNoteIfExists(event.address())!!

        // The version note under `event.id` is a different object with a different idHex. Both
        // carry the same appointment, and a feed that mixed the two showed it twice — `distinctBy`
        // and the LazyColumn keys both go by idHex. (The version note is not asserted on here: it
        // is weakly held and moves all its references to the canonical note on arrival, so it can
        // be collected at any moment — which is the other half of why a feed must not rely on it.)
        assertEquals(event.address().toValue(), canonical.idHex)
        assertNotEquals("the two identities for one appointment must not be confused", event.id, canonical.idHex)
    }

    @Test
    fun theAppointmentOutlivesTheVersionNoteBeingSwept() {
        val pubKey = "c1".repeat(32)
        val event = appointment("c3".repeat(32), pubKey, "1-1")
        LocalCache.justConsumeMyOwnEvent(event)

        // Held for the length of the test on purpose: `addressables` is a weak cache too, so
        // without a reference the appointment can be collected out from under the assertions
        // under GC pressure (it is exactly the feed's own list that holds the working set alive
        // in the app). The difference the fix rests on is not weak-vs-strong, it is that this
        // note is the one everything links to while the version note has its references moved
        // away on arrival and is swept on every app switch.
        @Suppress("UNUSED_VARIABLE")
        val keepAlive = LocalCache.getAddressableNoteIfExists(event.address())

        // CachePruner.cleanMemory()/prunePastVersionsOfReplaceables() drop version notes on every
        // app switch (MemoryTrimmingService tier 1); `notes` holds them weakly anyway.
        LocalCache.notes.remove(event.id)

        assertNull("the version note is gone, as it is after a trip to the background", LocalCache.notes.get(event.id))
        assertEquals(
            "the appointment must still be there for the feed to find",
            event.id,
            LocalCache
                .addressables
                .filterIntoSet(CalendarTimeSlotEvent.KIND, pubKey) { _, it -> it.event is CalendarTimeSlotEvent }
                .firstOrNull()
                ?.event
                ?.id,
        )
    }

    @Test
    fun theFeedReturnsTheCanonicalNote_notTheVersionNote() {
        val pubKey = "d1".repeat(32)
        val event = appointment("d3".repeat(32), pubKey, "planning")
        LocalCache.justConsumeMyOwnEvent(event)

        // Taken before the query, and held: see the note in the sweep test above.
        val delivered = LocalCache.getAddressableNoteIfExists(event.address())

        val mine =
            CalendarAppointmentsFeedFilter(seeEverythingAccount())
                .feed()
                .filter { it.event?.pubKey == pubKey }

        assertEquals("the appointment must be in the feed exactly once", 1, mine.size)
        assertSame(
            "the feed must hand back the same object live updates deliver, or the two paths " +
                "fight over which copy of the appointment is on screen",
            delivered,
            mine.first(),
        )
    }

    @Test
    fun theFeedStillFindsTheAppointmentAfterTheVersionNoteIsSwept() {
        val pubKey = "e1".repeat(32)
        val event = appointment("e3".repeat(32), pubKey, "review")
        LocalCache.justConsumeMyOwnEvent(event)

        // Held for the length of the test: see the note in the sweep test above.
        @Suppress("UNUSED_VARIABLE")
        val keepAlive = LocalCache.getAddressableNoteIfExists(event.address())

        // Every app switch runs the tier-1 sweep; the version note does not survive it.
        LocalCache.notes.remove(event.id)

        val mine =
            CalendarAppointmentsFeedFilter(seeEverythingAccount())
                .feed()
                .filter { it.event?.pubKey == pubKey }

        assertEquals("the appointment vanished from the feed after the sweep", 1, mine.size)
    }
}
