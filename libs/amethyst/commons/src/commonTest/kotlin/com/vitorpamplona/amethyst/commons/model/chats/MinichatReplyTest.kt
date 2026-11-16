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

import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A kind-9 chat message is a thread reply only when its `e` tag carries a NIP-10 marker.
 *
 * Three conventions share kind 9 and must not be confused:
 * - **NIP-C7** spends `q` on the in-chat reply and never mentions `e` at all, which is what leaves a
 *   marked `e` free to mean "thread reply".
 * - **WhiteNoise / Marmot** thread chat with a **plain, unmarked** `e` — an *in-chat* reply that has to
 *   keep rendering as a quote bubble in the timeline.
 * - **Buzz** threads with `["e", id, "", "reply"]` (nested: `root` + `reply`), which belongs in the
 *   minichat and must be dropped from the channel timeline.
 *
 * Getting this wrong is what put a Buzz thread reply in the main channel as a quote instead of in the
 * thread on its parent.
 */
class MinichatReplyTest {
    private val parentId = "1a05130cc86929f267747b17761d5873a95dbab66d5298c38a352bdfd0edc730"
    private val rootId = "bf2e60b69fdf6bf3aa11223344556677889900aabbccddeeff00112233445566"
    private val channel = "6a39da2f-33c0-44f6-a050-c4da0138644a"

    private fun chat(vararg tags: Array<String>) =
        ChatEvent(
            id = "id",
            pubKey = "pk",
            createdAt = 1L,
            tags = arrayOf(arrayOf("h", channel), *tags),
            content = "hi",
            sig = "sig",
        )

    /** The exact shape observed on the wire from Buzz's client for a direct reply. */
    @Test
    fun `buzz direct reply - reply-marked e tag - is a thread reply`() {
        assertTrue(isMinichatReply(chat(arrayOf("e", parentId, "", "reply"))))
    }

    /** Nested reply: `root` + `reply`, matching Buzz's `_buildReplyTags`. */
    @Test
    fun `buzz nested reply - root plus reply markers - is a thread reply`() {
        assertTrue(
            isMinichatReply(
                chat(arrayOf("e", rootId, "", "root"), arrayOf("e", parentId, "", "reply")),
            ),
        )
    }

    /**
     * Regression: WhiteNoise/Marmot use a bare `e`, which is an *in-chat* reply. Matching on the tag
     * rather than the marker would swallow those into the minichat and empty the timeline.
     */
    @Test
    fun `whitenoise unmarked e tag stays an in-chat reply`() {
        assertFalse(isMinichatReply(chat(arrayOf("e", parentId))))
        assertFalse(isMinichatReply(chat(arrayOf("e", parentId, ""))))
    }

    /** NIP-C7's own reply mechanism renders inline, not in a thread. */
    @Test
    fun `nip-c7 q tag reply stays an in-chat reply`() {
        assertFalse(isMinichatReply(chat(arrayOf("q", parentId, "", "pk"))))
    }

    @Test
    fun `a plain top-level chat message is not a thread reply`() {
        assertFalse(isMinichatReply(chat()))
    }

    /** A `root`-only marker (no `reply`) is a thread root reference, not a reply to that message. */
    @Test
    fun `root marker alone is not a reply`() {
        assertFalse(isMinichatReply(chat(arrayOf("e", rootId, "", "root"))))
    }

    /** "Also send to channel": Buzz flags the reply `broadcast`, on kind 9 exactly as on 40002. */
    @Test
    fun `buzz broadcast reply stays in the channel timeline`() {
        assertFalse(isMinichatReply(chat(arrayOf("e", parentId, "", "reply"), arrayOf("broadcast", "1"))))
    }
}
