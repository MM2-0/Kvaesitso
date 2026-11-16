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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Curated" (Selected) notification mode gates every event through
 * [NotificationFeedFilter.tagsAnEventByUser]. Replies to the user's likes and
 * zaps are kind:1111 comments whose relevance must be detectable without the
 * parent event in the local cache: reaction parents via the NIP-22 root/reply
 * author tags, zap parents via the `k` tag plus the explicit p tag (the
 * receipt itself is signed by the lightning provider, not the zapper).
 */
class NotificationTagsAnEventByUserTest {
    private val me = "1".repeat(64)
    private val wallet = "2".repeat(64)
    private val replier = "3".repeat(64)
    private val reactionId = "a".repeat(64)
    private val zapId = "b".repeat(64)
    private val replyId = "c".repeat(64)
    private val sig = "f".repeat(128)

    @Test
    fun `reply to my reaction is relevant via nip22 author tags without the parent in cache`() {
        val reply =
            CommentEvent(
                replyId,
                replier,
                1000,
                arrayOf(
                    arrayOf("E", reactionId, "", me),
                    arrayOf("K", "7"),
                    arrayOf("P", me),
                    arrayOf("e", reactionId, "", me),
                    arrayOf("k", "7"),
                    arrayOf("p", me),
                ),
                "lol same",
                sig,
            )
        // Parent reaction NOT in cache: replyTo resolves to an empty note (no author).
        val note =
            Note(replyId).apply {
                event = reply
                replyTo = listOf(Note(reactionId))
            }

        assertTrue(NotificationFeedFilter.tagsAnEventByUser(note, me))
    }

    @Test
    fun `reply to my zap is relevant via k tag and explicit p tag without the receipt in cache`() {
        val reply =
            CommentEvent(
                replyId,
                replier,
                1000,
                arrayOf(
                    arrayOf("E", zapId, "", wallet),
                    arrayOf("K", ZapReceiptEvent.KIND.toString()),
                    arrayOf("P", wallet),
                    arrayOf("e", zapId, "", wallet),
                    arrayOf("k", ZapReceiptEvent.KIND.toString()),
                    arrayOf("p", wallet),
                    arrayOf("p", me),
                ),
                "thanks for the zap!",
                sig,
            )
        val note =
            Note(replyId).apply {
                event = reply
                replyTo = listOf(Note(zapId))
            }

        assertTrue(NotificationFeedFilter.tagsAnEventByUser(note, me))
    }

    @Test
    fun `reply to someone else's zap that does not tag me stays irrelevant`() {
        val reply =
            CommentEvent(
                replyId,
                replier,
                1000,
                arrayOf(
                    arrayOf("E", zapId, "", wallet),
                    arrayOf("K", ZapReceiptEvent.KIND.toString()),
                    arrayOf("P", wallet),
                    arrayOf("e", zapId, "", wallet),
                    arrayOf("k", ZapReceiptEvent.KIND.toString()),
                    arrayOf("p", wallet),
                ),
                "nice zap",
                sig,
            )
        val note =
            Note(replyId).apply {
                event = reply
                replyTo = listOf(Note(zapId))
            }

        assertFalse(NotificationFeedFilter.tagsAnEventByUser(note, me))
    }
}
