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

class EventListMatchingFilterTest {
    private val author = "d0d0a746b44c9de8422165aef520b1fe041eedf5794f7592505477eeac122c18"

    private val filter = Filter(kinds = listOf(AppDefinitionEvent.KIND))

    private fun noteFor(dTag: String) = AddressableNote(Address(AppDefinitionEvent.KIND, author, dTag))

    private fun appDefinition(
        dTag: String,
        createdAt: Long,
    ): Event =
        EventFactory.create(
            // Unique per (dTag, createdAt): 8 hex of the dTag hash + 56 hex of createdAt,
            // so distinct addresses never collide on an event id.
            id = "%08x".format(dTag.hashCode()) + "%056x".format(createdAt),
            pubKey = author,
            createdAt = createdAt,
            kind = AppDefinitionEvent.KIND,
            tags = arrayOf(arrayOf("d", dTag)),
            content = "{}",
            sig = "00".repeat(64),
        )

    private fun AddressableNote.load(createdAt: Long): Event = appDefinition(dTag(), createdAt).also { this.event = it }

    private fun newFilter(
        withFilter: Filter = filter,
        sink: (List<Event>) -> Unit,
    ) = EventListMatchingFilter<Event>(
        filter = withFilter,
        atOnce = { emptyList() },
        update = sink,
    )

    @Test
    fun newerVersionReflectsUpdatedEventWithoutDuplicate() {
        var last: List<Event> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        // A crowd of app definitions, so a stale skip-set node could be bypassed.
        val target = noteFor("nostr-dvm-labeler")
        val other = noteFor("other-app")
        val top = noteFor("top-app")

        val v1 = target.load(1000)
        subject.new(v1, target)
        subject.new(other.load(2000), other)
        subject.new(top.load(4000), top)

        // Newer version replaces the event on the SAME instance, created_at 1000 -> 3000.
        val v2 = target.load(3000)
        subject.new(v2, target)

        // Exactly one entry for the target, and it is the NEW version (reflected + re-sorted).
        assertEquals(3, last.size, "no duplicate event for the updated addressable")
        assertEquals(1, last.count { it.id == v2.id }, "the updated addressable appears exactly once")
        assertEquals(0, last.count { it.id == v1.id }, "the old version is gone")
    }

    @Test
    fun listStaysSortedByCreatedAtDescending() {
        var last: List<Event> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        val a = noteFor("app-a")
        val b = noteFor("app-b")
        val c = noteFor("app-c")

        val ea = a.load(2000)
        subject.new(ea, a)
        val eb = b.load(4000)
        subject.new(eb, b)
        val ec = c.load(1000)
        subject.new(ec, c)

        assertEquals(listOf(eb.id, ea.id, ec.id), last.map { it.id })
    }

    @Test
    fun versionNoteReEmitsWhenAddressableIsListed() {
        val emissions = mutableListOf<List<Event>>()
        val subject = newFilter { emissions.add(it) }
        subject.init()

        val target = noteFor("nostr-dvm-labeler")
        val event = target.load(1000)
        subject.new(event, target)
        val countAfterInsert = emissions.size

        // The "version" note: a regular Note holding the addressable event.
        val versionNote = Note(event.id).apply { this.event = event }
        subject.new(event, versionNote)

        // It re-emits (addressable is listed) but never adds a second entry.
        assertEquals(countAfterInsert + 1, emissions.size, "version note triggers a re-emit")
        assertEquals(listOf(event.id), emissions.last().map { it.id })
    }

    @Test
    fun removeDropsTheEventEvenAfterCreatedAtChanged() {
        var last: List<Event> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        val target = noteFor("nostr-dvm-labeler")
        subject.new(target.load(1000), target)
        assertEquals(1, last.size)

        target.load(2000) // sort key moves before the delete arrives
        subject.remove(target)
        assertEquals(0, last.size, "remove finds the event despite the created_at change")
    }

    @Test
    fun concurrentUpdatesNeverEmitDuplicateEvents() {
        assertNoDuplicateUnderConcurrency(filter)
    }

    @Test
    fun concurrentUpdatesWithLimitNeverEmitDuplicateEvents() {
        assertNoDuplicateUnderConcurrency(Filter(kinds = listOf(AppDefinitionEvent.KIND), limit = 5))
    }

    private fun assertNoDuplicateUnderConcurrency(withFilter: Filter) {
        val firstViolation = AtomicReference<List<String>?>(null)
        val subject =
            newFilter(withFilter) { emitted ->
                val ids = emitted.map { it.id }
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
                        val event = note.load(1_000L + (i % 9))
                        if ((seed ushr 8) % 3 == 0) {
                            subject.remove(note)
                        } else {
                            subject.new(event, note)
                        }
                    }
                }
            }

        start.countDown()
        threads.forEach { it.join() }

        assertNull(firstViolation.get(), "an emission carried a duplicate event id: ${firstViolation.get()}")
    }
}
