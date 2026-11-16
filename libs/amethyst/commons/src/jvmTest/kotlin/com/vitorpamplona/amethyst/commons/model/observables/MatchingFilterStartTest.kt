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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How [EventListMatchingFilter.start] and [NoteListMatchingFilter.start] meet the cache: the
 * observer registers before it scans, so an event the cache delivers while the scan runs (on a
 * background thread, while relays keep ingesting) is listed, a note deleted meanwhile is not, and
 * the first list a collector sees is the whole one, never a lone live event ahead of it.
 */
class MatchingFilterStartTest {
    private val author = "d0d0a746b44c9de8422165aef520b1fe041eedf5794f7592505477eeac122c18"
    private val filter = Filter(kinds = listOf(AppDefinitionEvent.KIND))

    private fun note(
        dTag: String,
        createdAt: Long,
    ): AddressableNote =
        AddressableNote(Address(AppDefinitionEvent.KIND, author, dTag)).also {
            it.event =
                EventFactory.create(
                    id = "%08x".format(dTag.hashCode()) + "%056x".format(createdAt),
                    pubKey = author,
                    createdAt = createdAt,
                    kind = AppDefinitionEvent.KIND,
                    tags = arrayOf(arrayOf("d", dTag)),
                    content = "{}",
                    sig = "00".repeat(64),
                )
        }

    private val old = note("old", 1_000)
    private val older = note("older", 500)
    private val live = note("live", 2_000)

    /** The two observers behind one interface, so each case runs against both. */
    private interface Subject {
        fun start(register: () -> Unit)

        fun new(note: Note)

        fun remove(note: Note)
    }

    private fun events(
        scan: () -> List<Note>,
        sink: (List<String>) -> Unit,
    ): Subject {
        val observer = EventListMatchingFilter<Event>(filter, { scan() }) { sink(it.map { e -> e.id }) }
        return object : Subject {
            override fun start(register: () -> Unit) = observer.start(register)

            override fun new(note: Note) = observer.new(note.event!!, note)

            override fun remove(note: Note) = observer.remove(note)
        }
    }

    private fun notes(
        scan: () -> List<Note>,
        sink: (List<String>) -> Unit,
    ): Subject {
        val observer = NoteListMatchingFilter(filter, { scan() }) { sink(it.map { n -> n.event!!.id }) }
        return object : Subject {
            override fun start(register: () -> Unit) = observer.start(register)

            override fun new(note: Note) = observer.new(note.event!!, note)

            override fun remove(note: Note) = observer.remove(note)
        }
    }

    private val makers = listOf(::events, ::notes)

    /**
     * A cache stand-in: it delivers to the observer only once registered, as `observables` does,
     * and [during] runs inside the scan, the moment a relay event or a deletion lands.
     */
    private fun runStart(
        make: (() -> List<Note>, (List<String>) -> Unit) -> Subject,
        scanned: List<Note>,
        during: (deliver: Subject) -> Unit,
    ): List<List<String>> {
        val emissions = mutableListOf<List<String>>()
        var registered = false
        lateinit var subject: Subject
        subject =
            make({
                if (registered) during(subject)
                scanned
            }) { emissions.add(it) }
        subject.start { registered = true }
        return emissions
    }

    @Test
    fun anEventDeliveredDuringTheScanIsListed() {
        for (make in makers) {
            val emissions = runStart(make, listOf(old)) { it.new(live) }
            assertEquals(listOf(live.event!!.id, old.event!!.id), emissions.last(), "$make")
        }
    }

    @Test
    fun theFirstEmissionIsTheWholeList() {
        for (make in makers) {
            val emissions = runStart(make, listOf(old)) { it.new(live) }
            assertEquals(1, emissions.size, "$make: one emission, not the live event ahead of the scan: $emissions")
        }
    }

    @Test
    fun aNoteDeletedDuringTheScanIsNotListed() {
        for (make in makers) {
            val emissions = runStart(make, listOf(old, older)) { it.remove(old) }
            assertEquals(listOf(older.event!!.id), emissions.last(), "$make")
        }
    }

    @Test
    fun anEventListedWhileTheFirstListIsSentIsInTheLastOne() {
        for (make in makers) {
            val emissions = mutableListOf<List<String>>()
            var deliverLive: (() -> Unit)? = null
            val subject =
                make({ listOf(old) }) { list ->
                    // A relay thread lists and sends its newer list after start() read the state
                    // but before start()'s own send lands: the older list must not be the last word.
                    deliverLive?.also { deliverLive = null }?.invoke()
                    emissions.add(list)
                }
            deliverLive = { subject.new(live) }
            subject.start { }
            assertEquals(listOf(live.event!!.id, old.event!!.id), emissions.last(), "$make: $emissions")
        }
    }

    @Test
    fun twoEventsListedAtOnceEndOnTheNewerList() {
        for (make in makers) {
            val emissions = mutableListOf<List<String>>()
            var deliverOld: (() -> Unit)? = null
            val subject =
                make({ emptyList() }) { list ->
                    // A second relay thread publishes and sends between the first one's publish
                    // and its send.
                    deliverOld?.also { deliverOld = null }?.invoke()
                    emissions.add(list)
                }
            subject.start { }
            deliverOld = { subject.new(old) }
            subject.new(live)
            assertEquals(listOf(live.event!!.id, old.event!!.id), emissions.last(), "$make: $emissions")
        }
    }

    @Test
    fun theScanIsListedNewestFirstOnce() {
        for (make in makers) {
            // The cache query sorts and de-duplicates, but the observer must not depend on it.
            val emissions = runStart(make, listOf(older, live, old, live)) { }
            assertEquals(listOf(live.event!!.id, old.event!!.id, older.event!!.id), emissions.single(), "$make")
        }
    }
}
