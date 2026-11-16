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

import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplyContextTest {
    private val parentEventId = "b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c"
    private val parentAuthorPubKey = "4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0"

    /** Non-reply text note. Just content, no e/a tags. */
    @Test
    fun nonReplyReturnsNull() {
        val event = TextNoteEvent("", "", 0, emptyArray(), "hello world", "")
        val ctx = ReplyContext.from(event, null)
        assertNull(ctx)
    }

    /**
     * NIP-10 reply with a marked "reply" e-tag. The author can't come from
     * a CommentEvent (kind 1 isn't one), so it must come from the cache's
     * parent-note author lookup.
     */
    @Test
    fun markedReplyResolvesParentAuthorViaCache() {
        val event =
            TextNoteEvent(
                "",
                "",
                0,
                arrayOf(
                    arrayOf("e", parentEventId, "", "reply"),
                    arrayOf("p", parentAuthorPubKey),
                ),
                "agreed",
                "",
            )

        val parentUser = User(parentAuthorPubKey) { addr -> Note(addr.toValue()) }
        val parentNote = Note(parentEventId).apply { author = parentUser }
        val cache = StubCache(notesById = mapOf(parentEventId to parentNote))

        val ctx = ReplyContext.from(event, cache)
        assertEquals(parentEventId, ctx?.parentNoteId)
        assertEquals(parentAuthorPubKey, ctx?.parentAuthorPubKey)
        // No user metadata loaded — display falls back to truncated hex + ellipsis.
        assertTrue(ctx?.parentAuthorDisplay?.endsWith("…") == true)
    }

    /**
     * Reply with the parent NOT in the cache and no NIP-22 replyAuthor() tag.
     * from() can't resolve an author, so it bails out (returns null).
     * Recomposition picks up the label later once the parent arrives.
     */
    @Test
    fun replyWithoutParentInCacheReturnsNull() {
        val event =
            TextNoteEvent(
                "",
                "",
                0,
                arrayOf(arrayOf("e", parentEventId, "", "reply")),
                "agreed",
                "",
            )
        val cache = StubCache(notesById = emptyMap())
        val ctx = ReplyContext.from(event, cache)
        assertNull(ctx)
    }

    /** Address coordinates contain colons; event IDs do not. */
    @Test
    fun addressCoordDiscriminatorContainsColon() {
        val rawAddress = "30023:$parentAuthorPubKey:my-article"
        assertTrue(rawAddress.contains(":"))
        assertTrue(!parentEventId.contains(":"))
    }
}
