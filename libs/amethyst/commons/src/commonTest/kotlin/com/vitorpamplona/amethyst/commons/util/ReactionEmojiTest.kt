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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReactionEmojiTest {
    private fun tags(vararg tags: Array<String>) = ImmutableListOfLists(arrayOf(*tags))

    @Test
    fun customEmojiResolvesItsUrlFromTheEmojiTag() {
        val tags =
            tags(
                arrayOf("e", "aa".repeat(32)),
                arrayOf("emoji", "soapbox", "https://example.com/soapbox.png"),
            )
        assertEquals(":soapbox:https://example.com/soapbox.png", ":soapbox:".firstFullCharOrEmoji(tags))
    }

    @Test
    fun customEmojiWithoutATagStaysAsTheShortcode() {
        assertEquals(":soapbox:", ":soapbox:".firstFullCharOrEmoji(tags(arrayOf("e", "aa".repeat(32)))))
    }

    @Test
    fun customEmojiIgnoresNonEmojiTagsWithTheSameValue() {
        val tags = tags(arrayOf("t", "soapbox", "https://example.com/not-an-emoji.png"))
        assertEquals(":soapbox:", ":soapbox:".firstFullCharOrEmoji(tags))
    }

    @Test
    fun plainReactionsAreUnchanged() {
        assertEquals("+", "+".firstFullCharOrEmoji(tags()))
        assertEquals("-", "-".firstFullCharOrEmoji(tags()))
        assertEquals("😀", "😀".firstFullCharOrEmoji(tags()))
    }

    @Test
    fun customEmojiUrlIsReadFromTheNormalizedReaction() {
        assertEquals("https://example.com/soapbox.png", ":soapbox:https://example.com/soapbox.png".customEmojiUrlOrNull())
        assertEquals("https://x.com:8080/a.png", ":soapbox:https://x.com:8080/a.png".customEmojiUrlOrNull())
    }

    @Test
    fun bareShortcodeHasNoCustomEmojiUrl() {
        assertNull(":soapbox:".customEmojiUrlOrNull())
        assertNull(":soapbox: ".customEmojiUrlOrNull())
        assertNull(":soapbox".customEmojiUrlOrNull())
        assertNull(":".customEmojiUrlOrNull())
    }

    @Test
    fun plainReactionsHaveNoCustomEmojiUrl() {
        assertNull("+".customEmojiUrlOrNull())
        assertNull("😀".customEmojiUrlOrNull())
        assertNull("".customEmojiUrlOrNull())
    }

    @Test
    fun untaggedShortcodeReactionRendersWithoutAnImage() {
        // End to end: the normalized key of an untagged `:blob:` reaction must not yield an image url.
        assertNull(":blob:".firstFullCharOrEmoji(tags()).customEmojiUrlOrNull())
    }
}
