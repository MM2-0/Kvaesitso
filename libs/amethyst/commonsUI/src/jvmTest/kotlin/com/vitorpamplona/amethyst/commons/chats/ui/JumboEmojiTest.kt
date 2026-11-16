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
package com.vitorpamplona.amethyst.commons.chats.ui

import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMe
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMeTop
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThemBottom
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThemMiddle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// Emoji are written as escapes so the file stays unambiguous on disk (see AGENTS.md).
class JumboEmojiTest {
    private val grin = "\uD83D\uDE00" // U+1F600
    private val thumbsUpMedium = "\uD83D\uDC4D\uD83C\uDFFD" // U+1F44D + skin tone U+1F3FD
    private val family = "\uD83D\uDC69\u200D\uD83D\uDC67" // woman ZWJ girl: draws as one
    private val flagUs = "\uD83C\uDDFA\uD83C\uDDF8" // two regional indicators
    private val keycapOne = "1\uFE0F\u20E3" // '1' + VS16 + combining keycap
    private val redHeart = "\u2764\uFE0F" // U+2764 + VS16

    @Test
    fun countsUpToThreeEmoji() {
        assertEquals(1, jumboEmojiCount(grin))
        assertEquals(2, jumboEmojiCount(grin + redHeart))
        assertEquals(3, jumboEmojiCount(grin + grin + grin))
    }

    @Test
    fun moreThanThreeIsNotJumbo() = assertEquals(0, jumboEmojiCount(grin + grin + grin + grin))

    @Test
    fun anyTextMakesItNotJumbo() {
        assertEquals(0, jumboEmojiCount("hi $grin"))
        assertEquals(0, jumboEmojiCount("${grin}a"))
    }

    @Test
    fun whitespaceIsIgnored() = assertEquals(2, jumboEmojiCount(" $grin \n $grin "))

    @Test
    fun emptyOrBlankIsZero() {
        assertEquals(0, jumboEmojiCount(""))
        assertEquals(0, jumboEmojiCount("   "))
    }

    @Test
    fun modifiersAndZwjSequencesCountAsOne() {
        assertEquals(1, jumboEmojiCount(thumbsUpMedium))
        assertEquals(1, jumboEmojiCount(family))
        assertEquals(1, jumboEmojiCount(redHeart))
    }

    @Test
    fun flagsNeedBothRegionalIndicators() {
        assertEquals(1, jumboEmojiCount(flagUs))
        assertEquals(0, jumboEmojiCount("\uD83C\uDDFA")) // half a flag
    }

    @Test
    fun keycapsNeedTheirMark() {
        assertEquals(1, jumboEmojiCount(keycapOne))
        assertEquals(0, jumboEmojiCount("1")) // a bare digit is text
        assertEquals(0, jumboEmojiCount("1 $grin"))
    }

    @Test
    fun bubbleShapeFollowsSideAndPosition() {
        assertSame(ChatBubbleShapeMe, chatBubbleShapeFor(true, ChatGroupPosition.SINGLE))
        assertSame(ChatBubbleShapeMeTop, chatBubbleShapeFor(true, ChatGroupPosition.TOP))
        assertSame(ChatBubbleShapeThemMiddle, chatBubbleShapeFor(false, ChatGroupPosition.MIDDLE))
        assertSame(ChatBubbleShapeThemBottom, chatBubbleShapeFor(false, ChatGroupPosition.BOTTOM))
    }
}
