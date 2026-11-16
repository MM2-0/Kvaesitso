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
package com.vitorpamplona.amethyst.commons.model.nip52Calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderLogStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The predicate that decides whether a reminder scan may stop (Android's periodic worker chain
 * cancels itself on it). Getting it wrong in either direction is a bug:
 * a false "could fire" keeps waking the process forever; a false "can't fire"
 * silently kills a reminder the user RSVP'd to.
 *
 * Events go in through [consumePinned], which takes each event's note from the cache *before*
 * consuming it and keeps it for the test's lifetime. `LocalCache` holds notes weakly
 * (LargeSoftCache) and the scanner finds RSVPs and their time slots by reading the cache, so a
 * GC before that read would lose them — a lost slot reads as "not fetched yet" and flips
 * `couldStillFire` to true.
 */
class CalendarReminderScannerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 2_000_000_000L

    private val pinned = mutableListOf<Note>()

    private fun consumePinned(event: Event) {
        pinned.add(LocalCache.getOrCreateNote(event))
        LocalCache.justConsume(event, null, true)
    }

    // Unique per-test-class identities so the shared LocalCache singleton
    // doesn't collide with other suites running in the same JVM.
    private val organizer = "b0b0000000000000000000000000000000000000000000000000000000000001"
    private val attendee = "a11ce00000000000000000000000000000000000000000000000000000000002"
    private val follow = "f011000000000000000000000000000000000000000000000000000000000003"

    private var eventSeq = 0

    private fun timeSlot(
        dTag: String,
        startSeconds: Long?,
    ) = CalendarTimeSlotEvent(
        id = "c0ffee%058d".format(++eventSeq),
        pubKey = organizer,
        createdAt = now - 1000,
        tags =
            if (startSeconds != null) {
                arrayOf(arrayOf("d", dTag), arrayOf("start", startSeconds.toString()))
            } else {
                arrayOf(arrayOf("d", dTag))
            },
        content = "",
        sig = "sig",
    )

    private fun rsvp(
        dTag: String,
        targetDTag: String?,
        status: String = "accepted",
        author: String = attendee,
    ) = CalendarRSVPEvent(
        id = "faced%059d".format(++eventSeq),
        pubKey = author,
        createdAt = now - 900,
        tags =
            buildList {
                add(arrayOf("d", dTag))
                add(arrayOf("status", status))
                if (targetDTag != null) {
                    add(arrayOf("a", "${CalendarTimeSlotEvent.KIND}:$organizer:$targetDTag"))
                }
            }.toTypedArray(),
        content = "",
        sig = "sig",
    )

    @Test
    fun acceptedRsvpsInCache_returnsAcceptedAndSkipsDeclined() {
        val accepted = rsvp("cand-accepted", "cand-target-1")
        val declined = rsvp("cand-declined", "cand-target-2", status = "declined")
        consumePinned(accepted)
        consumePinned(declined)

        val found = CalendarReminderScanner.acceptedRsvpsInCache(listOf(attendee))
        assertTrue("accepted RSVP must be found", found.any { it.dTag() == "cand-accepted" })
        assertFalse("declined RSVP must be skipped", found.any { it.dTag() == "cand-declined" })
    }

    @Test
    fun futureTarget_couldStillFire() {
        val slot = timeSlot("cand-future", startSeconds = now + 3600)
        val r = rsvp("cand-rsvp-future", "cand-future")
        consumePinned(slot)
        consumePinned(r)

        assertTrue(CalendarReminderScanner.couldStillFire(listOf(r), now))
    }

    @Test
    fun pastTarget_cannotFireAnymore() {
        val slot = timeSlot("cand-past", startSeconds = now - 3600)
        val r = rsvp("cand-rsvp-past", "cand-past")
        consumePinned(slot)
        consumePinned(r)

        assertFalse(CalendarReminderScanner.couldStillFire(listOf(r), now))
    }

    @Test
    fun unresolvedTarget_keepsTheChainAlive() {
        // The RSVP points at an event the cache hasn't fetched yet: the start
        // is unknown, so the worker must NOT cancel its chain.
        val r = rsvp("cand-rsvp-unresolved", "cand-never-fetched")
        consumePinned(r)

        assertTrue(CalendarReminderScanner.couldStillFire(listOf(r), now))
    }

    @Test
    fun targetWithoutStart_keepsTheChainAlive() {
        // Target resolved but carries no start tag: still unknown, keep alive.
        val slot = timeSlot("cand-no-start", startSeconds = null)
        val r = rsvp("cand-rsvp-no-start", "cand-no-start")
        consumePinned(slot)
        consumePinned(r)

        assertTrue(CalendarReminderScanner.couldStillFire(listOf(r), now))
    }

    @Test
    fun rsvpWithoutTargetAddress_doesNotKeepTheChainAlive() {
        val r = rsvp("cand-rsvp-no-a-tag", targetDTag = null)
        consumePinned(r)

        assertFalse(CalendarReminderScanner.couldStillFire(listOf(r), now))
    }

    @Test
    fun emptyCache_doesNotKeepTheChainAlive() {
        assertFalse(CalendarReminderScanner.couldStillFire(emptyList(), now))
    }

    @Test
    fun scan_notifiesOncePerStartInsideTheLeadWindow() =
        runTest {
            val soon = timeSlot("scan-soon", startSeconds = now + 10 * 60)
            val later = timeSlot("scan-later", startSeconds = now + 60 * 60)
            consumePinned(soon)
            consumePinned(later)
            consumePinned(rsvp("scan-rsvp-soon", "scan-soon"))
            consumePinned(rsvp("scan-rsvp-later", "scan-later"))

            val log = CalendarReminderLogStore(dataStore())
            val fired = mutableListOf<DueCalendarReminder>()

            CalendarReminderScanner.scan(listOf(attendee), leadMinutes = 15, log = log, now = now) { fired.add(it) }
            val first = fired.filter { it.eventId == soon.id || it.eventId == later.id }
            assertEquals(listOf(soon.id), first.map { it.eventId })
            assertEquals(10, first.single().minutesAway)
            assertTrue(first.single().deepLink.startsWith("nostr:naddr1"))

            fired.clear()
            CalendarReminderScanner.scan(listOf(attendee), leadMinutes = 15, log = log, now = now + 60) { fired.add(it) }
            assertTrue("a reminder fires once per start", fired.none { it.eventId == soon.id })
        }

    @Test
    fun aFollowsRsvpIsNotTheUsersReminder() =
        runTest {
            // The home feed loads the RSVPs of everyone the user follows.
            val slot = timeSlot("follow-going", startSeconds = now + 5 * 60)
            consumePinned(slot)
            consumePinned(rsvp("follow-rsvp", "follow-going", author = follow))

            assertTrue(CalendarReminderScanner.acceptedRsvpsInCache(listOf(attendee)).none { it.dTag() == "follow-rsvp" })

            val fired = mutableListOf<DueCalendarReminder>()
            CalendarReminderScanner.scan(listOf(attendee), leadMinutes = 15, log = CalendarReminderLogStore(dataStore("follow")), now = now) { fired.add(it) }
            assertTrue(fired.none { it.eventId == slot.id })
        }

    @Test
    fun aReminderThatWasNotPostedIsTriedAgain() =
        runTest {
            val slot = timeSlot("retry-soon", startSeconds = now + 10 * 60)
            consumePinned(slot)
            consumePinned(rsvp("retry-rsvp", "retry-soon"))
            val log = CalendarReminderLogStore(dataStore("retry"))

            CalendarReminderScanner.scan(listOf(attendee), leadMinutes = 15, log = log, now = now) { false }

            val fired = mutableListOf<DueCalendarReminder>()
            CalendarReminderScanner.scan(listOf(attendee), leadMinutes = 15, log = log, now = now + 60) { fired.add(it) }
            assertEquals(listOf(slot.id), fired.filter { it.eventId == slot.id }.map { it.eventId })
        }

    private fun dataStore(name: String = "reminder_log"): DataStore<Preferences> {
        val file = File(folder.root, "$name.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file.toOkioPath() },
        )
    }
}
