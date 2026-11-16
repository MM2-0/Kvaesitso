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
package com.vitorpamplona.amethyst.commons.rendering

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Regressions for the second renderer audit. */
class RendererAuditTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000008".hexToByteArray()))
    private val eventId = "ab47ce8e660e1bcd5a31bc0b6ec31fa20337bc2288c935a03af62f0b66b36c22"

    private suspend fun raw(
        kind: Int,
        content: String,
        vararg tags: Array<String>,
    ): Event = alice.sign(1_700_000_000, kind, arrayOf(*tags), content)

    @Test
    fun anEmptyTagDoesNotDegradeTheRendering() =
        runTest {
            val note = raw(TextNoteEvent.KIND, "hi #nostr", arrayOf(), arrayOf("e", eventId, "", "root"), arrayOf("p", bob.pubKey), arrayOf("t", "nostr"))
            val rendered = EventRendererRegistry.render(note)
            assertEquals(eventId, rendered.root?.eventId)
            assertEquals(listOf("nostr"), rendered.hashtags)
            assertTrue(bob.pubKey in rendered.mentions)
        }

    @Test
    fun hostileContentStillRenders() =
        runTest {
            val rendered = EventRendererRegistry.render(raw(TextNoteEvent.KIND, "a@".repeat(50_000)))
            assertEquals(TextNoteEvent.KIND, rendered.kind)
        }

    @Test
    fun aReplyToAnArticleByAddressHasARoot() =
        runTest {
            val address = "30023:${bob.pubKey}:my-article"
            val rendered = EventRendererRegistry.render(raw(TextNoteEvent.KIND, "nice", arrayOf("a", address, "", "root")))
            val root = assertNotNull(rendered.root)
            assertEquals(address, root.address)
            assertEquals(bob.pubKey, root.author)
            assertEquals(address, rendered.replyTo?.address)
        }

    @Test
    fun aFollowListIsNotRepeatedAsMentions() =
        runTest {
            val list = raw(ContactListEvent.KIND, "", arrayOf("p", bob.pubKey))
            val rendered = EventRendererRegistry.render(list)
            assertTrue(rendered.mentions.isEmpty(), rendered.mentions.toString())
            assertEquals(listOf(bob.pubKey), (rendered.details as RenderedDetails.ContactList).follows)
        }
}
