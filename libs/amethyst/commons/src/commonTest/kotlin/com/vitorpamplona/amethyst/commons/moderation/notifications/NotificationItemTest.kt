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
package com.vitorpamplona.amethyst.commons.moderation.notifications

import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.composer.messageTags
import com.vitorpamplona.amethyst.commons.model.composer.quoteMessage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NotificationItemTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000008".hexToByteArray()))

    @Test
    fun classifiesByKindAndShape() =
        runTest {
            val mine = alice.sign(TextNoteEvent.build("mine"))

            val reply = ReplyActions.replyTo(EventHintBundle(mine), "re", bob)
            assertEquals("reply", NotificationItem.classify(reply)?.type)

            val mention = bob.sign(TextNoteEvent.build("hey") { pTag(PTag(alice.pubKey)) })
            assertEquals("mention", NotificationItem.classify(mention)?.type)

            // A quote cites the note (q tag) without replying to it.
            val quoteText = quoteMessage("look", EventCache().also { it.justConsume(mine, null, true) }.getOrCreateNote(mine.id))
            val quote = bob.sign(TextNoteEvent.build(quoteText) { messageTags(quoteText) })
            assertEquals("mention", NotificationItem.classify(quote)?.type)

            val like = bob.sign(ReactionEvent.build("+", EventHintBundle(mine)))
            assertEquals("+", assertIs<NotificationItem.Reaction>(NotificationItem.classify(like)).content)

            val repost = bob.sign(RepostEvent.build(EventHintBundle(mine)))
            assertEquals("repost", NotificationItem.classify(repost)?.type)

            val profile = bob.sign<Event>(1_700_000_000, 0, emptyArray(), "{}")
            assertNull(NotificationItem.classify(profile))
        }

    @Test
    fun effectiveAuthorIsTheSignerForNonZaps() =
        runTest {
            val like = bob.sign(ReactionEvent.build("+", EventHintBundle(alice.sign(TextNoteEvent.build("x")))))
            assertEquals(bob.pubKey, NotificationItem.classify(like)!!.effectiveAuthorPubKey)
        }

    @Test
    fun positionalRepliesAreRepliesAndCitationsAreMentions() =
        runTest {
            val r = "1".repeat(64)
            val emptyMarker = bob.sign<Event>(1_700_000_000, 1, arrayOf(arrayOf("e", r, "wss://r.example.com", "", alice.pubKey), arrayOf("p", alice.pubKey)), "re")
            assertEquals("reply", NotificationItem.classify(emptyMarker)?.type)

            val cited = bob.sign<Event>(1_700_000_000, 1, arrayOf(arrayOf("e", r), arrayOf("p", alice.pubKey)), "see nostr:" + NEvent.create(r, null, null, null))
            assertEquals("mention", NotificationItem.classify(cited)?.type)
        }

    @Test
    fun anUnprovenZapShowsTheProviderNotTheClaimedZapper() =
        runTest {
            val provider = NostrSignerInternal(KeyPair("000000000000000000000000000000000000000000000000000000000000000a".hexToByteArray()))
            val forgedRequest = """{"id":"x","pubkey":"abc","created_at":1,"kind":9734,"tags":[],"content":"","sig":"y"}"""
            val receipt = provider.sign<Event>(1_700_000_001, 9735, arrayOf(arrayOf("p", alice.pubKey), arrayOf("description", forgedRequest)), "")
            val item = assertIs<NotificationItem.Zap>(NotificationItem.classify(receipt))
            assertNull(item.sender)
            assertEquals(provider.pubKey, item.effectiveAuthorPubKey)
        }
}
