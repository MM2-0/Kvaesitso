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
package com.vitorpamplona.amethyst.commons.model.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShareToDMRouteRewriterTest {
    @Test
    fun injectsMessageAndAttachmentIntoRoomRoute() {
        val original = Route.Room(id = "pubkeyA,pubkeyB")
        val result = ShareToDMRouteRewriter.rewrite(original, "hello", "content://media/1")

        result as Route.Room
        assertEquals("pubkeyA,pubkeyB", result.id)
        assertEquals("hello", result.message)
        assertEquals("content://media/1", result.attachment)
    }

    @Test
    fun preservesExistingRoomFields() {
        val original = Route.Room(id = "x", replyId = "reply1", expiresDays = 3)
        val result = ShareToDMRouteRewriter.rewrite(original, "hi", null) as Route.Room

        assertEquals("reply1", result.replyId)
        assertEquals(3, result.expiresDays)
        assertEquals("hi", result.message)
    }

    @Test
    fun leavesNonRoomRoutesUnchanged() {
        val original = Route.Home
        val result = ShareToDMRouteRewriter.rewrite(original, "hi", "uri")
        assertSame(original, result)
    }
}
