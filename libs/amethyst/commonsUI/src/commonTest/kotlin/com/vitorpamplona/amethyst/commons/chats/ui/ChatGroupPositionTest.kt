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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A NIP-17 rename with no text renders as a system line, not a bubble, so it must not join the
 * author run of the bubbles around it — otherwise the author's next message is drawn as the
 * continuation of a bubble that isn't there: no author line, and a squared top corner.
 */
class ChatGroupPositionTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun message(
        id: Char,
        createdAt: Long,
        content: String,
        subject: String? = null,
    ): Note {
        val tags = listOfNotNull(arrayOf("p", bob), subject?.let { arrayOf("subject", it) }).toTypedArray()
        val event = ChatMessageEvent(id.toString().repeat(64), alice, createdAt, tags, content, "")
        return Note(event.id).also { it.loadEvent(event, User(alice, context), emptyList()) }
    }

    private val start = 1_800_000_000L

    @Test
    fun messageAfterARenameStartsANewRun() {
        val rename = message('1', start, content = "", subject = "Weekend plans")
        val next = message('2', start + 30, content = "hi all")
        val after = message('3', start + 60, content = "who's in?")

        assertEquals(ChatGroupPosition.SINGLE, computeChatGroupPosition(next, rename, null))
        assertEquals(ChatGroupPosition.TOP, computeChatGroupPosition(after, next, rename))
    }

    @Test
    fun messageBeforeARenameEndsItsRun() {
        val first = message('1', start, content = "hey")
        val before = message('2', start + 30, content = "one sec")
        val rename = message('3', start + 60, content = "", subject = "Weekend plans")

        assertEquals(ChatGroupPosition.BOTTOM, computeChatGroupPosition(rename, before, first))
    }

    @Test
    fun renameWithTextStillLeadsTheRunAfterIt() {
        val rename = message('1', start, content = "new goals", subject = "Weekend plans")
        val next = message('2', start + 30, content = "hi all")

        assertEquals(ChatGroupPosition.TOP, computeChatGroupPosition(next, rename, null))
        assertEquals(ChatGroupPosition.BOTTOM, computeChatGroupPosition(null, next, rename))
    }
}
