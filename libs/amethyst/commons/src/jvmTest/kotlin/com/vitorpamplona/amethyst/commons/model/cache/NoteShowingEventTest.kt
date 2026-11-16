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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class NoteShowingEventTest {
    private val signer = NostrSignerSync(KeyPair())

    private fun consumed(
        kind: Int,
        createdAt: Long,
        vararg tags: Array<String>,
    ): Event {
        val event = signer.sign<Event>(EventTemplate(createdAt, kind, arrayOf(*tags), "v$createdAt"))
        LocalCache.justConsume(event, null, false)
        return event
    }

    @Test
    fun theCurrentVersionOfAnAddressableEventIsItsAddressableNote() {
        // A music track's nevent: the same note a filter, an observer or the cache scan returns.
        val track = consumed(36787, 1_700_000_100L, arrayOf("d", "current-track"))
        val note = LocalCache.getNoteShowingEventIfExists(track.id)

        assertIs<AddressableNote>(note)
        assertSame(LocalCache.getAddressableNoteIfExists((track as AddressableEvent).address()), note)
        assertEquals(track.id, note.event?.id)
        // What the search screen's direct hit used to take: a second note holding the same event,
        // which drew the track twice beside the cache scan's addressable note.
        assertNotSame<Any?>(note, LocalCache.getNoteIfExists(track.id))
    }

    @Test
    fun aSupersededVersionIsItsOwnNote() {
        val old = consumed(36787, 1_700_000_000L, arrayOf("d", "edited-track"))
        val new = consumed(36787, 1_700_000_500L, arrayOf("d", "edited-track"))

        val oldNote = LocalCache.getNoteShowingEventIfExists(old.id)
        assertEquals(old.id, oldNote?.idHex)
        assertNotSame(LocalCache.getAddressableNoteIfExists((new as AddressableEvent).address()), oldNote)
        assertIs<AddressableNote>(LocalCache.getNoteShowingEventIfExists(new.id))
    }

    @Test
    fun aReplaceableEventFollowsTheSameRule() {
        val list = consumed(10002, 1_700_000_200L)
        assertIs<AddressableNote>(LocalCache.getNoteShowingEventIfExists(list.id))
    }

    @Test
    fun aRegularEventIsItsOwnNote() {
        val text = consumed(1, 1_700_000_300L)
        assertSame(LocalCache.getNoteIfExists(text.id), LocalCache.getNoteShowingEventIfExists(text.id))
    }

    @Test
    fun anUncachedIdIsNull() {
        assertNull(LocalCache.getNoteShowingEventIfExists("f".repeat(64)))
    }
}
