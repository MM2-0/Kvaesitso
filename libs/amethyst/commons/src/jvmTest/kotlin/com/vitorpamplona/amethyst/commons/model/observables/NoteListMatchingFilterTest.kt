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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.EventFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteListMatchingFilterTest {
    private val author = "d0d0a746b44c9de8422165aef520b1fe041eedf5794f7592505477eeac122c18"

    private val filter = Filter(kinds = listOf(AppDefinitionEvent.KIND))

    // Amethyst reuses a single AddressableNote instance per address (LocalCache),
    // so a newer replaceable event mutates createdAt on the SAME object.
    private fun noteFor(dTag: String) = AddressableNote(Address(AppDefinitionEvent.KIND, author, dTag))

    private fun appDefinition(
        dTag: String,
        createdAt: Long,
    ): Event =
        EventFactory.create(
            id = "%064x".format(createdAt),
            pubKey = author,
            createdAt = createdAt,
            kind = AppDefinitionEvent.KIND,
            tags = arrayOf(arrayOf("d", dTag)),
            content = "{}",
            sig = "00".repeat(64),
        )

    private fun AddressableNote.load(createdAt: Long) {
        event = appDefinition(dTag(), createdAt)
    }

    private fun newFilter(
        withFilter: Filter = filter,
        sink: (List<Note>) -> Unit,
    ) = NoteListMatchingFilter(
        filter = withFilter,
        atOnce = { emptyList() },
        update = sink,
    )

    @Test
    fun newerVersionOfAnAddressableDoesNotDuplicateTheKey() {
        var last: List<Note> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        // Three app definitions arrive. Their createdAt spread matters: after the
        // target moves, the sorted set's search path for the new key must be able
        // to bypass the stale node, which is what corrupts a createdAt-ordered set.
        val target = noteFor("nostr-dvm-labeler")
        val newer = noteFor("other-app")
        val newest = noteFor("top-app")

        target.load(1000)
        subject.new(target.event!!, target)
        newer.load(2000)
        subject.new(newer.event!!, newer)
        newest.load(4000)
        subject.new(newest.event!!, newest)
        assertEquals(3, last.size)

        // A newer definition replaces the event on the SAME target instance,
        // moving its createdAt from 1000 to 3000 (now between 2000 and 4000).
        // LocalCache then notifies the observer again. This must NOT insert the
        // note a second time.
        target.load(3000)
        subject.new(target.event!!, target)

        assertEquals(
            listOf(newest.idHex, newer.idHex, target.idHex).sorted(),
            last.map { it.idHex }.sorted(),
            "each addressable must appear exactly once",
        )
        assertEquals(last.size, last.map { it.idHex }.toSet().size, "no duplicate keys")
    }

    @Test
    fun listStaysSortedByCreatedAtDescendingAsNotesArriveOutOfOrder() {
        var last: List<Note> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        val a = noteFor("app-a")
        val b = noteFor("app-b")
        val c = noteFor("app-c")

        // Arrive out of order; the emitted list must always be newest-first.
        a.load(2000)
        subject.new(a.event!!, a)
        b.load(4000)
        subject.new(b.event!!, b)
        c.load(1000)
        subject.new(c.event!!, c)

        assertEquals(listOf(b.idHex, a.idHex, c.idHex), last.map { it.idHex })
    }

    @Test
    fun concurrentNewRemoveNeverEmitsDuplicateKeys() {
        // No limit: exercises the compute/remove per-key critical sections.
        assertNoDuplicateUnderConcurrency(filter)
    }

    @Test
    fun concurrentNewRemoveWithLimitNeverEmitsDuplicateKeys() {
        // With a limit: also exercises the cross-key eviction (pollLast + byId.remove).
        assertNoDuplicateUnderConcurrency(Filter(kinds = listOf(AppDefinitionEvent.KIND), limit = 5))
    }

    private fun assertNoDuplicateUnderConcurrency(withFilter: Filter) {
        // Observer callbacks fire from several consume threads at once (relay
        // ingest + UI-side justConsume). new()/remove() for the same idHex must
        // keep the sorted index and the membership map consistent, or a duplicate
        // idHex leaks into an emission and crashes the LazyColumn.
        val firstViolation = AtomicReference<List<String>?>(null)
        val subject =
            newFilter(withFilter) { emitted ->
                val ids = emitted.map { it.idHex }
                if (ids.size != ids.toSet().size) {
                    firstViolation.compareAndSet(null, ids)
                }
            }
        subject.init()

        val addresses = (0 until 12).map { "app-$it" }
        val notes = addresses.associateWith { noteFor(it) }
        val threadCount = 8
        val iterations = 5_000
        val start = CountDownLatch(1)

        val threads =
            (0 until threadCount).map { t ->
                thread {
                    start.await()
                    var seed = t * 31 + 7
                    repeat(iterations) { i ->
                        seed = seed * 1103515245 + 12345
                        val note = notes.getValue(addresses[(seed ushr 16) % addresses.size])
                        // Move created_at around so the sort key keeps changing under the set.
                        note.event = appDefinition(note.dTag(), 1_000L + (i % 9))
                        if ((seed ushr 8) % 3 == 0) {
                            subject.remove(note)
                        } else {
                            subject.new(note.event!!, note)
                        }
                    }
                }
            }

        start.countDown()
        threads.forEach { it.join() }

        assertNull(firstViolation.get(), "an emission carried a duplicate idHex: ${firstViolation.get()}")
    }

    @Test
    fun ignoresTheVersionNoteThatHoldsAnAddressableEvent() {
        // consumeBaseReplaceable also notifies observers with the "version" note:
        // getOrCreateNote(event.id), a regular Note (not AddressableNote) carrying
        // the AddressableEvent. It must never enter the addressable list.
        var last: List<Note> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        val event = appDefinition("nostr-dvm-labeler", 1000)
        val versionNote = Note(event.id).apply { this.event = event }
        subject.new(event, versionNote)

        assertEquals(emptyList(), last)

        // The addressable note for the same event, however, is listed.
        val addressable = noteFor("nostr-dvm-labeler").apply { this.event = event }
        subject.new(event, addressable)
        assertEquals(listOf(addressable.idHex), last.map { it.idHex })
    }

    @Test
    fun removeDropsTheNoteEvenAfterCreatedAtChanged() {
        var last: List<Note> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        val target = noteFor("nostr-dvm-labeler")
        target.load(1000)
        subject.new(target.event!!, target)
        assertEquals(1, last.size)

        // The createdAt sort key moves before the delete arrives.
        target.load(2000)
        subject.remove(target)

        assertEquals(0, last.size, "remove must find the note despite the createdAt change")
    }
}
