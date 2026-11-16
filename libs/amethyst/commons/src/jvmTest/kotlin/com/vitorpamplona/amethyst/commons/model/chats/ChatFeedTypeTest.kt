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
package com.vitorpamplona.amethyst.commons.model.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFeedTypeTest {
    // The Messages settings persist the DISABLED set as `ALL - enabled`; these tests pin the
    // round-trip so a stored preferences string keeps meaning the same thing across releases.

    @Test
    fun allContainsEveryType() {
        assertEquals(ChatFeedType.entries.toSet(), ChatFeedType.ALL)
    }

    @Test
    fun emptyEncodeMeansAllOn() {
        // Nothing disabled -> empty string -> decode back to nothing disabled -> everything enabled.
        val encoded = ChatFeedType.encode(ChatFeedType.ALL - ChatFeedType.ALL)
        assertEquals("", encoded)
        assertEquals(ChatFeedType.ALL, ChatFeedType.ALL - ChatFeedType.decode(encoded))
    }

    @Test
    fun nullDecodesToNothingDisabled() {
        assertEquals(emptySet<ChatFeedType>(), ChatFeedType.decode(null))
        assertEquals(ChatFeedType.ALL, ChatFeedType.ALL - ChatFeedType.decode(null))
    }

    @Test
    fun roundTripsAPartiallyDisabledSet() {
        val enabled = ChatFeedType.ALL - ChatFeedType.NIP04 - ChatFeedType.GEOHASH
        val storedDisabled = ChatFeedType.encode(ChatFeedType.ALL - enabled)
        val restored = ChatFeedType.ALL - ChatFeedType.decode(storedDisabled)
        assertEquals(enabled, restored)
    }

    @Test
    fun stableCodesForEveryType() {
        // Codes are the on-disk identity; renaming one silently disables it for upgraders.
        assertEquals("nip17", ChatFeedType.NIP17.code)
        assertEquals("nip04", ChatFeedType.NIP04.code)
        assertEquals("nip28", ChatFeedType.NIP28.code)
        assertEquals("nip29", ChatFeedType.NIP29.code)
        assertEquals("marmot", ChatFeedType.MARMOT.code)
        assertEquals("concord", ChatFeedType.CONCORD.code)
        assertEquals("geohash", ChatFeedType.GEOHASH.code)
        assertEquals("ephemeral", ChatFeedType.EPHEMERAL.code)
    }

    @Test
    fun unknownCodesAreDropped() {
        assertNull(ChatFeedType.fromCode("does-not-exist"))
        // A future/unknown code in stored prefs must not disable a known type.
        val decoded = ChatFeedType.decode("nip04,future-kind")
        assertEquals(setOf(ChatFeedType.NIP04), decoded)
        assertTrue(ChatFeedType.NIP17 in (ChatFeedType.ALL - decoded))
    }
}
