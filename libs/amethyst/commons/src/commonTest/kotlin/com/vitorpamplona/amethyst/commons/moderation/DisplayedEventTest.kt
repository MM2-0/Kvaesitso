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
package com.vitorpamplona.amethyst.commons.moderation

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayedEventTest {
    private val author = "a".repeat(64)
    private val sig = "0".repeat(128)

    private fun textNoteEvent(id: String = "1".padEnd(64, '0')): Event =
        Event(
            id = id,
            pubKey = author,
            createdAt = 0L,
            kind = 1,
            tags = emptyArray(),
            content = "hello",
            sig = sig,
        )

    private fun repostWrapper(content: String): RepostEvent =
        RepostEvent(
            id = "r".repeat(64),
            pubKey = author,
            createdAt = 0L,
            tags = emptyArray(),
            content = content,
            sig = sig,
        )

    @Test
    fun nonRepostReturnsOwnEvent() {
        val inner = textNoteEvent()
        val note = Note(idHex = inner.id).apply { event = inner }
        assertEquals(inner, note.displayedEvent())
    }

    @Test
    fun repostWithReplyToReturnsInnerEvent() {
        val inner = textNoteEvent(id = "i".repeat(64))
        val innerNote = Note(idHex = inner.id).apply { event = inner }
        val wrapper = repostWrapper(content = "{}") // valid JSON but missing fields
        val wrapperNote =
            Note(idHex = wrapper.id).apply {
                event = wrapper
                replyTo = listOf(innerNote)
            }
        assertEquals(inner, wrapperNote.displayedEvent())
    }

    @Test
    fun repostWithoutReplyToFallsBackToContainedPost() {
        // Malformed inner JSON → containedPost() returns null → displayedEvent() returns null.
        val wrapper = repostWrapper(content = "not-valid-json-at-all")
        val wrapperNote = Note(idHex = wrapper.id).apply { event = wrapper }
        assertNull(wrapperNote.displayedEvent())
    }

    @Test
    fun noteWithoutEventReturnsNull() {
        val note = Note(idHex = "x".repeat(64))
        assertNull(note.displayedEvent())
    }
}
