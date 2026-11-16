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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression cover for the Concord attach-before-consume ordering: `LocalCache.consumeConcordRumor`
 * attaches a message row to its [ConcordChannel] (so the note carries its gatherer through the
 * Messages filter) *before* `justConsume` loads the event, so [com.vitorpamplona.amethyst.commons.model.Channel.addNote]
 * runs while `createdAt()` is null and cannot pick [com.vitorpamplona.amethyst.commons.model.Channel.lastNote].
 * Without the [com.vitorpamplona.amethyst.commons.model.Channel.refreshAfterEventLoad] follow-up,
 * `lastNote` stays null forever and every channel row renders "No messages yet" even after messages
 * have loaded.
 */
class ConcordChannelLastNoteTest {
    private val community = "aa".repeat(32)
    private val channelId = "cc".repeat(32)
    private val author = "bb".repeat(32)

    private fun channel() = ConcordChannel(ConcordChannelId(community, channelId))

    private fun event(createdAt: Long): Event = EventFactory.create("00".repeat(32), author, createdAt, 1, arrayOf(), "hi", "22".repeat(64))

    @Test
    fun lastNoteStaysNullUntilEventLoads_thenReflectsIt() {
        val c = channel()
        val note = Note("11".repeat(32))

        // Attach exactly as Concord does — before the rumor is consumed, so the event is still null.
        c.addNote(note)
        assertNull(c.lastNote, "addNote can't pick lastNote while createdAt() is null (the bug)")

        // justConsume loads the event; the follow-up refresh must now set lastNote.
        note.event = event(1000)
        c.refreshAfterEventLoad(note)
        assertEquals(note, c.lastNote)
    }

    @Test
    fun refreshKeepsTheNewestAndIgnoresLaterOlderLoads() {
        val c = channel()
        val older = Note("11".repeat(32))
        val newer = Note("22".repeat(32))
        c.addNote(older)
        c.addNote(newer)

        older.event = event(1000)
        c.refreshAfterEventLoad(older)
        newer.event = event(2000)
        c.refreshAfterEventLoad(newer)
        assertEquals(newer, c.lastNote)

        // An out-of-order older message loading afterwards must not overwrite the newest.
        val evenOlder = Note("33".repeat(32))
        c.addNote(evenOlder)
        evenOlder.event = event(500)
        c.refreshAfterEventLoad(evenOlder)
        assertEquals(newer, c.lastNote)
    }

    @Test
    fun refreshIsANoOpForANoteNotInThisChannel() {
        val c = channel()
        val stray = Note("44".repeat(32)).apply { event = event(9999) }
        c.refreshAfterEventLoad(stray)
        assertNull(c.lastNote)
    }
}
