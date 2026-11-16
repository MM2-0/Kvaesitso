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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The kind-1009 overlay rule, resolved off a message's own [Note.edits].
 *
 * Both halves are read-side on purpose: a sender cannot be trusted to have
 * applied them, and this is the only place the app decides which text a reader
 * actually sees.
 */
class MarmotEditOverlayTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun user(pubkey: String) = User(pubkey, context)

    private fun event(
        id: String,
        pubkey: String,
        kind: Int,
        content: String,
        createdAt: Long,
        targetId: String? = null,
    ) = Event(
        id = id,
        pubKey = pubkey,
        createdAt = createdAt,
        kind = kind,
        tags = targetId?.let { arrayOf(arrayOf("e", it)) } ?: emptyArray(),
        content = content,
        sig = "",
    )

    private fun note(event: Event): Note = Note(event.id).also { it.loadEvent(event, user(event.pubKey), emptyList()) }

    private fun target(): Note = note(event("0".repeat(64), alice, MarmotAppEvent.KIND_CHAT, "frist post", 1_800_000_000L))

    private fun edit(
        id: String,
        author: String,
        content: String,
        createdAt: Long,
        targetId: String = "0".repeat(64),
    ) = note(event(id, author, MarmotAppEvent.KIND_EDIT, content, createdAt, targetId))

    @Test
    fun `an unedited message has no overlay`() {
        assertNull(target().latestMarmotEdit())
    }

    @Test
    fun `the author's own edit overlays their message`() {
        val message = target()
        message.addEdit(edit("1".repeat(64), alice, "first post", 1_800_000_100L))
        assertEquals("first post", message.latestMarmotEdit()?.event?.content)
    }

    @Test
    fun `an edit by another account is ignored`() {
        // Only the original author may replace their words. The transport
        // cannot enforce this — any member can send a well-formed 1009 naming
        // someone else's message — so the reader has to.
        val message = target()
        message.addEdit(edit("2".repeat(64), bob, "not mine", 1_800_000_100L))
        assertNull(message.latestMarmotEdit())
    }

    @Test
    fun `the latest edit wins`() {
        val message = target()
        message.addEdit(edit("1".repeat(64), alice, "v2", 1_800_000_100L))
        message.addEdit(edit("2".repeat(64), alice, "v3", 1_800_000_300L))
        message.addEdit(edit("3".repeat(64), alice, "v2b", 1_800_000_200L))
        assertEquals("v3", message.latestMarmotEdit()?.event?.content)
    }

    @Test
    fun `a same-second pair resolves by event id identically for every reader`() {
        // Two devices of one account can stamp the same second. Without a
        // deterministic tie-break two readers would render different text for
        // the same message forever, and neither would be wrong.
        val stamp = 1_800_000_100L
        val ascending = target()
        ascending.addEdit(edit("1".repeat(64), alice, "from device A", stamp))
        ascending.addEdit(edit("f".repeat(64), alice, "from device B", stamp))

        val descending = target()
        descending.addEdit(edit("f".repeat(64), alice, "from device B", stamp))
        descending.addEdit(edit("1".repeat(64), alice, "from device A", stamp))

        assertEquals("from device B", ascending.latestMarmotEdit()?.event?.content)
        assertEquals(
            ascending.latestMarmotEdit()?.event?.content,
            descending.latestMarmotEdit()?.event?.content,
            "insertion order must not decide the winner",
        )
    }

    @Test
    fun `a non-edit child is not an overlay`() {
        // `edits` is a general child list; a reaction or any other kind
        // anchored to the message must not be read as replacement text.
        val message = target()
        message.addEdit(note(event("4".repeat(64), alice, 7, "🍕", 1_800_000_400L)))
        assertNull(message.latestMarmotEdit())
    }
}
